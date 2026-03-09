# SREmate Enhancement Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 修复 querySubOrderInfo 工具的提示词缺失问题，并实现数据查询场景下跳过 LLM 最终回答生成、直接输出工具结果的性能优化。

**Architecture:** 任务一仅修改系统提示词文件，无需改 Java 代码。任务二新增 `DirectOutputHolder`（ThreadLocal 容器），在 `ObservabilityAspect` 中识别数据查询工具并写入结果，在 `SREConsole` 的流式订阅中检测后直接打印工具结果、取消后续 LLM token 流。

**Tech Stack:** Java 21, Spring Boot 3.5.8, Spring AI Alibaba 1.1.2.0, Project Reactor (Flux/Disposable), AspectJ AOP

---

## 任务一：修复 querySubOrderInfo 提示词缺失

### Task 1: 在 sre-agent.md 中补充 querySubOrderInfo 工具说明

**Files:**
- Modify: `src/main/resources/prompts/sre-agent.md`

**Step 1: 在"可用工具"章节补充 querySubOrderInfo 说明**

在 `sre-agent.md` 第 53 行（`### 3. callPredefinedEndpoint`）之前插入以下内容：

```markdown
### 2c. querySubOrderInfo
根据订单号查询对应的子单基本信息，支持按报价单号和变更单号筛选。
- 参数：
  - homeOrderNo: 订单号（**必填**），纯数字格式，如 826030611000000795
  - quotationOrderNo: 报价单号（可选），GBILL前缀+数字，如 GBILL260309110407580001
  - projectChangeNo: 变更单号（可选）
- 使用场景：
  - 用户询问"某订单下某报价单的子单信息"时使用此工具
  - 用户询问"826030611000000795下GBILL260309110407580001的子单信息"时使用此工具
- **与 sign-order-list 的区别**：
  - `sign-order-list`（callPredefinedEndpoint）：查询订单下**可签约的 S 单列表**（签约业务场景）
  - `querySubOrderInfo`（本工具）：查询子单的**基本信息详情**（排查场景）
```

**Step 2: 在示例对话章节末尾补充子单查询示例**

在 `sre-agent.md` 最后追加：

```markdown
---

**示例5（子单基本信息查询）：**

**用户：** 826030611000000795下GBILL260309110407580001的子单信息

**助手：**
{"subOrderList":[{"subOrderId":"xxx","homeOrderNo":"826030611000000795","quotationOrderNo":"GBILL260309110407580001","status":1}]}
```

**Step 3: 提交**

```bash
git add src/main/resources/prompts/sre-agent.md
git commit -m "fix: add querySubOrderInfo tool description to system prompt"
```

---

### Task 2: 验证修复效果（集成测试）

**Files:**
- Create: `src/test/java/com/yycome/sremate/SubOrderQueryIT.java`

**Step 1: 编写集成测试**

```java
package com.yycome.sremate;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 子单查询端到端集成测试
 *
 * 运行：
 *   JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
 *     mvn test -pl 05-SREmate -Dtest=SubOrderQueryIT
 */
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
class SubOrderQueryIT {

    // 替换为真实存在的订单号和报价单号
    private static final String HOME_ORDER_NO = "826030611000000795";
    private static final String QUOTATION_ORDER_NO = "GBILL260309110407580001";

    @Autowired
    private ChatClient sreAgent;

    @Test
    void querySubOrder_withBothParams_shouldReturnSubOrderData() {
        String response = sreAgent.prompt()
                .user(HOME_ORDER_NO + "下" + QUOTATION_ORDER_NO + "的子单信息")
                .call()
                .content();

        System.out.println("=== Agent 回复 ===\n" + response);

        // Agent 不应该回答"没有找到对应的工具"
        assertThat(response).doesNotContain("没有找到对应的工具");
        // 不应该是错误响应
        assertThat(response).doesNotContain("\"error\"");
    }

    @Test
    void querySubOrder_withOnlyOrderNo_shouldReturnSubOrderData() {
        String response = sreAgent.prompt()
                .user("查询订单" + HOME_ORDER_NO + "的子单")
                .call()
                .content();

        System.out.println("=== Agent 回复（仅订单号）===\n" + response);

        assertThat(response).doesNotContain("没有找到对应的工具");
        assertThat(response).doesNotContain("\"error\"");
    }
}
```

**Step 2: 运行测试确认通过**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test -pl 05-SREmate -Dtest=SubOrderQueryIT
```

Expected: 两个测试均 PASS，Agent 正确调用 `querySubOrderInfo` 工具并返回数据。

**Step 3: 提交测试文件**

```bash
git add src/test/java/com/yycome/sremate/SubOrderQueryIT.java
git commit -m "test: add SubOrderQueryIT to verify querySubOrderInfo tool routing"
```

---

## 任务二：数据查询场景性能优化 —— 跳过 LLM 最终回答生成

### Task 3: 新增 DirectOutputHolder

**Files:**
- Create: `src/main/java/com/yycome/sremate/infrastructure/service/DirectOutputHolder.java`

**Step 1: 编写单元测试**

Create: `src/test/java/com/yycome/sremate/service/DirectOutputHolderTest.java`

```java
package com.yycome.sremate.service;

import com.yycome.sremate.infrastructure.service.DirectOutputHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DirectOutputHolderTest {

    @AfterEach
    void cleanup() {
        DirectOutputHolder.clear();
    }

    @Test
    void hasResult_returnsFalse_whenEmpty() {
        assertThat(DirectOutputHolder.hasResult()).isFalse();
        assertThat(DirectOutputHolder.get()).isNull();
    }

    @Test
    void set_and_get_returnsStoredValue() {
        DirectOutputHolder.set("{\"key\":\"value\"}");

        assertThat(DirectOutputHolder.hasResult()).isTrue();
        assertThat(DirectOutputHolder.get()).isEqualTo("{\"key\":\"value\"}");
    }

    @Test
    void clear_removesValue() {
        DirectOutputHolder.set("{\"key\":\"value\"}");
        DirectOutputHolder.clear();

        assertThat(DirectOutputHolder.hasResult()).isFalse();
        assertThat(DirectOutputHolder.get()).isNull();
    }

    @Test
    void threadIsolation_differentThreadsHaveIndependentValues() throws InterruptedException {
        DirectOutputHolder.set("main-thread-value");

        String[] childResult = {null};
        Thread child = new Thread(() -> childResult[0] = DirectOutputHolder.get());
        child.start();
        child.join();

        // 子线程看不到主线程设置的值
        assertThat(childResult[0]).isNull();
        // 主线程值不受影响
        assertThat(DirectOutputHolder.get()).isEqualTo("main-thread-value");
    }
}
```

**Step 2: 运行测试确认失败**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test -pl 05-SREmate -Dtest=DirectOutputHolderTest
```

Expected: FAIL，`DirectOutputHolder` 类不存在

**Step 3: 实现 DirectOutputHolder**

```java
package com.yycome.sremate.infrastructure.service;

/**
 * 数据查询工具结果直接输出容器
 * 当工具为数据查询类型时，将结果存入此 ThreadLocal，
 * 由 SREConsole 直接打印，跳过 LLM 最终回答生成。
 */
public class DirectOutputHolder {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private DirectOutputHolder() {}

    public static void set(String result) {
        HOLDER.set(result);
    }

    public static String get() {
        return HOLDER.get();
    }

    public static boolean hasResult() {
        return HOLDER.get() != null;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
```

**Step 4: 运行测试确认通过**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test -pl 05-SREmate -Dtest=DirectOutputHolderTest
```

Expected: 4 个测试全部 PASS

**Step 5: 提交**

```bash
git add src/main/java/com/yycome/sremate/infrastructure/service/DirectOutputHolder.java \
        src/test/java/com/yycome/sremate/service/DirectOutputHolderTest.java
git commit -m "feat: add DirectOutputHolder for bypassing LLM final answer on data queries"
```

---

### Task 4: 修改 ObservabilityAspect —— 数据查询工具后写入 DirectOutputHolder

**Files:**
- Modify: `src/main/java/com/yycome/sremate/aspect/ObservabilityAspect.java`

**Step 1: 编写单元测试**

在现有 `InfrastructureServiceTest.java` 中了解测试风格后，创建：
`src/test/java/com/yycome/sremate/aspect/ObservabilityAspectDirectOutputTest.java`

```java
package com.yycome.sremate.aspect;

import com.yycome.sremate.infrastructure.service.DirectOutputHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 ObservabilityAspect 对数据查询工具调用后正确写入 DirectOutputHolder
 *
 * 运行：
 *   JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
 *     mvn test -pl 05-SREmate -Dtest=ObservabilityAspectDirectOutputTest
 */
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
class ObservabilityAspectDirectOutputTest {

    @Autowired
    private com.yycome.sremate.trigger.agent.ContractTool contractTool;

    @AfterEach
    void cleanup() {
        DirectOutputHolder.clear();
    }

    @Test
    void queryContractData_isDataQueryTool_shouldWriteToHolder() {
        // 直接调用工具方法（AOP 会拦截），验证 Holder 被写入
        contractTool.queryContractInstanceId("C1772854666284956");

        // ObservabilityAspect 应将工具结果写入 DirectOutputHolder
        assertThat(DirectOutputHolder.hasResult()).isTrue();
        assertThat(DirectOutputHolder.get()).isNotBlank();
    }
}
```

**Step 2: 运行测试确认失败**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test -pl 05-SREmate -Dtest=ObservabilityAspectDirectOutputTest
```

Expected: FAIL，`DirectOutputHolder.hasResult()` 为 false（Aspect 尚未写入）

**Step 3: 修改 ObservabilityAspect**

在 `ObservabilityAspect.java` 中：

1. 注入 `DirectOutputHolder`（静态工具类，无需注入，直接调用）
2. 定义数据查询工具白名单 `Set<String>`
3. 工具调用成功后，若工具名在白名单中，写入 `DirectOutputHolder`

修改后的完整文件：

```java
package com.yycome.sremate.aspect;

import com.yycome.sremate.infrastructure.service.DirectOutputHolder;
import com.yycome.sremate.infrastructure.service.MetricsCollector;
import com.yycome.sremate.infrastructure.service.TracingService;
import com.yycome.sremate.infrastructure.service.model.TracingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 可观测性切面
 * 记录工具调用的详细信息，集成追踪和指标收集。
 * 对数据查询类工具，调用成功后将结果写入 DirectOutputHolder，
 * 由 SREConsole 直接输出，跳过 LLM 最终回答生成。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ObservabilityAspect {

    private final TracingService tracingService;
    private final MetricsCollector metricsCollector;

    /**
     * 数据查询工具白名单：这些工具的结果直接输出，不经过 LLM 二次生成。
     */
    private static final Set<String> DATA_QUERY_TOOLS = Set.of(
            "queryContractData",
            "queryContractsByOrderId",
            "queryContractInstanceId",
            "queryContractFormId",
            "queryContractConfig",
            "querySubOrderInfo",
            "callPredefinedEndpoint"
    );

    @Around("@annotation(org.springframework.ai.tool.annotation.Tool)")
    public Object logToolCall(ProceedingJoinPoint joinPoint) throws Throwable {
        String toolName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();
        long startTime = System.currentTimeMillis();

        Map<String, Object> params = buildParamsMap(args);
        TracingContext tracing = tracingService.startToolCall(toolName, params);

        log.info("[TOOL_CALL] 开始调用工具: {}, 参数: {}", toolName, Arrays.toString(args));

        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;

            tracingService.endToolCall(tracing, result);
            metricsCollector.recordToolCall(toolName, duration, true);

            log.info("[TOOL_CALL] 工具调用成功: {}, 耗时: {}ms", toolName, duration);

            // 数据查询工具：将结果写入 DirectOutputHolder，SREConsole 直接输出
            if (DATA_QUERY_TOOLS.contains(toolName) && result != null) {
                DirectOutputHolder.set(result.toString());
            }

            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;

            tracingService.failToolCall(tracing, e);
            metricsCollector.recordToolCall(toolName, duration, false);

            log.error("[TOOL_CALL] 工具调用失败: {}, 耗时: {}ms, 错误: {}",
                    toolName, duration, e.getMessage());
            throw e;
        }
    }

    private Map<String, Object> buildParamsMap(Object[] args) {
        Map<String, Object> params = new HashMap<>();
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                params.put("arg" + i, args[i]);
            }
        }
        return params;
    }
}
```

**Step 4: 运行测试确认通过**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test -pl 05-SREmate -Dtest=ObservabilityAspectDirectOutputTest
```

Expected: PASS

**Step 5: 提交**

```bash
git add src/main/java/com/yycome/sremate/aspect/ObservabilityAspect.java \
        src/test/java/com/yycome/sremate/aspect/ObservabilityAspectDirectOutputTest.java
git commit -m "feat: write data query tool results to DirectOutputHolder in ObservabilityAspect"
```

---

### Task 5: 修改 SREConsole —— 检测 DirectOutputHolder 并直接输出

**Files:**
- Modify: `src/main/java/com/yycome/sremate/trigger/console/SREConsole.java`

**Step 1: 理解当前流式输出逻辑**

当前 `SREConsole.java:127-146` 的流式订阅：
```java
Disposable sub = sreAgent.prompt()
        .messages(conversationHistory)
        .stream()
        .content()
        .doOnNext(chunk -> {
            if (firstTokenMs[0] < 0) firstTokenMs[0] = System.currentTimeMillis();
            System.out.print(chunk);           // 逐 token 打印 LLM 输出
            responseBuilder.append(chunk);
        })
        .doOnComplete(() -> { System.out.println(); latch.countDown(); })
        .doOnError(e -> latch.countDown())
        .doOnCancel(latch::countDown)
        .subscribe();
```

需要改造成：当 `DirectOutputHolder` 中有值时，停止打印 LLM token，直接输出工具结果。

**Step 2: 修改 SREConsole 中的流式输出逻辑**

在 `SREConsole.java` 的主循环内，替换流式订阅部分（当前第 126-146 行）为：

```java
// 每次请求前清理上一次的直接输出结果
DirectOutputHolder.clear();

AtomicBoolean directOutputDone = new AtomicBoolean(false);

Disposable sub = sreAgent.prompt()
        .messages(conversationHistory)
        .stream()
        .content()
        .doOnNext(chunk -> {
            if (firstTokenMs[0] < 0) {
                firstTokenMs[0] = System.currentTimeMillis();
            }
            // 若已检测到直接输出结果，丢弃后续 LLM token
            if (directOutputDone.get()) {
                return;
            }
            // 检测 DirectOutputHolder：有结果则直接输出并停止打印 LLM token
            if (DirectOutputHolder.hasResult()) {
                directOutputDone.set(true);
                String toolResult = DirectOutputHolder.get();
                System.out.println(toolResult);
                responseBuilder.append(toolResult);
                return;
            }
            System.out.print(chunk);
            responseBuilder.append(chunk);
        })
        .doOnComplete(() -> {
            // 如果流结束时 Holder 中仍有结果但尚未输出（极端情况），补输出
            if (!directOutputDone.get() && DirectOutputHolder.hasResult()) {
                String toolResult = DirectOutputHolder.get();
                System.out.println(toolResult);
                responseBuilder.append(toolResult);
            }
            System.out.println();
            latch.countDown();
        })
        .doOnError(e -> latch.countDown())
        .doOnCancel(latch::countDown)
        .subscribe();
currentSubscription.set(sub);
latch.await();
currentSubscription.set(null);
```

同时在 `latch.await()` 之后、`interrupted` 判断之前，清理 Holder：

```java
DirectOutputHolder.clear();
```

还需要在文件头部添加 import：
```java
import com.yycome.sremate.infrastructure.service.DirectOutputHolder;
```

**Step 3: 手动验证（无法单元测试 CLI，通过集成测试替代）**

重新运行合同查询集成测试，确保现有功能不受影响：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test -pl 05-SREmate -Dtest=ContractDataQueryIT,ContractConfigQueryIT,ContractFormQueryIT,ContractListQueryIT
```

Expected: 所有现有集成测试 PASS（功能无回归）

**Step 4: 提交**

```bash
git add src/main/java/com/yycome/sremate/trigger/console/SREConsole.java
git commit -m "feat: bypass LLM final answer for data query tools in SREConsole"
```

---

### Task 6: 端到端验证两个任务的联合效果

**Step 1: 运行全部集成测试**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test -pl 05-SREmate -Dtest=ContractDataQueryIT,ContractConfigQueryIT,ContractFormQueryIT,ContractListQueryIT,SubOrderQueryIT
```

Expected: 全部 PASS

**Step 2: 启动应用手动验证**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn spring-boot:run -pl 05-SREmate -Dspring-boot.run.profiles=local
```

验证场景：

| 输入 | 期望行为 |
|---|---|
| `826030611000000795下GBILL260309110407580001的子单信息` | 正确调用 querySubOrderInfo，直接输出 JSON，无 LLM 转述 |
| `C1772854666284956合同数据` | 直接输出工具 JSON，响应明显变快 |
| `数据库连接超时怎么办` | 正常走 LLM 生成，输出排查建议 |

**Step 3: 最终提交（如有遗漏文件）**

```bash
git status
git add -A
git commit -m "feat: complete SREmate enhancement - fix sub-order tool prompt and optimize data query performance"
```
