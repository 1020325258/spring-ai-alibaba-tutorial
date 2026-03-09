# SREmate 重构 & 可观测性重设计文档

日期：2026-03-09

## 背景

代码审查发现两类问题：
1. 代码存在 DDD 分层越界、死代码堆积、责任混乱等问题，对研发人员阅读和维护造成困扰
2. 工具调用的可观测信息冗余（每次调用打 4-5 行重复日志）且关键信息缺失（命中了哪张表、查出几条、向量检索是否命中、HTTP 响应状态等）

---

## 目标

1. **代码重构**：让代码结构符合 DDD 分层规范，删除无效代码，消除设计噪音
2. **可观测性重设计**：每次工具调用输出一行结构化日志 + 控制台内联提示，信息聚焦、不冗余

---

## 任务一：删除死代码

### 待删除内容

| 类/方法 | 位置 | 原因 |
|---|---|---|
| `ParallelExecutor` | `infrastructure/service/` | `executeSingleTool` 只返回硬编码 stub，无实际工具调用，无生产代码使用 |
| `ResultAggregator` | `infrastructure/service/` | 只消费 `ParallelExecutor` 的 stub 结果，无生产代码使用 |
| `AggregatedResult` | `infrastructure/service/model/` | 只被 `ResultAggregator` 使用，随之删除 |
| `ToolExecutionRequest` | `infrastructure/service/model/` | 只被 `ParallelExecutor` 使用，随之删除 |
| `TracingService.startSession` | `infrastructure/service/` | 从未被任何生产代码调用 |
| `TracingService.endSession` | `infrastructure/service/` | 同上 |
| `TracingService.currentContext` (ThreadLocal) | `infrastructure/service/` | 被设置但从未被读取；且在 `Throwable` 场景下存在 ThreadLocal 泄漏 |
| `TraceSession.toolCalls` 字段及 `addToolCall` | `infrastructure/service/model/` | `startSession` 删除后，`toolCalls` 永远为空；`visualizeTraceChain` 随之简化 |
| `ContractQueryService.queryListByOrderId` | `domain/contract/service/` | 无任何 `@Tool` 方法调用它 |
| `ContractTool` 孤立 Javadoc 块（第 61-65 行） | `trigger/agent/` | 对应方法已删除，注释残留 |
| `MetricsCollector.getToolMetrics(String)` | `infrastructure/service/` | 无任何调用方 |
| `SREConsole` 中对 `currentSession`/`TraceSession` 的引用 | `trigger/console/` | session 管理移除后同步清理 |

---

## 任务二：代码质量重构

### 2.1 修复 DDD 分层越界：`ContractQueryService` → `HttpEndpointTool`

**问题**：`ContractQueryService`（domain 层）直接注入 `HttpEndpointTool`（trigger 层），domain → trigger 的反向依赖。

**方案**：在 domain 层定义端口接口，由 trigger 层提供适配实现。

```
domain/contract/gateway/FormDataGateway.java     ← 新增接口（domain 层定义契约）
trigger/agent/FormDataGatewayImpl.java           ← 新增实现（trigger 层适配）
```

`FormDataGateway` 接口：
```java
package com.yycome.sremate.domain.contract.gateway;

public interface FormDataGateway {
    /**
     * 根据 platform_instance_id 查询版式表单数据
     * @return 接口原始响应 JSON 字符串
     */
    String queryFormData(String instanceId);
}
```

`ContractQueryService` 改为注入 `FormDataGateway`，`FormDataGatewayImpl` 注入 `HttpEndpointTool` 并实现接口。

### 2.2 修复 `toErrorJson` 的 JSON 转义安全问题

**问题**：`ContractTool.toErrorJson` 手拼 JSON，只转义双引号，换行符、反斜杠、控制字符不处理，会产生非法 JSON。

**方案**：`ContractTool` 注入 `ObjectMapper`，统一用 `objectMapper.writeValueAsString(Map.of("error", message))` 生成 JSON。

### 2.3 修复 `ContractQueryService` 非托管线程池

**问题**：`Executors.newFixedThreadPool(5)` 在字段初始化处创建，Spring 无法管理生命周期，应用关闭或测试重启上下文时线程泄漏。

**方案**：注册为 `@Bean`，在 `AgentConfiguration` 中声明，或让 `ContractQueryService` 实现 `DisposableBean` 并在 `destroy()` 中调用 `executor.shutdown()`。

### 2.4 消除 `queryByOrderId` 与已删除 `queryListByOrderId` 的残余重复

删除 `queryListByOrderId` 后，`queryByOrderId` 中构建基础 `LinkedHashMap` 的逻辑可提取为私有辅助方法 `buildContractBaseItem(Map<String, Object> contract)`，提升可读性。

### 2.5 修复 `ContractDao` 的 `SELECT *`

`fetchQuotations` 改为显式列名：
```sql
SELECT contract_code, quotation_order_id, del_status, ctime, utime
FROM contract_quotation_relation
WHERE contract_code = ? AND del_status = 0
```

### 2.6 统一 ctime 格式化位置

`fetchContractsByOrderId` 的结果由 service 层调用 `DateTimeUtil.format()`，而其他 DAO 方法在 DAO 层内格式化。统一到 DAO 层内处理，service 层不再承担格式化责任。

---

## 任务三：可观测性重设计

### 3.1 新增 `ToolCallContext`（轻量信息收集容器）

**位置**：`infrastructure/service/ToolCallContext.java`

每次工具调用期间，通过 `ThreadLocal` 在同线程内共享调用细节，由各执行层（DAO、HTTP 客户端、知识库）主动写入，`ObservabilityAspect` 在后置处理中读取并格式化输出。

```java
public class ToolCallContext {
    // DB 查询信息：表名 → 返回行数
    private final Map<String, Integer> dbQueries = new LinkedHashMap<>();
    // HTTP 调用信息：endpointId → HTTP 状态码
    private final Map<String, Integer> httpCalls = new LinkedHashMap<>();
    // 向量检索信息：是否命中、命中文档摘要列表
    private boolean vectorSearchExecuted = false;
    private int vectorHits = 0;
    private final List<String> hitDocSummaries = new ArrayList<>();

    // ThreadLocal 管理
    private static final ThreadLocal<ToolCallContext> CURRENT = new ThreadLocal<>();
    public static ToolCallContext start() { ... }
    public static ToolCallContext get() { ... }
    public static void clear() { ... }
}
```

### 3.2 各执行层写入 ToolCallContext

**ContractDao**：每个查询方法执行后记录表名和行数：
```java
// 示例：fetchNodes 方法末尾
List<Map<String, Object>> result = jdbcTemplate.queryForList(...);
ToolCallContext.record("contract_node", result.size());
return result;
```

**EndpointTemplateService**：HTTP 调用完成后记录 endpointId 和状态码：
```java
ToolCallContext.recordHttp(endpointId, response.statusCode().value());
```

**KnowledgeService**：向量检索完成后记录命中情况：
```java
ToolCallContext.recordVectorSearch(results.size(), summaries);
```

### 3.3 重构 `ObservabilityAspect`

**职责简化**：
- 在工具调用前调用 `ToolCallContext.start()` 初始化上下文
- 在工具调用后从 `ToolCallContext.get()` 读取执行细节
- 输出一行结构化日志（INFO 级别）
- 在控制台打印内联提示行（通过 `System.out`）
- 调用 `ToolCallContext.clear()` 清理（`finally` 块确保执行）
- 仍调用 `MetricsCollector.recordToolCall` 记录性能指标
- 仍调用 `TracingService`（简化后版本）记录追踪链

**日志格式**（结构化单行）：
```
[TOOL] {toolName} | {param1=v1 param2=v2} | {duration}ms {status} | {detail}
```

示例：
```
[TOOL] queryContractData    | contractCode=C1772854666284956 dataType=ALL    | 234ms ✓ | tables=[contract(1),contract_node(3),contract_user(2),contract_field_sharding_6(20)]
[TOOL] querySubOrderInfo    | homeOrderNo=826... quotationOrderNo=GBILL...    | 187ms ✓ | endpoint=sub-order-info status=200
[TOOL] searchKnowledge      | query="数据库连接超时" topK=3                  |  89ms ✓ | vector hits=2 ["troubleshooting/database#连接池配置","faq/contract#..."]
[TOOL] queryContractData    | contractCode=C000000 dataType=ALL              |  45ms ✗ | error=未找到合同记录
```

**控制台内联提示**（灰色，工具结果输出前打印）：
```
  ⓘ queryContractData · contract(1) contract_node(3) contract_user(2) contract_field_sharding_6(20) · 234ms
```

**移除的冗余日志**：
- `ObservabilityAspect` 中原有的两条 `log.info("[TOOL_CALL] ...")` 替换为上述单行格式
- `TracingService` 中的 `log.info("[TRACE] ...")` 全部删除（追踪信息仍写入内存，但不打日志）

**使用 MethodSignature 获取参数名**：
```java
MethodSignature sig = (MethodSignature) joinPoint.getSignature();
String[] paramNames = sig.getParameterNames();
Object[] args = joinPoint.getArgs();
// 生成 "contractCode=C123 dataType=ALL" 格式
```

**异常处理升级**：`catch (Exception e)` 改为 `catch (Throwable t)`，确保 `ToolCallContext.clear()` 在任何情况下都执行（ThreadLocal 不泄漏）。

### 3.4 简化后的 `TracingService`

删除 session 管理和 ThreadLocal 后，`TracingService` 仅保留：
- `startToolCall(toolName, params)` → 创建 `TracingContext`
- `endToolCall(context, result)` → 更新 context 状态
- `failToolCall(context, e)` → 记录错误
- 内存中保留最近 N 条工具调用记录（供 `trace` 命令查看）

`SREConsole` 的 `trace` 命令改为展示最近若干条工具调用记录（不再依赖 session 概念）。

---

## 效果对比

### 日志 Before（每次工具调用 4-5 行，信息分散）
```
INFO  queryContractData - contractCode: C123, dataType: ALL
INFO  [TOOL_CALL] 开始调用工具: queryContractData, 参数: [C123, ALL]
INFO  [TRACE] 开始工具调用: tool=queryContractData, traceId=xxx, params={arg0=C123, arg1=ALL}
INFO  [TRACE] 工具调用成功: tool=queryContractData, traceId=xxx, duration=234ms
INFO  [TOOL_CALL] 工具调用成功: queryContractData, 耗时: 234ms
```

### 日志 After（1 行，信息完整）
```
INFO  [TOOL] queryContractData | contractCode=C123 dataType=ALL | 234ms ✓ | tables=[contract(1),contract_node(3),contract_user(2),contract_field_sharding_6(20)]
```

### 控制台 Before
```
SRE助手:
{"contractCode":"C123",...}
```

### 控制台 After
```
SRE助手:
  ⓘ queryContractData · contract(1) contract_node(3) contract_user(2) contract_field_sharding_6(20) · 234ms
{"contractCode":"C123",...}
```

---

## 改动范围汇总

| 操作 | 文件 |
|---|---|
| **删除** | `ParallelExecutor.java` |
| **删除** | `ResultAggregator.java` |
| **删除** | `AggregatedResult.java` |
| **删除** | `ToolExecutionRequest.java` |
| **新增** | `domain/contract/gateway/FormDataGateway.java` |
| **新增** | `trigger/agent/FormDataGatewayImpl.java` |
| **新增** | `infrastructure/service/ToolCallContext.java` |
| **修改** | `ContractTool.java`（注入 ObjectMapper，修复 toErrorJson，清理孤立注释） |
| **修改** | `ContractQueryService.java`（FormDataGateway 替换 HttpEndpointTool，executor 生命周期，删除死方法，提取 buildContractBaseItem） |
| **修改** | `ContractDao.java`（fetchQuotations 改显式列名，fetchContractsByOrderId 内格式化 ctime，增加 ToolCallContext.record 调用） |
| **修改** | `ObservabilityAspect.java`（ToolCallContext 生命周期，结构化单行日志，MethodSignature 参数名，catch Throwable） |
| **修改** | `TracingService.java`（删除 session 管理、ThreadLocal，删除日志打印，保留追踪链内存存储） |
| **修改** | `MetricsCollector.java`（删除 getToolMetrics(String) 死方法） |
| **修改** | `EndpointTemplateService.java`（增加 ToolCallContext.recordHttp 调用） |
| **修改** | `KnowledgeService.java`（增加 ToolCallContext.recordVectorSearch 调用，条件编译保护） |
| **修改** | `SREConsole.java`（trace 命令改用简化后的 TracingService，控制台内联提示输出） |
| **修改** | `AgentConfiguration.java`（executor 注册为 Bean） |
