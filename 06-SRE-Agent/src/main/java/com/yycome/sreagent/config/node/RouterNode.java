package com.yycome.sreagent.config.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.skills.SkillMetadata;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.yycome.sreagent.domain.ontology.model.OntologyEntity;
import com.yycome.sreagent.domain.ontology.service.EntityRegistry;
import com.yycome.sreagent.config.infra.SessionProperties;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.HashMap;
import java.util.Map;

/**
 * 路由节点：调用 LLM 判断用户意图，路由到对应 Agent，或直接回答。
 *
 * 四种路由目标：
 * - queryAgent：查询意图，用户想查看/获取数据
 * - investigateAgent：排查意图，用户反馈异常症状或想诊断问题
 * - admin：后台管理，明确涉及环境操作
 * - done：直接回答，询问系统能力或输入模糊
 *
 * 增强功能：
 * - 集成会话上下文（用户画像 + 历史摘要）
 * - 输出置信度评估
 * - 预提取参数
 * - 直接回答能力（answer 意图）
 */
public class RouterNode implements NodeAction {

    private static final Logger log = LoggerFactory.getLogger(RouterNode.class);

    private static final String ROUTER_PROMPT = """
            你是一个路由器，判断用户意图属于哪一类，并提取关键参数。

            ## 意图分类

            **query** - 查询意图，用户想查看/获取数据
            - 查订单、查合同、查节点、查报价单
            - 弹窗有哪些S单、合同的签约单据
            - 某个实体/模型的数据详情

            **investigate** - 排查意图，用户反馈异常症状或想诊断问题
            - 弹窗提示XXX、缺少XXX、无XXX
            - 为什么XXX、排查XXX、诊断XXX
            - 某功能不工作、某数据异常

            **answer** - 直接回答，用户询问系统能力或输入过于模糊
            - 有什么功能、能帮我做什么、你是什么
            - 输入太短（少于5字符）
            - 询问系统配置（但不含"环境"/"switch"/"env"关键词）

            **admin** - 后台管理操作，明确涉及环境操作
            - 查看环境、切换环境、switch env
            - 包含"环境"/"switch"/"env"等关键词

            ## 历史上下文
            %s

            ## 用户问题
            %s

            请按照以下 JSON 格式回复（必须严格 JSON 格式，不要其他文字）：
            {
              "intent": "query/investigate/answer/admin",
              "confidence": 0.0-1.0 之间的置信度分数,
              "extractedParams": {
                "entity": "实体类型（Order/Contract/Quotation等）",
                "value": "实体值（订单号/合同号等）",
                "queryScope": "查询范围（空表示仅查主实体）"
              },
              "clarification": "如果置信度低于 0.6，需要向用户确认的问题"
            }
            """;

    private static final String ANSWER_PROMPT = """
            ## 历史上下文
            %s

            ## 系统能力列表
            %s

            ## 用户问题
            %s

            你是一个智能 SRE 助手。根据历史上下文和系统能力列表，直接回答用户问题。
            - 如果用户询问系统功能，使用系统能力列表介绍可用功能（不暴露技术名称如 queryAgent、ontologyQuery）
            - 如果用户输入模糊，引导其描述具体业务需求
            - 如果用户询问配置但不涉及环境切换，直接说明
            只输出回答内容，不要其他说明。
            """;

    private final ChatModel chatModel;
    private final MessageWindowChatMemory memory;
    private final SessionProperties sessionProperties;
    private final SkillRegistry skillRegistry;
    private final EntityRegistry entityRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RouterNode(ChatModel chatModel, MessageWindowChatMemory memory,
                      SessionProperties sessionProperties,
                      SkillRegistry skillRegistry,
                      EntityRegistry entityRegistry) {
        this.chatModel = chatModel;
        this.memory = memory;
        this.sessionProperties = sessionProperties;
        this.skillRegistry = skillRegistry;
        this.entityRegistry = entityRegistry;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String input = state.value(OverAllState.DEFAULT_INPUT_KEY, "");
        String sessionId = state.value("sessionId", "");

        log.info("RouterNode 收到 input: {}, sessionId: {}", input, sessionId);

        // 获取会话上下文
        String contextInfo = "";
        if (sessionId != null && !sessionId.isEmpty()) {
            List<Message> history = memory.get(sessionId);
            if (!history.isEmpty()) {
                contextInfo = formatHistoryAsContext(history);
            }
        }

        // 调用 LLM 进行意图分类
        Map<String, Object> result = classifyIntent(input, contextInfo);

        Map<String, Object> updates = new HashMap<>();
        String target = (String) result.get("target");
        updates.put("routingTarget", target);
        updates.put("intent", result.get("intent"));
        updates.put("confidence", result.get("confidence"));
        updates.put("extractedParams", result.get("extractedParams"));
        updates.put("clarification", result.get("clarification"));

        // 直接回答：生成答案并写入 result
        if ("done".equals(target)) {
            String directAnswer = generateDirectAnswer(input, contextInfo);
            updates.put("result", directAnswer);
        }

        log.info("RouterNode → intent={}, confidence={}, target={}",
                result.get("intent"), result.get("confidence"), target);

        return updates;
    }

    private Map<String, Object> classifyIntent(String userInput, String contextInfo) {
        String fullPrompt = String.format(ROUTER_PROMPT, contextInfo, userInput);
        var response = chatModel.call(new Prompt(fullPrompt));
        String text = response.getResult().getOutput().getText().trim();

        // 解析 JSON 响应
        Map<String, Object> result = new HashMap<>();
        try {
            // 尝试解析 JSON
            JsonNode jsonNode = objectMapper.readTree(text);
            String intent = jsonNode.has("intent") ? jsonNode.get("intent").asText().toLowerCase() : "answer";
            double confidence = jsonNode.has("confidence") ? jsonNode.get("confidence").asDouble() : 0.5;

            // 提取参数
            Map<String, String> extractedParams = new HashMap<>();
            if (jsonNode.has("extractedParams")) {
                JsonNode params = jsonNode.get("extractedParams");
                if (params.has("entity")) extractedParams.put("entity", params.get("entity").asText());
                if (params.has("value")) extractedParams.put("value", params.get("value").asText());
                if (params.has("queryScope")) extractedParams.put("queryScope", params.get("queryScope").asText());
            }

            String clarification = jsonNode.has("clarification") ? jsonNode.get("clarification").asText() : "";

            result.put("intent", intent);
            result.put("confidence", confidence);
            result.put("extractedParams", extractedParams);
            result.put("clarification", clarification);

            // 置信度阈值判断 + 意图路由
            double threshold = sessionProperties.getConfidenceThreshold();
            if ("admin".equals(intent)) {
                result.put("target", "admin");
            } else if (confidence < threshold) {
                // 低置信度且非 admin → 直接回答
                result.put("target", "done");
                if (clarification.isEmpty()) {
                    clarification = "您是想查询数据还是排查问题？请更详细地描述您的需求。";
                }
                result.put("clarification", clarification);
            } else if ("answer".equals(intent)) {
                result.put("target", "done");
            } else {
                result.put("target", intent.equals("query") ? "queryAgent" :
                                   intent.equals("investigate") ? "investigateAgent" : "done");
            }

        } catch (Exception e) {
            log.warn("RouterNode JSON 解析失败，使用默认路由: {}", e.getMessage());
            // 降级处理
            result.put("intent", "answer");
            result.put("confidence", 0.3);
            result.put("extractedParams", Map.of());
            result.put("clarification", "抱歉，我无法理解您的问题。请更详细地描述。");
            result.put("target", "done");
        }

        return result;
    }

    /**
     * 直接回答用户问题（answer 意图或低置信度场景）
     */
    private String generateDirectAnswer(String input, String contextInfo) {
        String capabilities = buildAvailableCapabilities();
        String formattedPrompt = String.format(ANSWER_PROMPT, contextInfo, capabilities, input);
        try {
            var response = chatModel.call(new Prompt(formattedPrompt));
            return response.getResult().getOutput().getText().trim();
        } catch (Exception e) {
            log.error("[RouterNode] generateDirectAnswer 失败: {}", e.getMessage(), e);
            return """
                    抱歉，我无法理解您的问题。请描述您的业务需求，例如：
                    - 查询订单合同
                    - 查询报价单
                    - 排查签约问题
                    """;
        }
    }

    /**
     * 构建可用能力列表（Skills + 实体）
     * 与 AdminNode.buildAvailableCapabilities() 逻辑一致
     */
    private String buildAvailableCapabilities() {
        StringBuilder sb = new StringBuilder();

        // 1. Skills
        sb.append("【排查能力】\n");
        List<SkillMetadata> skills = skillRegistry.listAll();
        if (skills.isEmpty()) {
            sb.append("（暂无）\n");
        } else {
            for (SkillMetadata skill : skills) {
                sb.append("- ").append(skill.getName())
                        .append("：").append(skill.getDescription())
                        .append("\n");
            }
        }

        sb.append("\n【查询实体】\n");
        // 2. Entities
        List<OntologyEntity> entities = entityRegistry.getOntology().getEntities();
        for (OntologyEntity entity : entities) {
            sb.append("- ").append(entity.getName())
                    .append("（").append(entity.getDisplayName()).append("）");
            if (entity.getAliases() != null && !entity.getAliases().isEmpty()) {
                sb.append("：别名[").append(String.join(", ", entity.getAliases())).append("]");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 将历史消息格式化为文本摘要，供路由 prompt 使用
     */
    private String formatHistoryAsContext(List<Message> messages) {
        StringBuilder sb = new StringBuilder("## 最近对话记录\n");
        for (Message msg : messages) {
            if (msg instanceof UserMessage) {
                sb.append("- 用户: ").append(msg.getText()).append("\n");
            } else if (msg instanceof AssistantMessage am) {
                String text = am.getText();
                // 助手消息可能是 JSON，只取前 150 字供路由参考
                if (text != null && text.length() > 150) {
                    text = text.substring(0, 150) + "...";
                }
                sb.append("- 助手: ").append(text).append("\n");
            }
        }
        return sb.toString();
    }
}
