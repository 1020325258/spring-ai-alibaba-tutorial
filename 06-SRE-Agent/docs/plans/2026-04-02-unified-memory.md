# Unified Memory Management Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 用 Spring AI 标准的 `MessageWindowChatMemory` 替换自定义 `SessionContextManager`，实现上下文读写的统一入口。

**Architecture:** `MessageWindowChatMemory` 作为唯一记忆存储，RouterNode/AgentNode 均从同一 Bean 读取历史消息，写回统一在 `SREAgentGraphProcess.processStream` 的 `doOnComplete` 回调中执行（graph 完成后写入 `UserMessage(input)` + `AssistantMessage(result)`）。

**Tech Stack:** Spring AI `MessageWindowChatMemory` + `InMemoryChatMemoryRepository`，Spring AI 1.1.2

---

## 现状问题

1. `SessionContextManager` 有 `getRouterContext()` / `getAgentContext()` 两套视图，维护负担重
2. `addTurn()` **从未被调用**——记忆写回一直是空操作，现有"记忆"功能形同虚设
3. `SREAgentGraphProcess` 导入了 `SessionContextManager` 但完全没使用

---

## Task 1: 创建 `MemoryConfig` Bean

**Files:**
- Create: `src/main/java/com/yycome/sreagent/infrastructure/memory/MemoryConfig.java`
- Simplify: `src/main/java/com/yycome/sreagent/infrastructure/config/SessionProperties.java`
- Modify: `src/main/resources/application.yml`

**Step 1: 创建 MemoryConfig.java**

```java
package com.yycome.sreagent.infrastructure.memory;

import com.yycome.sreagent.infrastructure.config.SessionProperties;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MemoryConfig {

    @Bean
    public MessageWindowChatMemory messageWindowChatMemory(SessionProperties props) {
        // maxRecentTurns=5 轮 → 10 条消息（每轮 user+assistant 各一条）
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(props.getMaxRecentTurns() * 2)
                .build();
    }
}
```

**Step 2: 简化 `SessionProperties.java`**

删除 `enabled`、`storeType`、`expireHours` 三个字段（不再需要），保留 `maxRecentTurns` 和 `confidenceThreshold`：

```java
package com.yycome.sreagent.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "sreagent.session")
public class SessionProperties {

    /** 保留最近对话轮次数（每轮 = user + assistant 各一条消息） */
    private int maxRecentTurns = 5;

    /** 意图识别置信度阈值（低于此值路由到 admin） */
    private double confidenceThreshold = 0.6;
}
```

**Step 3: 更新 `application.yml` 中的 `sreagent.session` 配置段**

将原来的配置段（第64-80行）替换为：

```yaml
# SRE-Agent 会话记忆配置
sreagent:
  session:
    # 保留最近对话轮次数（每轮包含 user + assistant 各一条消息）
    max-recent-turns: 5
    # 意图识别置信度阈值：低于此值路由到 admin
    confidence-threshold: 0.6
```

（同时删除 `sreagent.redis` 配置段，因为已不再使用自定义 redis 存储）

**Step 4: 编译验证（无需运行测试）**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
mvn compile -f ../pom.xml -pl 06-SRE-Agent -q
```

期望：BUILD SUCCESS

**Step 5: Commit**

```bash
git add 06-SRE-Agent/src/main/java/com/yycome/sreagent/infrastructure/memory/MemoryConfig.java \
        06-SRE-Agent/src/main/java/com/yycome/sreagent/infrastructure/config/SessionProperties.java \
        06-SRE-Agent/src/main/resources/application.yml
git commit -m "feat: add MessageWindowChatMemory Bean, simplify SessionProperties"
```

---

## Task 2: 改造 `RouterNode`

**Files:**
- Modify: `src/main/java/com/yycome/sreagent/config/node/RouterNode.java`

**Step 1: 替换依赖**

将 `RouterNode` 的构造函数参数从 `SessionContextManager` 改为 `MessageWindowChatMemory` + `SessionProperties`：

```java
// 替换 import
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import com.yycome.sreagent.infrastructure.config.SessionProperties;
// 删除旧 import:
// import com.yycome.sreagent.infrastructure.session.SessionContextManager;
// import com.yycome.sreagent.infrastructure.session.SessionContext;

// 替换字段
private final MessageWindowChatMemory memory;
private final SessionProperties sessionProperties;

// 新构造函数
public RouterNode(ChatModel chatModel, MessageWindowChatMemory memory,
                  SessionProperties sessionProperties) {
    this.chatModel = chatModel;
    this.memory = memory;
    this.sessionProperties = sessionProperties;
}
```

**Step 2: 替换 `apply()` 中的上下文读取逻辑**

将 `apply()` 中读取上下文的部分：
```java
// 旧代码（第90-94行）
String contextInfo = "";
if (sessionContextManager.isEnabled() && sessionId != null && !sessionId.isEmpty()) {
    SessionContext context = sessionContextManager.getOrCreate(sessionId, userId);
    contextInfo = sessionContextManager.getRouterContext(context);
}
```
替换为：
```java
String contextInfo = "";
if (sessionId != null && !sessionId.isEmpty()) {
    List<Message> history = memory.get(sessionId);
    if (!history.isEmpty()) {
        contextInfo = formatHistoryAsContext(history);
    }
}
```

**Step 3: 替换 `routeByLLM()` 中的置信度阈值读取**

将：
```java
double threshold = sessionContextManager.getConfidenceThreshold();
```
替换为：
```java
double threshold = sessionProperties.getConfidenceThreshold();
```

**Step 4: 在 `RouterNode` 中添加私有辅助方法**

在类末尾添加：

```java
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
            if (text.length() > 150) text = text.substring(0, 150) + "...";
            sb.append("- 助手: ").append(text).append("\n");
        }
    }
    return sb.toString();
}
```

**Step 5: 编译验证**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
mvn compile -f ../pom.xml -pl 06-SRE-Agent -q
```

期望：BUILD SUCCESS（RouterNode 编译通过，但 SREAgentGraphConfiguration 此时会编译失败，因为它还在传旧参数——Task 5 会修复）

**Step 6: Commit**

```bash
git add 06-SRE-Agent/src/main/java/com/yycome/sreagent/config/node/RouterNode.java
git commit -m "refactor: RouterNode use MessageWindowChatMemory instead of SessionContextManager"
```

---

## Task 3: 改造 `AgentNode`

**Files:**
- Modify: `src/main/java/com/yycome/sreagent/config/node/AgentNode.java`

**Step 1: 替换依赖**

```java
// 替换 import
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
// 删除旧 import:
// import com.yycome.sreagent.infrastructure.session.SessionContextManager;
// import com.yycome.sreagent.infrastructure.session.SessionContext;

// 替换字段
private final MessageWindowChatMemory memory;

// 新构造函数
public AgentNode(ReactAgent agent, String agentName, TracingService tracingService,
                 MessageWindowChatMemory memory) {
    this.agent = agent;
    this.agentName = agentName;
    this.tracingService = tracingService;
    this.memory = memory;
}
```

**Step 2: 替换 `buildEnhancedInput()` 中的上下文读取**

将：
```java
// 获取会话上下文
if (sessionContextManager.isEnabled() && sessionId != null && !sessionId.isEmpty()) {
    SessionContext context = sessionContextManager.getOrCreate(sessionId, userId);
    String agentContext = sessionContextManager.getAgentContext(context);
    if (agentContext != null && !agentContext.isEmpty()) {
        sb.append(agentContext).append("\n\n");
    }
}
```
替换为：
```java
// 注入历史对话上下文
if (sessionId != null && !sessionId.isEmpty()) {
    List<Message> history = memory.get(sessionId);
    if (!history.isEmpty()) {
        sb.append(formatHistoryAsContext(history)).append("\n\n");
    }
}
```

**Step 3: 添加私有辅助方法**

在类末尾添加（与 RouterNode 中相同的逻辑，但不截断助手消息——Agent 需要完整上下文）：

```java
private String formatHistoryAsContext(List<Message> messages) {
    StringBuilder sb = new StringBuilder("## 历史对话\n");
    for (Message msg : messages) {
        if (msg instanceof UserMessage) {
            sb.append("- 用户: ").append(msg.getText()).append("\n");
        } else if (msg instanceof AssistantMessage am) {
            sb.append("- 助手: ").append(am.getText()).append("\n");
        }
    }
    return sb.toString();
}
```

**Step 4: Commit**

```bash
git add 06-SRE-Agent/src/main/java/com/yycome/sreagent/config/node/AgentNode.java
git commit -m "refactor: AgentNode use MessageWindowChatMemory instead of SessionContextManager"
```

---

## Task 4: 在 `SREAgentGraphProcess` 中添加写回逻辑

**Files:**
- Modify: `src/main/java/com/yycome/sreagent/config/SREAgentGraphProcess.java`

**Step 1: 新增字段和构造函数参数**

```java
// 新增 import
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
// 删除旧 import:
// import com.yycome.sreagent.infrastructure.session.SessionContextManager;

// 新增字段
private final MessageWindowChatMemory memory;

// 修改构造函数
public SREAgentGraphProcess(CompiledGraph compiledGraph, ObjectMapper objectMapper,
                             MessageWindowChatMemory memory) {
    this.compiledGraph = compiledGraph;
    this.objectMapper = objectMapper;
    this.eventDispatcher = new SREAgentEventDispatcher(objectMapper);
    this.memory = memory;
}
```

**Step 2: 在 `processStream` 中追踪最终 state 并写回**

在 `processStream` 方法中，添加追踪引用，并在 `doOnComplete` 里写回：

```java
public void processStream(String sessionId, Flux<NodeOutput> generator,
                          Sinks.Many<ServerSentEvent<String>> sink) {
    final String sessionIdStr = sessionId;
    // 追踪最后一个非 END 节点的 state
    final AtomicReference<OverAllState> finalStateRef = new AtomicReference<>();

    Future<?> future = executor.submit(() -> {
        ThinkingContextHolder.set(sink);
        generator.doOnNext(output -> {
            String nodeName = output.node();

            if (StateGraph.START.equals(nodeName) || StateGraph.END.equals(nodeName)) {
                return;
            }

            // 追踪最终 state（最后一个 Agent 节点的输出状态）
            finalStateRef.set(output.state());

            eventDispatcher.dispatch(output, sink);

        }).doOnComplete(() -> {
            // 写回记忆：input + result → UserMessage + AssistantMessage
            writeBackToMemory(sessionIdStr, finalStateRef.get());

            logger.info("Stream processing completed for session: {}", sessionIdStr);
            ThinkingContextHolder.clear();
            sink.tryEmitComplete();
            graphTaskFutureMap.remove(sessionIdStr);
        }).doOnError(e -> {
            logger.error("Error in stream processing", e);
            ThinkingContextHolder.clear();
            sink.tryEmitNext(ServerSentEvent.builder("{\"error\": \"" + e.getMessage() + "\"}").build());
            sink.tryEmitError(e);
        }).subscribe();
    });
    Future<?> oldFuture = graphTaskFutureMap.put(sessionIdStr, future);
    if (oldFuture != null && !oldFuture.isDone()) {
        logger.warn("A task with the same sessionId {} is still running!", sessionIdStr);
    }
}

/**
 * 将本轮对话写回 MessageWindowChatMemory
 * 仅在 sessionId 非空时写回（测试中使用空 sessionId 的场景跳过）
 */
private void writeBackToMemory(String sessionId, OverAllState state) {
    if (sessionId == null || sessionId.isEmpty() || state == null) {
        return;
    }
    String input = state.value("input", "");
    String result = state.value("result", "");
    if (!input.isEmpty() && !result.isEmpty()) {
        memory.add(sessionId, List.of(new UserMessage(input), new AssistantMessage(result)));
        logger.info("[Memory] write-back sessionId={}, input_len={}, result_len={}",
                sessionId, input.length(), result.length());
    }
}
```

**Step 3: Commit**

```bash
git add 06-SRE-Agent/src/main/java/com/yycome/sreagent/config/SREAgentGraphProcess.java
git commit -m "feat: write back UserMessage+AssistantMessage to memory after graph completes"
```

---

## Task 5: 更新 `SREAgentGraphConfiguration`

**Files:**
- Modify: `src/main/java/com/yycome/sreagent/config/SREAgentGraphConfiguration.java`

**Step 1: 替换注入**

```java
// 替换 import
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import com.yycome.sreagent.infrastructure.config.SessionProperties;
// 删除旧 import:
// import com.yycome.sreagent.infrastructure.session.SessionContextManager;

// 替换字段
@Autowired
private MessageWindowChatMemory messageWindowChatMemory;

@Autowired
private SessionProperties sessionProperties;

// 删除旧字段:
// @Autowired
// private SessionContextManager sessionContextManager;
```

**Step 2: 更新 `stateGraph()` Bean 中的节点构造**

```java
graph.addNode("router", node_async(new RouterNode(chatModel, messageWindowChatMemory, sessionProperties)))
     .addNode("queryAgent", node_async(queryAgentNode))
     .addNode("investigateAgent", node_async(new AgentNode(investigateAgent, "investigateAgent", tracingService, messageWindowChatMemory)))
     .addNode("admin", node_async(new AdminNode(environmentConfig, adminAgent, tracingService, chatModel, skillRegistry, entityRegistry)));
```

**Step 3: 更新 `sreAgentGraphProcess()` Bean**

```java
@Bean
public SREAgentGraphProcess sreAgentGraphProcess(
        @Qualifier("stateGraph") StateGraph stateGraph,
        ObjectMapper objectMapper,
        MessageWindowChatMemory messageWindowChatMemory)
        throws com.alibaba.cloud.ai.graph.exception.GraphStateException {
    CompileConfig compileConfig = CompileConfig.builder().build();
    CompiledGraph compiledGraph = stateGraph.compile(compileConfig);
    return new SREAgentGraphProcess(compiledGraph, objectMapper, messageWindowChatMemory);
}
```

**Step 4: 编译验证**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
mvn compile -f ../pom.xml -pl 06-SRE-Agent -q
```

期望：BUILD SUCCESS

**Step 5: Commit**

```bash
git add 06-SRE-Agent/src/main/java/com/yycome/sreagent/config/SREAgentGraphConfiguration.java
git commit -m "refactor: SREAgentGraphConfiguration inject MessageWindowChatMemory"
```

---

## Task 6: 删除旧的 Session 基础设施

**Files:**
- Delete: `src/main/java/com/yycome/sreagent/infrastructure/session/SessionContextManager.java`
- Delete: `src/main/java/com/yycome/sreagent/infrastructure/session/SessionContext.java`
- Delete: `src/test/java/com/yycome/sreagent/infrastructure/session/SessionContextManagerTest.java`

**Step 1: 删除文件**

```bash
rm 06-SRE-Agent/src/main/java/com/yycome/sreagent/infrastructure/session/SessionContextManager.java
rm 06-SRE-Agent/src/main/java/com/yycome/sreagent/infrastructure/session/SessionContext.java
rm 06-SRE-Agent/src/test/java/com/yycome/sreagent/infrastructure/session/SessionContextManagerTest.java
```

**Step 2: 检查是否还有残余引用**

```bash
grep -r "SessionContextManager\|SessionContext\|infrastructure\.session" \
  06-SRE-Agent/src --include="*.java"
```

期望：无输出（已全部清除）

**Step 3: 编译验证（全量）**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
mvn compile -f ../pom.xml -pl 06-SRE-Agent -q
```

期望：BUILD SUCCESS

**Step 4: Commit**

```bash
git add -u 06-SRE-Agent/src/
git commit -m "refactor: delete SessionContextManager, SessionContext and related test"
```

---

## Task 7: 运行测试验证

**Step 1: 运行单元测试**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
mvn test -f ../pom.xml -pl 06-SRE-Agent \
  -Dtest="ObservabilityAspectAnnotationTest,ToolExecutionTemplateTest,ToolResultTest,QueryScopeTest,EntityRegistryTest,OntologyQueryEngineTest,PersonalQuoteGatewayTest" \
  -Dsurefire.failIfNoSpecifiedTests=false
```

期望：所有单元测试通过（`SessionContextManagerTest` 已被删除，不再需要运行）

**Step 2: 检查日志**

```bash
grep -E "ERROR|WARN.*上下文未设置|一致性校验失败" log/sre-agent.log | tail -20
```

期望：无新增 ERROR

**Step 3: 运行集成测试**

```bash
./run-integration-tests.sh
```

期望：所有集成测试通过。集成测试是单轮调用，不依赖多轮记忆，不受本次改动影响。

**Step 4: Final commit（如有遗漏文件）**

```bash
git status
# 确认无遗漏，如有补充则 commit
```

---

## 变更总结

| 变更 | 类型 | 说明 |
|------|------|------|
| `MemoryConfig.java` | 新建 | 创建 `MessageWindowChatMemory` Bean |
| `SessionProperties.java` | 简化 | 删除 enabled/storeType/expireHours，保留 maxRecentTurns + confidenceThreshold |
| `RouterNode.java` | 重构 | 依赖 MessageWindowChatMemory，格式化历史消息为路由 prompt 上下文 |
| `AgentNode.java` | 重构 | 依赖 MessageWindowChatMemory，格式化历史消息注入 ReactAgent 输入 |
| `SREAgentGraphProcess.java` | 增强 | doOnComplete 写回 UserMessage + AssistantMessage |
| `SREAgentGraphConfiguration.java` | 更新 | 注入 MessageWindowChatMemory 替代 SessionContextManager |
| `application.yml` | 简化 | 删除废弃 session 配置项 |
| `SessionContextManager.java` | 删除 | - |
| `SessionContext.java` | 删除 | - |
| `SessionContextManagerTest.java` | 删除 | - |
