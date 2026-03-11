# SREmate 代码质量优化实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 按业务领域拆分工具类，提取执行模板消除重复代码，统一错误处理格式

**Architecture:** 新增 ToolExecutionTemplate 和 ToolResult 统一工具执行模式；拆分 ContractTool 为 ContractQueryTool、BudgetBillTool、SubOrderTool 三个类；使用 @DataQueryTool 注解替代白名单

**Tech Stack:** Java 21, Spring Boot 3.5.8, Spring AI Alibaba 1.1.2.0, AspectJ AOP, Lombok

---

## 阶段一：基础设施层改造

### Task 1: 新增 @DataQueryTool 注解

**Files:**
- Create: `src/main/java/com/yycome/sremate/infrastructure/annotation/DataQueryTool.java`

**Step 1: 创建注解文件**

```java
package com.yycome.sremate.infrastructure.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记工具为数据查询工具
 *
 * 被标记的工具结果会直接输出，跳过 LLM 最终回答生成。
 * 使用注解替代白名单，新增工具时无需修改 Aspect。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataQueryTool {
}
```

**Step 2: 提交**

```bash
git add 05-SREmate/src/main/java/com/yycome/sremate/infrastructure/annotation/DataQueryTool.java
git commit -m "feat: add @DataQueryTool annotation for marking data query tools"
```

---

### Task 2: 新增 ToolResult 统一结果类

**Files:**
- Create: `src/main/java/com/yycome/sremate/infrastructure/service/ToolResult.java`
- Create: `src/test/java/com/yycome/sremate/infrastructure/service/ToolResultTest.java`

**Step 1: 编写单元测试**

```java
package com.yycome.sremate.infrastructure.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolResultTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void success_shouldReturnJsonWithData() throws Exception {
        Map<String, Object> data = Map.of("contractCode", "C123", "status", 1);
        String result = ToolResult.success(data);

        Map<String, Object> parsed = mapper.readValue(result, Map.class);
        assertThat(parsed).containsEntry("contractCode", "C123");
        assertThat(parsed).containsEntry("status", 1);
        assertThat(parsed).doesNotContainKey("error");
    }

    @Test
    void error_shouldReturnJsonWithError() throws Exception {
        String result = ToolResult.error("未找到合同");

        Map<String, Object> parsed = mapper.readValue(result, Map.class);
        assertThat(parsed).containsEntry("error", "未找到合同");
        assertThat(parsed).hasSize(1);
    }

    @Test
    void notFound_shouldReturnJsonWithError() throws Exception {
        String result = ToolResult.notFound("合同", "C123");

        Map<String, Object> parsed = mapper.readValue(result, Map.class);
        assertThat(parsed).containsEntry("error", "未找到合同: C123");
    }

    @Test
    void error_withQuotes_shouldEscapeProperly() {
        String result = ToolResult.error("消息包含\"引号\"");

        assertThat(result).contains("\"error\"");
        assertThat(result).contains("消息包含'引号'");
    }
}
```

**Step 2: 运行测试确认失败**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test -pl 05-SREmate -Dtest=ToolResultTest
```

Expected: FAIL (ToolResult 类不存在)

**Step 3: 实现 ToolResult**

```java
package com.yycome.sremate.infrastructure.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 工具执行结果 - 统一的 JSON 结果格式
 */
public final class ToolResult {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ToolResult() {}

    /**
     * 成功结果
     */
    public static String success(Object data) {
        try {
            return MAPPER.writeValueAsString(data);
        } catch (Exception e) {
            return error("序列化失败: " + e.getMessage());
        }
    }

    /**
     * 错误结果
     */
    public static String error(String message) {
        try {
            return MAPPER.writeValueAsString(Map.of("error", message));
        } catch (Exception e) {
            // 降级处理：ObjectMapper 失败时手动构建
            return "{\"error\":\"" + message.replace("\"", "'") + "\"}";
        }
    }

    /**
     * 资源未找到
     */
    public static String notFound(String resource, String identifier) {
        return error("未找到" + resource + ": " + identifier);
    }
}
```

**Step 4: 运行测试确认通过**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test -pl 05-SREmate -Dtest=ToolResultTest
```

Expected: 4 tests PASS

**Step 5: 提交**

```bash
git add 05-SREmate/src/main/java/com/yycome/sremate/infrastructure/service/ToolResult.java \
        05-SREmate/src/test/java/com/yycome/sremate/infrastructure/service/ToolResultTest.java
git commit -m "feat: add ToolResult for unified tool result format"
```

---

### Task 3: 新增 ToolExecutionTemplate 工具执行模板

**Files:**
- Create: `src/main/java/com/yycome/sremate/infrastructure/service/ToolExecutionTemplate.java`
- Create: `src/test/java/com/yycome/sremate/infrastructure/service/ToolExecutionTemplateTest.java`

**Step 1: 编写单元测试**

```java
package com.yycome.sremate.infrastructure.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolExecutionTemplateTest {

    @Test
    void execute_withSuccess_shouldReturnResult() {
        String result = ToolExecutionTemplate.execute("testTool", () -> "success data");

        assertThat(result).isEqualTo("success data");
    }

    @Test
    void execute_withException_shouldReturnError() {
        String result = ToolExecutionTemplate.execute("testTool", () -> {
            throw new RuntimeException("测试错误");
        });

        assertThat(result).contains("\"error\"");
        assertThat(result).contains("测试错误");
    }

    @Test
    void execute_withNullResult_shouldReturnNull() {
        String result = ToolExecutionTemplate.execute("testTool", () -> null);

        assertThat(result).isNull();
    }
}
```

**Step 2: 运行测试确认失败**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test -pl 05-SREmate -Dtest=ToolExecutionTemplateTest
```

Expected: FAIL (ToolExecutionTemplate 类不存在)

**Step 3: 实现 ToolExecutionTemplate**

```java
package com.yycome.sremate.infrastructure.service;

import lombok.extern.slf4j.Slf4j;

/**
 * 工具执行模板 - 统一处理计时、日志、异常
 * 让工具类只需关注业务逻辑，遵循 DDD 业务语义编程思想
 */
@Slf4j
public final class ToolExecutionTemplate {

    private ToolExecutionTemplate() {}

    /**
     * 执行工具调用，统一处理日志和错误
     *
     * @param toolName 工具名称，用于日志输出
     * @param action 业务逻辑执行器
     * @return 工具执行结果（JSON 格式），若 action 返回 null 则返回 null
     */
    public static String execute(String toolName, ToolAction action) {
        long start = System.currentTimeMillis();
        try {
            String result = action.execute();
            if (result != null) {
                log.info("[TOOL] {} → {}ms, ok", toolName, System.currentTimeMillis() - start);
            }
            return result;
        } catch (Exception e) {
            log.error("[TOOL] {} → {}ms, error: {}", toolName, System.currentTimeMillis() - start, e.getMessage());
            return ToolResult.error(e.getMessage());
        }
    }

    @FunctionalInterface
    public interface ToolAction {
        String execute() throws Exception;
    }
}
```

**Step 4: 运行测试确认通过**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test -pl 05-SREmate -Dtest=ToolExecutionTemplateTest
```

Expected: 3 tests PASS

**Step 5: 提交**

```bash
git add 05-SREmate/src/main/java/com/yycome/sremate/infrastructure/service/ToolExecutionTemplate.java \
        05-SREmate/src/test/java/com/yycome/sremate/infrastructure/service/ToolExecutionTemplateTest.java
git commit -m "feat: add ToolExecutionTemplate to eliminate repetitive tool patterns"
```

---

## 阶段二：改造 ObservabilityAspect

### Task 4: 改造 ObservabilityAspect 使用 @DataQueryTool 注解

**Files:**
- Modify: `src/main/java/com/yycome/sremate/aspect/ObservabilityAspect.java`
- Create: `src/test/java/com/yycome/sremate/aspect/ObservabilityAspectAnnotationTest.java`

**Step 1: 查看当前 ObservabilityAspect 实现**

Read file: `05-SREmate/src/main/java/com/yycome/sremate/aspect/ObservabilityAspect.java`

**Step 2: 编写测试验证注解识别**

```java
package com.yycome.sremate.aspect;

import com.yycome.sremate.infrastructure.annotation.DataQueryTool;
import com.yycome.sremate.infrastructure.service.DirectOutputHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 ObservabilityAspect 通过 @DataQueryTool 注解识别数据查询工具
 */
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
class ObservabilityAspectAnnotationTest {

    @Autowired
    private TestTool testTool;

    @AfterEach
    void cleanup() {
        DirectOutputHolder.clear();
    }

    @Test
    void dataQueryToolAnnotation_shouldTriggerDirectOutput() {
        testTool.queryWithDataQueryAnnotation();

        assertThat(DirectOutputHolder.hasOutput()).isTrue();
        assertThat(DirectOutputHolder.getAndClear()).isEqualTo("{\"result\":\"ok\"}");
    }

    @Test
    void noDataQueryToolAnnotation_shouldNotTriggerDirectOutput() {
        testTool.queryWithoutAnnotation();

        assertThat(DirectOutputHolder.hasOutput()).isFalse();
    }

    /**
     * 测试用工具类
     */
    @Component
    static class TestTool {

        @Tool(description = "带 @DataQueryTool 注解的测试方法")
        @DataQueryTool
        public String queryWithDataQueryAnnotation() {
            return "{\"result\":\"ok\"}";
        }

        @Tool(description = "不带 @DataQueryTool 注解的测试方法")
        public String queryWithoutAnnotation() {
            return "{\"result\":\"ok\"}";
        }
    }
}
```

**Step 3: 运行测试确认失败**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test -pl 05-SREmate -Dtest=ObservabilityAspectAnnotationTest
```

Expected: FAIL (@DataQueryTool 注解尚未生效)

**Step 4: 修改 ObservabilityAspect**

在 `ObservabilityAspect.java` 中：

1. 移除 `DATA_QUERY_TOOLS` 白名单常量
2. 添加 `import com.yycome.sremate.infrastructure.annotation.DataQueryTool;`
3. 添加 `import org.aspectj.lang.reflect.MethodSignature;`
4. 添加 `import java.lang.reflect.Method;`
5. 修改工具调用后置处理逻辑

修改后的 `logToolCall` 方法核心部分：

```java
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

        // 通过反射检查方法是否有 @DataQueryTool 注解
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        boolean isDataQuery = method.isAnnotationPresent(DataQueryTool.class);
        if (isDataQuery && result != null) {
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
```

**Step 5: 运行测试确认通过**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test -pl 05-SREmate -Dtest=ObservabilityAspectAnnotationTest
```

Expected: 2 tests PASS

**Step 6: 运行现有集成测试确保无回归**

```bash
./05-SREmate/scripts/run-integration-tests.sh
```

Expected: 所有测试 PASS

**Step 7: 提交**

```bash
git add 05-SREmate/src/main/java/com/yycome/sremate/aspect/ObservabilityAspect.java \
        05-SREmate/src/test/java/com/yycome/sremate/aspect/ObservabilityAspectAnnotationTest.java
git commit -m "refactor: use @DataQueryTool annotation instead of whitelist in ObservabilityAspect"
```

---

## 阶段三：工具类拆分

### Task 5: 创建 SubOrderTool（子单查询工具）

**Files:**
- Create: `src/main/java/com/yycome/sremate/trigger/agent/SubOrderTool.java`

**Step 1: 创建 SubOrderTool**

从 ContractTool 中提取 `querySubOrderInfo` 方法：

```java
package com.yycome.sremate.trigger.agent;

import com.yycome.sremate.infrastructure.annotation.DataQueryTool;
import com.yycome.sremate.infrastructure.service.ToolExecutionTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 子单查询工具
 * 负责：子单基本信息查询
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubOrderTool {

    private final HttpEndpointTool httpEndpointTool;

    /**
     * 根据订单号查询子单信息
     *
     * @param homeOrderNo       订单号（必填）
     * @param quotationOrderNo  报价单号（可选）
     * @param projectChangeNo   变更单号（可选）
     * @return JSON格式子单信息
     */
    @Tool(description = """
            【子单查询】用户提到"子单"或"S单"时使用。

            触发条件：包含关键词"子单"、"S单"

            参数：
            - homeOrderNo：订单号（必填）
            - quotationOrderNo：报价单号（可选，GBILL前缀）
            - projectChangeNo：变更单号（可选）

            示例：
            - "825123110000002753的子单" → homeOrderNo=825123110000002753
            - "825123110000002753下GBILL260309110407580001的子单" → 加上quotationOrderNo""")
    @DataQueryTool
    public String querySubOrderInfo(String homeOrderNo, String quotationOrderNo, String projectChangeNo) {
        return ToolExecutionTemplate.execute("querySubOrderInfo", () -> {
            Map<String, String> params = new HashMap<>();
            params.put("homeOrderNo", homeOrderNo);
            params.put("quotationOrderNo", quotationOrderNo != null ? quotationOrderNo : "");
            params.put("projectChangeNo", projectChangeNo != null ? projectChangeNo : "");
            return httpEndpointTool.callPredefinedEndpoint("sub-order-info", params);
        });
    }
}
```

**Step 2: 运行子单查询集成测试**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test -pl 05-SREmate -Dtest=SubOrderToolIT
```

Expected: 测试 PASS（需要确认 HttpEndpointTool 已注册为新工具）

**Step 3: 提交**

```bash
git add 05-SREmate/src/main/java/com/yycome/sremate/trigger/agent/SubOrderTool.java
git commit -m "feat: create SubOrderTool for sub-order queries (extracted from ContractTool)"
```

---

### Task 6: 创建 BudgetBillTool（报价单查询工具）

**Files:**
- Create: `src/main/java/com/yycome/sremate/trigger/agent/BudgetBillTool.java`

**Step 1: 创建 BudgetBillTool**

从 ContractTool 中提取 `queryBudgetBillList` 和 `querySubOrdersForBill` 方法：

```java
package com.yycome.sremate.trigger.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yycome.sremate.infrastructure.annotation.DataQueryTool;
import com.yycome.sremate.infrastructure.service.ToolExecutionTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 报价单查询工具
 * 负责：报价单列表及其子单聚合查询
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BudgetBillTool {

    private final HttpEndpointTool httpEndpointTool;
    private final ObjectMapper objectMapper;

    /**
     * 根据项目订单号查询报价单列表
     *
     * @param projectOrderId 项目订单号，纯数字格式
     * @return 过滤后的报价单列表 JSON（含子单聚合）
     */
    @Tool(description = """
            【报价单查询】用户提到"报价单"或"报价"时使用。

            触发条件：包含关键词"报价单"、"报价"、"报价列表"

            参数：
            - projectOrderId：纯数字订单号（必填）

            示例：
            - "826031111000001859的报价单" → projectOrderId=826031111000001859
            - "查询826031111000001859报价单列表" → projectOrderId=826031111000001859

            ⚠️ 注意：报价单 ≠ 子单，不要用子单工具查报价单""")
    @DataQueryTool
    public String queryBudgetBillList(String projectOrderId) {
        return ToolExecutionTemplate.execute("queryBudgetBillList", () -> {
            // 1. 获取报价单列表（已过滤字段）
            String billListJson = httpEndpointTool.callPredefinedEndpoint("budget-bill-list",
                    Map.of("projectOrderId", projectOrderId));

            // 2. 逐条报价单查询子单并聚合
            JsonNode billListNode = objectMapper.readTree(billListJson);
            ObjectNode result = objectMapper.createObjectNode();

            for (String listKey : List.of("decorateBudgetList", "personalBudgetList")) {
                JsonNode list = billListNode.path(listKey);
                if (!list.isArray()) {
                    result.set(listKey, list);
                    continue;
                }
                ArrayNode enrichedList = objectMapper.createArrayNode();
                for (JsonNode bill : list) {
                    ObjectNode enrichedBill = (ObjectNode) bill.deepCopy();
                    String billCode = bill.path("billCode").asText(null);
                    enrichedBill.set("subOrders", querySubOrdersForBill(projectOrderId, billCode));
                    enrichedList.add(enrichedBill);
                }
                result.set(listKey, enrichedList);
            }

            return objectMapper.writeValueAsString(result);
        });
    }

    /**
     * 查询单条报价单对应的子单列表，提取 orderNo/projectChangeNo/mdmCode/dueAmount
     */
    private ArrayNode querySubOrdersForBill(String projectOrderId, String billCode) {
        ArrayNode subOrders = objectMapper.createArrayNode();
        if (billCode == null || billCode.isBlank()) return subOrders;
        try {
            String raw = httpEndpointTool.callPredefinedEndpointRaw("sub-order-info",
                    Map.of("homeOrderNo", projectOrderId, "quotationOrderNo", billCode, "projectChangeNo", ""));
            if (raw == null) return subOrders;

            JsonNode data = objectMapper.readTree(raw).path("data");
            if (!data.isArray()) return subOrders;

            for (JsonNode item : data) {
                ObjectNode subOrder = objectMapper.createObjectNode();
                subOrder.set("orderNo", item.path("orderNo"));
                subOrder.set("projectChangeNo", item.path("projectChangeNo"));
                subOrder.set("mdmCode", item.path("mdmCode"));
                // dueAmount 不是所有接口版本都返回，缺失时不写入
                if (!item.path("dueAmount").isMissingNode()) {
                    subOrder.set("dueAmount", item.path("dueAmount"));
                }
                subOrders.add(subOrder);
            }
        } catch (Exception e) {
            log.warn("querySubOrdersForBill 失败 billCode={}: {}", billCode, e.getMessage());
        }
        return subOrders;
    }
}
```

**Step 2: 运行报价单查询集成测试**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test -pl 05-SREmate -Dtest=BudgetBillToolIT
```

Expected: 测试 PASS

**Step 3: 提交**

```bash
git add 05-SREmate/src/main/java/com/yycome/sremate/trigger/agent/BudgetBillTool.java
git commit -m "feat: create BudgetBillTool for budget bill queries (extracted from ContractTool)"
```

---

### Task 7: 创建 ContractQueryTool（合同查询工具）

**Files:**
- Create: `src/main/java/com/yycome/sremate/trigger/agent/ContractQueryTool.java`

**Step 1: 创建 ContractQueryTool**

从 ContractTool 中提取合同相关查询方法：

```java
package com.yycome.sremate.trigger.agent;

import com.yycome.sremate.domain.contract.service.ContractQueryService;
import com.yycome.sremate.infrastructure.annotation.DataQueryTool;
import com.yycome.sremate.infrastructure.service.ToolExecutionTemplate;
import com.yycome.sremate.infrastructure.service.ToolResult;
import com.yycome.sremate.types.enums.QueryDataType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 合同数据查询工具
 * 负责：合同基础数据、实例ID、版式、配置表的查询
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractQueryTool {

    private final ContractQueryService contractQueryService;
    private final ObjectMapper objectMapper;

    /**
     * 根据合同编号查询合同数据（支持4种查询类型）
     */
    @Tool(description = """
            【合同编号查询】用户输入包含C前缀编号时使用。

            触发条件：编号以C开头（如C1767173898135504）

            参数：
            - contractCode：C前缀+数字（必填）
            - dataType：查询范围
              | 用户说 | dataType值 |
              |--------|-----------|
              | 合同数据/详情/信息 | ALL |
              | 节点/日志 | CONTRACT_NODE |
              | 字段 | CONTRACT_FIELD |
              | 签约人/参与人 | CONTRACT_USER |

            示例：
            - "C1767173898135504合同数据" → contractCode=C1767173898135504, dataType=ALL
            - "C1767173898135504签约人" → dataType=CONTRACT_USER

            ⚠️ 注意：纯数字是订单号，请用queryContractsByOrderId""")
    @DataQueryTool
    public String queryContractData(String contractCode, String dataType) {
        return ToolExecutionTemplate.execute("queryContractData", () -> {
            // 防御性校验：纯数字说明是订单号，LLM 调错了工具
            if (contractCode != null && contractCode.matches("\\d+")) {
                return ToolResult.error("参数错误：" + contractCode + " 是订单号（纯数字），请使用 queryContractsByOrderId 工具查询订单下的合同");
            }
            QueryDataType type = QueryDataType.valueOf(dataType.toUpperCase());
            Map<String, Object> result = contractQueryService.queryByCode(contractCode, type);
            if (result == null) {
                return ToolResult.notFound("合同", contractCode);
            }
            return contractQueryService.toJson(result);
        });
    }

    /**
     * 根据项目订单号查询所有合同完整详情
     */
    @Tool(description = """
            【订单号查询】用户输入包含纯数字编号时使用。

            触发条件：编号为纯数字（如825123110000002753）

            参数：
            - projectOrderId：纯数字订单号（必填）

            示例：
            - "825123110000002753有哪些合同" → projectOrderId=825123110000002753
            - "订单825123110000002753的合同详情" → projectOrderId=825123110000002753

            ⚠️ 注意：C前缀是合同号，请用queryContractData""")
    @DataQueryTool
    public String queryContractsByOrderId(String projectOrderId) {
        return ToolExecutionTemplate.execute("queryContractsByOrderId", () -> {
            List<Map<String, Object>> result = contractQueryService.queryByOrderId(projectOrderId);
            if (result == null) {
                return ToolResult.error("订单 " + projectOrderId + " 下未找到合同记录");
            }
            return contractQueryService.toJson(result);
        });
    }

    /**
     * 根据合同编号查询 platform_instance_id
     */
    @Tool(description = """
            【查询实例ID】仅查询platform_instance_id时使用。

            触发条件：用户明确问"instance_id"或"实例ID"

            参数：
            - contractCode：C前缀合同号（必填）

            示例："C1767173898135504的instance_id"

            💡 提示：如需版式form_id，直接用queryContractFormId""")
    @DataQueryTool
    public String queryContractInstanceId(String contractCode) {
        return ToolExecutionTemplate.execute("queryContractInstanceId", () -> {
            Long instanceId = contractQueryService.queryInstanceId(contractCode);
            if (instanceId == null) {
                return ToolResult.notFound("合同", contractCode);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("contractCode", contractCode);
            result.put("platformInstanceId", instanceId);
            return contractQueryService.toJson(result);
        });
    }

    /**
     * 根据合同编号查询版式 form_id
     */
    @Tool(description = """
            【版式查询】仅当用户提到"版式"或"form_id"时使用。

            触发条件：包含关键词"版式"、"form_id"、"版式ID"

            参数：
            - contractCode：C前缀合同号（必填）

            示例："C1767173898135504的版式form_id"

            ⚠️ 禁止：用户说"合同数据"时不能用此工具""")
    @DataQueryTool
    public String queryContractFormId(String contractCode) {
        return ToolExecutionTemplate.execute("queryContractFormId", () -> {
            String result = contractQueryService.queryFormId(contractCode);
            if (result == null) {
                return ToolResult.error("未找到合同编号为 " + contractCode + " 的合同记录，无法查询版式");
            }
            return result;
        });
    }

    /**
     * 查询合同配置表
     */
    @Tool(description = """
            【合同配置表查询】用户提到"配置表"或"合同配置"时使用。

            触发条件：包含关键词"配置表"、"合同配置"

            参数：
            - contractOrOrderId：合同号或订单号（自动识别格式）
            - contractType：合同类型（订单号查询时必填）

            支持的类型：认购(1)、设计(2)、正签(3)、套餐变更(4)、首期款(5)、
            整装首期(6)、图纸(7)、销售(8)、设计变更(11)、补充协议(29)、和解(30)

            示例：
            - "C1767173898135504的配置表" → contractOrOrderId=C1767173898135504, contractType可空
            - "825123110000002753的销售合同配置" → contractOrOrderId=825123110000002753, contractType=销售""")
    @DataQueryTool
    public String queryContractConfig(String contractOrOrderId, String contractType) {
        return ToolExecutionTemplate.execute("queryContractConfig", () -> {
            // 自动识别编号类型
            String contractCode = null;
            String projectOrderId = null;

            if (contractOrOrderId != null && !contractOrOrderId.isBlank()) {
                if (contractOrOrderId.toUpperCase().startsWith("C")) {
                    contractCode = contractOrOrderId;
                } else {
                    projectOrderId = contractOrOrderId;
                }
            }

            Map<String, Object> result = contractQueryService.queryContractConfig(contractCode, projectOrderId, contractType);
            if (result == null) {
                return ToolResult.error("未找到编号 " + contractOrOrderId + " 对应的合同记录");
            }
            return contractQueryService.toJson(result);
        });
    }
}
```

**Step 2: 运行合同查询集成测试**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test -pl 05-SREmate -Dtest=ContractQueryToolIT,ContractInstanceToolIT,ContractConfigToolIT
```

Expected: 测试 PASS

**Step 3: 提交**

```bash
git add 05-SREmate/src/main/java/com/yycome/sremate/trigger/agent/ContractQueryTool.java
git commit -m "feat: create ContractQueryTool for contract data queries (extracted from ContractTool)"
```

---

### Task 8: 删除原 ContractTool

**Files:**
- Delete: `src/main/java/com/yycome/sremate/trigger/agent/ContractTool.java`

**Step 1: 确认所有测试通过**

```bash
./05-SREmate/scripts/run-integration-tests.sh
```

Expected: 所有测试 PASS

**Step 2: 删除 ContractTool**

```bash
git rm 05-SREmate/src/main/java/com/yycome/sremate/trigger/agent/ContractTool.java
git commit -m "refactor: remove ContractTool (replaced by ContractQueryTool, BudgetBillTool, SubOrderTool)"
```

---

## 阶段四：统一错误处理

### Task 9: 改造 HttpEndpointTool 统一错误处理

**Files:**
- Modify: `src/main/java/com/yycome/sremate/trigger/agent/HttpEndpointTool.java`

**Step 1: 修改 HttpEndpointTool**

1. 注入 ObjectMapper 替代自己创建
2. 使用 ToolResult 统一错误格式
3. 添加 @DataQueryTool 注解

修改后的关键部分：

```java
@Slf4j
@Component
public class HttpEndpointTool {

    private final WebClient webClient;
    private final EndpointTemplateService endpointTemplateService;
    private final ObjectMapper objectMapper;  // 改为注入

    public HttpEndpointTool(WebClient.Builder webClientBuilder,
                            EndpointTemplateService endpointTemplateService,
                            ObjectMapper objectMapper) {  // 注入
        this.webClient = webClientBuilder
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
        this.endpointTemplateService = endpointTemplateService;
        this.objectMapper = objectMapper;
    }

    @Tool(description = "...")
    @DataQueryTool
    public String callPredefinedEndpoint(String endpointId, Map<String, String> params) {
        return ToolExecutionTemplate.execute("callPredefinedEndpoint", () -> {
            EndpointTemplate template = endpointTemplateService.getTemplate(endpointId);
            if (template == null) {
                return ToolResult.error("未找到接口模板: " + endpointId + "\n使用 listAvailableEndpoints 查看可用接口");
            }

            Map<String, String> safeParams = params != null ? params : new HashMap<>();
            endpointTemplateService.validateParameters(template, safeParams);

            Map<String, String> filledParams = endpointTemplateService.fillDefaultValues(template, safeParams);
            String url = endpointTemplateService.buildUrl(template, filledParams);

            ResponseEntity<String> responseEntity = executeHttpRequest(template, url, filledParams)
                    .timeout(Duration.ofSeconds(template.getTimeout()))
                    .block();

            String response = responseEntity.getBody();

            // 字段过滤：配置了 responseFields 时，直接返回过滤后的纯 JSON
            if (template.getResponseFields() != null && !template.getResponseFields().isEmpty()) {
                return filterResponseFields(response, template.getResponseFields());
            }

            return String.format("接口: %s (%s)\n名称: %s\n响应:\n%s",
                    endpointId, template.getName(), url, response);
        });
    }

    // 其他方法类似修改...
}
```

**Step 2: 运行 HTTP 接口集成测试**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test -pl 05-SREmate -Dtest=HttpEndpointToolIT
```

Expected: 测试 PASS

**Step 3: 提交**

```bash
git add 05-SREmate/src/main/java/com/yycome/sremate/trigger/agent/HttpEndpointTool.java
git commit -m "refactor: inject ObjectMapper and use ToolResult in HttpEndpointTool"
```

---

## 阶段五：最终验证

### Task 10: 运行全部集成测试

**Step 1: 运行全部集成测试**

```bash
./05-SREmate/scripts/run-integration-tests.sh
```

Expected: 所有测试 PASS

**Step 2: 最终提交（如有遗漏）**

```bash
git status
git add -A
git commit -m "feat: complete SREmate code quality optimization - split tools, add template, unify errors"
```

---

## 变更文件清单

| 文件 | 变更类型 |
|------|----------|
| `infrastructure/annotation/DataQueryTool.java` | 新增 |
| `infrastructure/service/ToolResult.java` | 新增 |
| `infrastructure/service/ToolExecutionTemplate.java` | 新增 |
| `trigger/agent/ContractQueryTool.java` | 新增 |
| `trigger/agent/BudgetBillTool.java` | 新增 |
| `trigger/agent/SubOrderTool.java` | 新增 |
| `trigger/agent/ContractTool.java` | 删除 |
| `trigger/agent/HttpEndpointTool.java` | 修改 |
| `aspect/ObservabilityAspect.java` | 修改 |
| `test/.../ToolResultTest.java` | 新增 |
| `test/.../ToolExecutionTemplateTest.java` | 新增 |
| `test/.../ObservabilityAspectAnnotationTest.java` | 新增 |
