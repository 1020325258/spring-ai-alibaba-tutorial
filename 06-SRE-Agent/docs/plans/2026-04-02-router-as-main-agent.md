# RouterNode 升级为主 Agent Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** RouterNode 升级为主 Agent，能够直接回答用户问题（引导、能力介绍等），AdminNode 仅保留环境切换等后台管理功能。

**Architecture:** RouterNode 新增 "answer" 意图分类，当意图为 "answer" 或置信度不足时，自身生成回答并写入 `state["result"]`，设置 `routingTarget = "done"` 直接到 END，不再路由到 AdminNode；AdminNode 去掉引导逻辑，只保留环境切换的纯代码处理。

**Tech Stack:** Spring AI `ChatModel`（同步调用），StateGraph 条件边，SkillRegistry + EntityRegistry（从 AdminNode 迁移到 RouterNode）

---

## 变更全景

| 节点 | 变化 |
|------|------|
| RouterNode | 新增 `answer` 意图 + `generateDirectAnswer()` 直接回答（借助 SkillRegistry/EntityRegistry） |
| AdminNode | 删除 recommendCapabilities/skillRegistry/entityRegistry，只保留环境切换 |
| SREAgentEventDispatcher | ROUTER case：当 `routingTarget == "done"` 时输出内容事件而非路由指示器 |
| SREAgentNodeName | ADMIN displayTitle 改为 "后台管理" |
| SREAgentGraphConfiguration | RouterNode 构造加 skillRegistry/entityRegistry；AdminNode 构造去掉；条件边加 "done"→END |

---

## Task 1: RouterNode 升级为主 Agent

**Files:**
- Modify: `src/main/java/com/yycome/sreagent/config/node/RouterNode.java`

RouterNode 需要：
1. 新增 `answer` 意图分类到 ROUTER_PROMPT
2. 新增字段 `SkillRegistry skillRegistry` 和 `EntityRegistry entityRegistry`
3. 更新构造函数（5 参数）
4. `routeByLLM()` 当 intent=="answer" 或低置信度时，调用 `generateDirectAnswer()`，结果写入 state["result"]，target="done"
5. 新增 `generateDirectAnswer()` 和 `buildAvailableCapabilities()` 方法

**Step 1: 更新 RouterNode.java**

完整替换为以下内容（保留现有 formatHistoryAsContext 方法不变）：

```java
package com.yycome.sreagent.config.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.skills.SkillMetadata;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.yycome.sreagent.domain.ontology.model.OntologyEntity;
import com.yycome.sreagent.domain.ontology.service.EntityRegistry;
import com.yycome.sreagent.infrastructure.config.SessionProperties;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 路由节点（主 Agent）：判断意图后路由到专业 Agent，或直接回答用户问题。
 *
 * 路由目标：
 * - queryAgent：查询意图，用户想查看/获取数据
 * - investigateAgent：排查意图，用户反馈异常症状或想诊断问题
 * - admin：后台管理操作（环境查看/切换）
 * - done（→ END）：RouterNode 直接回答，无需路由（引导、能力介绍、闲聊等）
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

            **admin** - 后台管理操作
            - 查看当前环境、切换到某个环境
            - 包含关键词"环境"、"switch"、"env"

            **answer** - RouterNode 直接回答，不路由到其他 Agent
            - 输入太短、表述模糊、问候语、感谢
            - 询问系统功能、有什么能力、能帮我做什么
            - 其他不属于 query/investigate/admin 的情况

            ## 历史上下文
            %s

            ## 用户问题
            %s

            请按照以下 JSON 格式回复（必须严格 JSON 格式，不要其他文字）：
            {
              "intent": "query/investigate/admin/answer",
              "confidence": 0.0-1.0 之间的置信度分数,
              "extractedParams": {
                "entity": "实体类型（Order/Contract/Quotation等）",
                "value": "实体值（订单号/合同号等）",
                "queryScope": "查询范围（空表示仅查主实体）"
              }
            }
            """;

    private static final String ANSWER_PROMPT = """
            你是一个 SRE 助手，请直接友好地回答用户的问题。

            ## 历史上下文
            %s

            ## 系统可用能力
            %s

            ## 用户输入
            %s

            回答要求：
            - 如果用户在询问系统能力，介绍上方的查询和排查能力
            - 如果用户输入模糊，给出引导建议
            - 如果是问候/感谢，简短回复即可
            - 不要暴露技术术语（如 queryAgent、ontologyQuery 等）
            """;

    private final ChatModel chatModel;
    private final MessageWindowChatMemory memory;
    private final SessionProperties sessionProperties;
    private final SkillRegistry skillRegistry;
    private final EntityRegistry entityRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RouterNode(ChatModel chatModel, MessageWindowChatMemory memory,
                      SessionProperties sessionProperties,
                      SkillRegistry skillRegistry, EntityRegistry entityRegistry) {
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
        updates.put("intent", result.get("intent"));
        updates.put("confidence", result.get("confidence"));
        updates.put("extractedParams", result.get("extractedParams"));

        String target = (String) result.get("target");
        updates.put("routingTarget", target);

        // 直接回答：RouterNode 自己处理
        if ("done".equals(target)) {
            String answer = generateDirectAnswer(input, contextInfo);
            updates.put("result", answer);
            log.info("RouterNode 直接回答，input_len={}", input.length());
        } else {
            log.info("RouterNode → target={}, intent={}, confidence={}",
                    target, result.get("intent"), result.get("confidence"));
        }

        return updates;
    }

    private Map<String, Object> classifyIntent(String userInput, String contextInfo) {
        String fullPrompt = String.format(ROUTER_PROMPT, contextInfo, userInput);
        var response = chatModel.call(new Prompt(fullPrompt));
        String text = response.getResult().getOutput().getText().trim();

        Map<String, Object> result = new HashMap<>();
        try {
            JsonNode jsonNode = objectMapper.readTree(text);
            String intent = jsonNode.has("intent") ? jsonNode.get("intent").asText().toLowerCase() : "answer";
            double confidence = jsonNode.has("confidence") ? jsonNode.get("confidence").asDouble() : 0.5;

            Map<String, String> extractedParams = new HashMap<>();
            if (jsonNode.has("extractedParams")) {
                JsonNode params = jsonNode.get("extractedParams");
                if (params.has("entity")) extractedParams.put("entity", params.get("entity").asText());
                if (params.has("value")) extractedParams.put("value", params.get("value").asText());
                if (params.has("queryScope")) extractedParams.put("queryScope", params.get("queryScope").asText());
            }

            result.put("intent", intent);
            result.put("confidence", confidence);
            result.put("extractedParams", extractedParams);

            // 置信度不足 → 直接回答
            double threshold = sessionProperties.getConfidenceThreshold();
            if (confidence < threshold && !"admin".equals(intent)) {
                result.put("target", "done");
            } else {
                result.put("target", switch (intent) {
                    case "query" -> "queryAgent";
                    case "investigate" -> "investigateAgent";
                    case "admin" -> "admin";
                    default -> "done";
                });
            }
        } catch (Exception e) {
            log.warn("RouterNode JSON 解析失败，降级为直接回答: {}", e.getMessage());
            result.put("intent", "answer");
            result.put("confidence", 0.3);
            result.put("extractedParams", Map.of());
            result.put("target", "done");
        }

        return result;
    }

    /**
     * 生成直接回答（用于 answer 意图或低置信度情况）
     */
    private String generateDirectAnswer(String userInput, String contextInfo) {
        String capabilities = buildAvailableCapabilities();
        String prompt = String.format(ANSWER_PROMPT, contextInfo, capabilities, userInput);
        try {
            var response = chatModel.call(new Prompt(prompt));
            return response.getResult().getOutput().getText().trim();
        } catch (Exception e) {
            log.error("[RouterNode] 直接回答生成失败", e);
            return "抱歉，我无法理解您的问题。请描述您的业务需求，例如：\n- 查询订单合同\n- 查询报价单\n- 排查签约问题";
        }
    }

    /**
     * 构建可用能力列表（Skills + 实体），用于直接回答时的能力介绍
     */
    private String buildAvailableCapabilities() {
        StringBuilder sb = new StringBuilder();

        sb.append("【排查能力】\n");
        List<SkillMetadata> skills = skillRegistry.listAll();
        if (skills.isEmpty()) {
            sb.append("（暂无）\n");
        } else {
            for (SkillMetadata skill : skills) {
                sb.append("- ").append(skill.getName())
                        .append("：").append(skill.getDescription()).append("\n");
            }
        }

        sb.append("\n【查询实体】\n");
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
                if (text != null && text.length() > 150) {
                    text = text.substring(0, 150) + "...";
                }
                sb.append("- 助手: ").append(text).append("\n");
            }
        }
        return sb.toString();
    }
}
```

**Step 2: 编译验证（预期有报错）**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
mvn compile -f ../pom.xml -pl 06-SRE-Agent -q 2>&1
```

预期 RouterNode 本身编译通过；SREAgentGraphConfiguration 报 RouterNode 构造不匹配（Task 4 修复）。

---

## Task 2: AdminNode 瘦身——只保留环境管理

**Files:**
- Modify: `src/main/java/com/yycome/sreagent/config/node/AdminNode.java`

删除：
- `recommendCapabilities()` 方法
- `buildAvailableCapabilities()` 方法
- `buildDefaultHelpResponse()` 方法
- `SkillRegistry skillRegistry` 字段和 import
- `EntityRegistry entityRegistry` 字段和 import
- `ReactAgent adminAgent` 字段（从未被调用）和 import
- `TracingService tracingService` 字段（从未被调用）和 import
- `ChatModel chatModel` 字段（之前用于 recommendCapabilities，删除后不再需要）和 import

保留：
- `EnvironmentConfig environmentConfig` 字段
- `EnvAlias` 枚举
- `handleEnvSwitch()` 方法
- `buildEnvListResponse()` 方法

**完整替换为：**

```java
package com.yycome.sreagent.config.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.yycome.sreagent.infrastructure.config.EnvironmentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 后台管理节点：处理环境查看/切换等系统命令。
 *
 * 职责（仅后台管理）：
 * - 环境切换（纯代码逻辑，无 LLM 调用）
 * - 环境列表查询
 *
 * 引导/推荐类功能已迁移到 RouterNode 直接处理。
 */
public class AdminNode implements NodeAction {

    private static final Logger log = LoggerFactory.getLogger(AdminNode.class);

    private enum EnvAlias {
        OFFLINE_BETA("offline-beta", "基准", "离线", "beta", "offline"),
        NRS_ESCROW("nrs-escrow", "测试", "escrow", "nrs");

        private final String envKey;
        private final Set<String> aliases;

        EnvAlias(String envKey, String... aliases) {
            this.envKey = envKey;
            this.aliases = new HashSet<>();
            for (String alias : aliases) {
                this.aliases.add(alias.toLowerCase());
            }
        }

        public String getEnvKey() { return envKey; }

        public boolean matches(String input) {
            if (input.contains(envKey)) return true;
            return aliases.stream().anyMatch(input::contains);
        }
    }

    private final EnvironmentConfig environmentConfig;

    public AdminNode(EnvironmentConfig environmentConfig) {
        this.environmentConfig = environmentConfig;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String input = state.value(OverAllState.DEFAULT_INPUT_KEY, "");
        log.info("AdminNode 收到 input: {}", input);
        String result = executeAdmin(input);
        return Map.of("result", result);
    }

    private String executeAdmin(String input) {
        String lowerInput = input.toLowerCase();
        Map<String, String> envs = environmentConfig.getAvailableEnvironments();

        String envResult = handleEnvSwitch(lowerInput, envs);
        if (envResult != null) return envResult;

        return buildEnvListResponse();
    }

    private String handleEnvSwitch(String lowerInput, Map<String, String> envs) {
        String targetEnv = null;

        for (String envKey : envs.keySet()) {
            if (lowerInput.contains(envKey.toLowerCase())) {
                targetEnv = envKey;
                break;
            }
        }

        if (targetEnv == null) {
            for (EnvAlias alias : EnvAlias.values()) {
                if (alias.matches(lowerInput) && envs.containsKey(alias.getEnvKey())) {
                    targetEnv = alias.getEnvKey();
                    break;
                }
            }
        }

        if (targetEnv == null) {
            outer:
            for (Map.Entry<String, String> entry : envs.entrySet()) {
                for (String token : entry.getValue().split("[-\\s]+")) {
                    if (token.length() >= 2 && lowerInput.contains(token.toLowerCase())) {
                        targetEnv = entry.getKey();
                        break outer;
                    }
                }
            }
        }

        if (targetEnv != null) {
            boolean success = environmentConfig.switchEnv(targetEnv);
            if (success) {
                return "已切换到环境：**" + environmentConfig.getCurrentEnvDescription()
                        + "**（`" + environmentConfig.getCurrentEnv() + "`）";
            } else {
                return "切换失败：未知环境 `" + targetEnv + "`";
            }
        }

        return null;
    }

    private String buildEnvListResponse() {
        Map<String, String> envs = environmentConfig.getAvailableEnvironments();
        StringBuilder sb = new StringBuilder();
        sb.append("**当前环境**：").append(environmentConfig.getCurrentEnvDescription())
                .append("（`").append(environmentConfig.getCurrentEnv()).append("`）\n\n");
        sb.append("**可用环境**：\n");
        envs.forEach((key, desc) -> {
            String marker = key.equals(environmentConfig.getCurrentEnv()) ? " ✓" : "";
            sb.append("- `").append(key).append("` — ").append(desc).append(marker).append("\n");
        });
        return sb.toString();
    }
}
```

---

## Task 3: 事件分发器 + 节点名称更新

**Files:**
- Modify: `src/main/java/com/yycome/sreagent/config/SREAgentEventDispatcher.java`
- Modify: `src/main/java/com/yycome/sreagent/config/node/SREAgentNodeName.java`

### 3A: SREAgentNodeName — 更新 ADMIN displayTitle

将：
```java
ADMIN("admin", "智能推荐"),
```
改为：
```java
ADMIN("admin", "后台管理"),
```

### 3B: SREAgentEventDispatcher — ROUTER case 支持直接回答

找到 switch 中的 ROUTER case：
```java
case ROUTER -> buildRoutingEvent(output);
```

替换为：
```java
case ROUTER -> {
    Object target = output.state().value("routingTarget").orElse("");
    if ("done".equals(target)) {
        yield buildMarkdownEvent(output); // RouterNode 直接回答
    } else {
        yield buildRoutingEvent(output); // 路由指示器
    }
}
```

注意：`buildMarkdownEvent(output)` 已经存在，它读取 `state["result"]` 和节点的 displayTitle，对 "router" 节点会显示 "意图识别" 标题——可接受（也可以在 SREAgentNodeName 中为 ROUTER 改一个更合适的 direct-answer 标题，但暂不做）。

---

## Task 4: 更新 SREAgentGraphConfiguration 接线

**Files:**
- Modify: `src/main/java/com/yycome/sreagent/config/SREAgentGraphConfiguration.java`

**改动点：**

1. **RouterNode 构造函数**——加 skillRegistry + entityRegistry：

将：
```java
graph.addNode("router", node_async(new RouterNode(chatModel, messageWindowChatMemory, sessionProperties)))
```
改为：
```java
graph.addNode("router", node_async(new RouterNode(chatModel, messageWindowChatMemory, sessionProperties, skillRegistry, entityRegistry)))
```

2. **AdminNode 构造函数**——只传 environmentConfig：

将：
```java
.addNode("admin", node_async(new AdminNode(environmentConfig, adminAgent, tracingService, chatModel, skillRegistry, entityRegistry)));
```
改为：
```java
.addNode("admin", node_async(new AdminNode(environmentConfig)));
```

3. **条件边**——加 "done"→END：

将：
```java
.addConditionalEdges("router", edge_async(new RouterDispatcher()),
        Map.of("queryAgent", "queryAgent", "investigateAgent", "investigateAgent", "admin", "admin"))
```
改为：
```java
.addConditionalEdges("router", edge_async(new RouterDispatcher()),
        Map.of("queryAgent", "queryAgent",
               "investigateAgent", "investigateAgent",
               "admin", "admin",
               "done", END))
```

4. **删除不再使用的字段**（如果 adminAgent、tracingService（SREAgentGraphConfiguration 里的）在去掉 AdminNode 注入后变成了孤立字段，则删除对应的 `@Autowired` 和 field）：

检查哪些 `@Autowired` 字段只用于 AdminNode 构造，现在 AdminNode 只需要 `environmentConfig`：
- `investigateAgent` — 仍用于 AgentNode，保留
- `adminAgent` — 仅用于 AdminNode 构造，**删除**
- `tracingService` — 仍用于 AgentNode，保留
- `environmentConfig` — 仍用于 AdminNode，保留

删除：
```java
@Lazy
@Autowired
@Qualifier("adminAgent")
private ReactAgent adminAgent;
```
（以及对应的 import，如果 ReactAgent 只在这里用到的话）

**Step 3: 编译验证**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
mvn compile -f ../pom.xml -pl 06-SRE-Agent -q 2>&1
```

期望：**BUILD SUCCESS，零错误**

**Step 4: Commit**

```bash
cd /Users/zqy/work/AI-Project/workTree/spring-ai-alibaba-tutorial-feature-integration-test
git add 06-SRE-Agent/src/main/java/com/yycome/sreagent/config/node/RouterNode.java \
        06-SRE-Agent/src/main/java/com/yycome/sreagent/config/node/AdminNode.java \
        06-SRE-Agent/src/main/java/com/yycome/sreagent/config/node/SREAgentNodeName.java \
        06-SRE-Agent/src/main/java/com/yycome/sreagent/config/SREAgentEventDispatcher.java \
        06-SRE-Agent/src/main/java/com/yycome/sreagent/config/SREAgentGraphConfiguration.java
git commit -m "feat: RouterNode as main agent with direct answer capability

- RouterNode adds 'answer' intent: generates response directly via LLM
- RouterNode takes over capability recommendation from AdminNode
- AdminNode stripped to env-switch only (no LLM calls)
- SREAgentEventDispatcher: router node emits content event when target=done
- Graph: add 'done'->END conditional edge"
```

---

## Task 5: 运行测试验证

**Step 1: 单元测试**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
mvn test -f ../pom.xml -pl 06-SRE-Agent \
  -Dtest="ObservabilityAspectAnnotationTest,ToolExecutionTemplateTest,ToolResultTest,QueryScopeTest,EntityRegistryTest,OntologyQueryEngineTest,PersonalQuoteGatewayTest" \
  -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -20
```

期望：所有单元测试 PASS

**Step 2: 检查日志**

```bash
grep -E "ERROR" log/sre-agent.log 2>/dev/null | tail -20
```

**Step 3: 集成测试**

```bash
./run-integration-tests.sh
```

期望：所有集成测试 PASS。query/investigate 路径不受影响；admin 路径的变化是 RouterNode 现在处理引导，不再路由到 AdminNode，集成测试中如有 admin 场景需关注。

---

## 写回记忆的时序

- `done` 路径：RouterNode 写 `result`，`processStream.doOnComplete` 写回 memory ✓
- `query/investigate/admin` 路径：对应 Agent 写 `result`，同样被写回 ✓
- 无需修改 `SREAgentGraphProcess`
