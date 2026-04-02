package com.yycome.sreagent.config.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import com.yycome.sreagent.infrastructure.config.SessionProperties;
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
 * 路由节点：调用 LLM 判断用户意图，路由到对应 Agent。
 *
 * 三种路由目标：
 * - queryAgent：查询意图，用户想查看/获取数据
 * - investigateAgent：排查意图，用户反馈异常症状或想诊断问题
 * - admin：需要引导，用户意图不明确
 *
 * 增强功能：
 * - 集成会话上下文（用户画像 + 历史摘要）
 * - 输出置信度评估
 * - 预提取参数
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

            **admin** - 需要引导，用户意图不明确或需要帮助
            - 输入太短、表述模糊
            - 询问系统配置、环境、怎么做

            ## 历史上下文
            %s

            ## 用户问题
            %s

            请按照以下 JSON 格式回复（必须严格 JSON 格式，不要其他文字）：
            {
              "intent": "query/investigate/admin",
              "confidence": 0.0-1.0 之间的置信度分数,
              "extractedParams": {
                "entity": "实体类型（Order/Contract/Quotation等）",
                "value": "实体值（订单号/合同号等）",
                "queryScope": "查询范围（空表示仅查主实体）"
              },
              "clarification": "如果置信度低于 0.6，需要向用户确认的问题"
            }
            """;

    private final ChatModel chatModel;
    private final MessageWindowChatMemory memory;
    private final SessionProperties sessionProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RouterNode(ChatModel chatModel, MessageWindowChatMemory memory,
                      SessionProperties sessionProperties) {
        this.chatModel = chatModel;
        this.memory = memory;
        this.sessionProperties = sessionProperties;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String input = state.value(OverAllState.DEFAULT_INPUT_KEY, "");
        String sessionId = state.value("sessionId", "");
        String userId = state.value("userId", "");

        log.info("RouterNode 收到 input: {}, sessionId: {}", input, sessionId);

        // 获取会话上下文
        String contextInfo = "";
        if (sessionId != null && !sessionId.isEmpty()) {
            List<Message> history = memory.get(sessionId);
            if (!history.isEmpty()) {
                contextInfo = formatHistoryAsContext(history);
            }
        }

        // 调用 LLM 进行路由
        Map<String, Object> result = routeByLLM(input, contextInfo);

        Map<String, Object> updates = new HashMap<>();
        updates.put("routingTarget", result.get("target"));
        updates.put("intent", result.get("intent"));
        updates.put("confidence", result.get("confidence"));
        updates.put("extractedParams", result.get("extractedParams"));
        updates.put("clarification", result.get("clarification"));

        log.info("RouterNode → intent={}, confidence={}, target={}",
                result.get("intent"), result.get("confidence"), result.get("target"));

        return updates;
    }

    private Map<String, Object> routeByLLM(String userInput, String contextInfo) {
        String fullPrompt = String.format(ROUTER_PROMPT, contextInfo, userInput);
        var response = chatModel.call(new Prompt(fullPrompt));
        String text = response.getResult().getOutput().getText().trim();

        // 解析 JSON 响应
        Map<String, Object> result = new HashMap<>();
        try {
            // 尝试解析 JSON
            JsonNode jsonNode = objectMapper.readTree(text);
            String intent = jsonNode.has("intent") ? jsonNode.get("intent").asText().toLowerCase() : "admin";
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

            // 置信度阈值判断
            double threshold = sessionProperties.getConfidenceThreshold();
            if (confidence < threshold) {
                result.put("target", "admin");
                if (clarification.isEmpty()) {
                    clarification = "您是想查询数据还是排查问题？请更详细地描述您的需求。";
                }
                result.put("clarification", clarification);
            } else {
                result.put("target", intent.equals("query") ? "queryAgent" :
                                   intent.equals("investigate") ? "investigateAgent" : "admin");
            }

        } catch (Exception e) {
            log.warn("RouterNode JSON 解析失败，使用默认路由: {}", e.getMessage());
            // 降级处理
            result.put("intent", "admin");
            result.put("confidence", 0.3);
            result.put("extractedParams", Map.of());
            result.put("clarification", "抱歉，我无法理解您的问题。请更详细地描述。");
            result.put("target", "admin");
        }

        return result;
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
