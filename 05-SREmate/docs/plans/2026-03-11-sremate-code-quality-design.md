# SREmate 代码质量优化设计文档

日期：2026-03-11

## 背景

SREmate 项目经过多轮迭代，代码存在以下质量问题：

1. **工具类职责过多**：ContractTool 有 415 行，混合了合同数据、报价单、子单等多个领域的查询逻辑
2. **重复代码模式**：每个工具方法都有相同的 `try-catch-log` 模式
3. **错误处理不一致**：ContractTool 返回 `{"error":"xxx"}`，HttpEndpointTool 返回 `"错误：xxx"`
4. **ObjectMapper 重复创建**：HttpEndpointTool 自己创建 ObjectMapper，而其他类注入 Spring 管理的
5. **DATA_QUERY_TOOLS 维护困难**：新增工具时需要手动更新白名单

## 目标

基于 DDD 和业务语义编程思想，实现：

1. 按业务领域拆分工具类，职责单一
2. 提取工具执行模板，消除重复代码
3. 统一错误处理格式
4. 使用注解识别数据查询工具，降低维护成本

---

## 设计方案

### 一、新增基础设施组件

#### 1. ToolExecutionTemplate（工具执行模板）

位置：`infrastructure/service/ToolExecutionTemplate.java`

```java
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
     * @return 工具执行结果（JSON 格式）
     */
    public static String execute(String toolName, ToolAction action) {
        long start = System.currentTimeMillis();
        try {
            String result = action.execute();
            log.info("[TOOL] {} → {}ms, ok", toolName, System.currentTimeMillis() - start);
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

#### 2. ToolResult（统一结果类）

位置：`infrastructure/service/ToolResult.java`

```java
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

#### 3. @DataQueryTool（数据查询工具注解）

位置：`infrastructure/annotation/DataQueryTool.java`

```java
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

---

### 二、工具类拆分

#### 拆分方案

```
trigger/agent/
├── ContractQueryTool.java    # 合同数据查询（5个方法）
├── BudgetBillTool.java       # 报价单查询（1个方法）
├── SubOrderTool.java         # 子单查询（1个方法）
├── HttpEndpointTool.java     # HTTP 接口调用（保持，优化错误处理）
├── SkillQueryTool.java       # 技能查询（保持）
└── KnowledgeQueryTool.java   # 知识库查询（保持）
```

#### ContractQueryTool（合同数据查询）

职责：合同基础数据、实例ID、版式、配置表的查询

| 方法 | 功能 | @DataQueryTool |
|------|------|----------------|
| `queryContractData` | 根据合同编号查询合同数据 | ✓ |
| `queryContractsByOrderId` | 根据订单号查询合同列表 | ✓ |
| `queryContractInstanceId` | 查询 platform_instance_id | ✓ |
| `queryContractFormId` | 查询版式 form_id | ✓ |
| `queryContractConfig` | 查询合同配置表 | ✓ |

```java
@Slf4j
@Component
public class ContractQueryTool {

    private final ContractQueryService contractQueryService;
    private final ObjectMapper objectMapper;

    public ContractQueryTool(ContractQueryService contractQueryService,
                             ObjectMapper objectMapper) {
        this.contractQueryService = contractQueryService;
        this.objectMapper = objectMapper;
    }

    @Tool(description = "【合同编号查询】用户输入包含C前缀编号时使用...")
    @DataQueryTool
    public String queryContractData(String contractCode, String dataType) {
        return ToolExecutionTemplate.execute("queryContractData", () -> {
            // 防御性校验：纯数字说明是订单号，LLM 调错了工具
            if (contractCode != null && contractCode.matches("\\d+")) {
                return ToolResult.error("参数错误：" + contractCode + " 是订单号，请使用 queryContractsByOrderId");
            }
            QueryDataType type = QueryDataType.valueOf(dataType.toUpperCase());
            Map<String, Object> result = contractQueryService.queryByCode(contractCode, type);
            if (result == null) {
                return ToolResult.notFound("合同", contractCode);
            }
            return contractQueryService.toJson(result);
        });
    }

    // 其他方法类似...
}
```

#### BudgetBillTool（报价单查询）

职责：报价单列表及其子单聚合查询

| 方法 | 功能 | @DataQueryTool |
|------|------|----------------|
| `queryBudgetBillList` | 查询报价单列表（含子单聚合） | ✓ |

```java
@Slf4j
@Component
public class BudgetBillTool {

    private final HttpEndpointTool httpEndpointTool;
    private final ObjectMapper objectMapper;

    @Tool(description = "【报价单查询】用户提到"报价单"或"报价"时使用...")
    @DataQueryTool
    public String queryBudgetBillList(String projectOrderId) {
        return ToolExecutionTemplate.execute("queryBudgetBillList", () -> {
            // 业务逻辑...
        });
    }

    private ArrayNode querySubOrdersForBill(String projectOrderId, String billCode) {
        // 私有辅助方法...
    }
}
```

#### SubOrderTool（子单查询）

职责：子单基本信息查询

| 方法 | 功能 | @DataQueryTool |
|------|------|----------------|
| `querySubOrderInfo` | 查询子单基本信息 | ✓ |

```java
@Slf4j
@Component
public class SubOrderTool {

    private final HttpEndpointTool httpEndpointTool;

    @Tool(description = "【子单查询】用户提到"子单"或"S单"时使用...")
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

---

### 三、HttpEndpointTool 优化

#### 问题

1. 自己创建 `new ObjectMapper()`，应注入 Spring 管理的
2. 错误处理返回字符串格式，与统一格式不一致

#### 改造

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
                return ToolResult.error("未找到接口模板: " + endpointId);
            }
            // 业务逻辑...
        });
    }
}
```

---

### 四、ObservabilityAspect 改造

#### 当前实现

使用 `DATA_QUERY_TOOLS` 白名单判断是否为数据查询工具：

```java
private static final Set<String> DATA_QUERY_TOOLS = Set.of(
    "queryContractData", "queryContractsByOrderId", ...
);

if (DATA_QUERY_TOOLS.contains(toolName) && result != null) {
    DirectOutputHolder.set(result.toString());
}
```

#### 改造后

使用 `@DataQueryTool` 注解自动识别：

```java
@Around("@annotation(tool)")
public Object logToolCall(ProceedingJoinPoint joinPoint, Tool tool) throws Throwable {
    String toolName = joinPoint.getSignature().getName();
    // ...

    try {
        Object result = joinPoint.proceed();
        // ...

        // 通过反射检查方法是否有 @DataQueryTool 注解
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        boolean isDataQuery = method.isAnnotationPresent(DataQueryTool.class);
        if (isDataQuery && result != null) {
            DirectOutputHolder.set(result.toString());
        }

        return result;
    } catch (Exception e) {
        // ...
    }
}
```

**优点：**
- 新增工具时只需加 `@DataQueryTool` 注解，无需修改 Aspect
- 业务语义更清晰：一看方法就知道是数据查询工具

---

## 文件变更清单

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `infrastructure/annotation/DataQueryTool.java` | 新增 | 数据查询工具注解 |
| `infrastructure/service/ToolExecutionTemplate.java` | 新增 | 工具执行模板 |
| `infrastructure/service/ToolResult.java` | 新增 | 统一结果类 |
| `trigger/agent/ContractQueryTool.java` | 新增 | 合同查询工具 |
| `trigger/agent/BudgetBillTool.java` | 新增 | 报价单查询工具 |
| `trigger/agent/SubOrderTool.java` | 新增 | 子单查询工具 |
| `trigger/agent/ContractTool.java` | 删除 | 已拆分 |
| `trigger/agent/HttpEndpointTool.java` | 修改 | 注入 ObjectMapper，统一错误格式 |
| `aspect/ObservabilityAspect.java` | 修改 | 使用注解识别数据查询工具 |

---

## 实施计划

### 阶段一：基础设施层改造

1. 新增 `@DataQueryTool` 注解
2. 新增 `ToolExecutionTemplate` 工具执行模板
3. 新增 `ToolResult` 统一结果类

### 阶段二：工具类拆分

1. 创建 `ContractQueryTool`（从 ContractTool 提取合同查询方法）
2. 创建 `BudgetBillTool`（从 ContractTool 提取报价单查询方法）
3. 创建 `SubOrderTool`（从 ContractTool 提取子单查询方法）
4. 删除原 `ContractTool`

### 阶段三：统一错误处理

1. 改造 `HttpEndpointTool` 使用 `ToolResult`
2. 更新 `ObservabilityAspect` 使用 `@DataQueryTool` 注解

### 阶段四：验证

1. 运行全部集成测试验证功能正常
2. 手动测试验证错误处理统一

---

## 测试策略

### 单元测试

- `ToolExecutionTemplateTest`：验证模板的计时、日志、异常处理
- `ToolResultTest`：验证 JSON 序列化正确性

### 集成测试

现有集成测试应全部通过，无需修改测试逻辑（方法名不变）：
- `ContractDataQueryIT`
- `ContractConfigQueryIT`
- `ContractFormQueryIT`
- `ContractListQueryIT`

---

## 风险与缓解

| 风险 | 缓解措施 |
|------|----------|
| 拆分后工具类数量增加 | 粒度适中，仅拆分为 3 个类，不会过度拆分 |
| 错误格式变更影响调用方 | 检查调用链，确认无硬编码依赖 |
| ObservabilityAspect 改造引入 bug | 编写单元测试验证注解识别逻辑 |
