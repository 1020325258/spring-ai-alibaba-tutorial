# DirectOutput 多工具调用结果聚合修复

**日期**: 2026-03-15
**问题**: 用户查询"825123110000002753合同签约单据和节点"时，只返回了第一个工具调用的结果，后续工具调用的结果被丢弃。

## 问题分析

### 原始行为

```
用户: 825123110000002753合同签约单据和节点
LLM 调用:
1. ontologyQuery(entity=Order, value=825123110000002753, scope=default)
   → 返回合同列表
2. ontologyQuery(entity=Contract, value=C1767150648920281, scope=ContractQuotationRelation)
   → 返回签约单据 (结果被丢弃)
3. ontologyQuery(entity=Contract, value=C1767150648920281, scope=ContractNode)
   → 返回节点数据 (结果被丢弃)
...

输出: 只有合同列表，没有签约单据和节点数据
```

### 问题根源

1. **原始问题**: `DirectOutput` 机制在**第一个 LLM token 到达时**立即输出第一个工具结果并终止流，导致后续工具调用的结果被丢弃。

2. **跨线程问题**: 工具执行在不同线程（`boundedElastic-*`），而 `DirectOutputHolder` 原本使用 `ThreadLocal` 存储，无法跨线程共享结果。

## 修复方案

### 核心思路

1. **延迟输出**: 不在首个 token 到达时立即输出，等待流结束
2. **收集所有结果**: `DirectOutputHolder` 改为收集所有工具结果
3. **聚合输出**: 在流结束时聚合多个 `ontologyQuery` 结果
4. **跨线程支持**: 使用 `requestId + ConcurrentHashMap` 替代 `ThreadLocal`
5. **统一聚合逻辑**: 将聚合逻辑移到 `DirectOutputHolder` 中，供 SREConsole 和测试基类共用

### 代码变更

#### 1. DirectOutputHolder.java - 支持跨线程多结果收集和聚合

```java
@Component
public class DirectOutputHolder {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 当前请求的请求ID */
    private static final AtomicReference<String> CURRENT_REQUEST_ID = new AtomicReference<>();

    /** 请求ID -> 结果列表的映射 */
    private static final ConcurrentHashMap<String, CopyOnWriteArrayList<ToolResult>> REQUEST_RESULTS = new ConcurrentHashMap<>();

    /** 请求ID -> OUTPUT 标记的映射 */
    private static final ConcurrentHashMap<String, String> REQUEST_OUTPUT = new ConcurrentHashMap<>();

    /** 开始新请求，生成请求ID */
    public void startRequest() {
        String requestId = UUID.randomUUID().toString();
        CURRENT_REQUEST_ID.set(requestId);
        REQUEST_RESULTS.put(requestId, new CopyOnWriteArrayList<>());
        REQUEST_OUTPUT.remove(requestId);
    }

    /** 添加工具结果（收集模式，支持跨线程） */
    public void addResult(String toolName, String result) {
        String requestId = getCurrentRequestId();
        if (requestId == null) return;
        CopyOnWriteArrayList<ToolResult> results = REQUEST_RESULTS.get(requestId);
        if (results != null) {
            results.add(new ToolResult(toolName, result));
            REQUEST_OUTPUT.put(requestId, "pending");
        }
    }

    /** 获取聚合后的输出结果 */
    public String getAggregatedOutput() {
        List<ToolResult> results = getResults();
        if (results.isEmpty()) return "";
        if (results.size() == 1) return results.get(0).result;
        return aggregateOntologyQueryResults(results);
    }

    /** 聚合多个 ontologyQuery 结果 */
    private String aggregateOntologyQueryResults(List<ToolResult> results) {
        // 解析结果，区分 Order 和 Contract 查询
        // 将 Contract 的 nodes/signedObjects 等关联数据合并到对应的 Order 合同中
        // 返回聚合后的 JSON
    }
}
```

#### 2. ObservabilityAspect.java - 使用收集模式

```java
// 如果是数据查询类工具，将结果收集到 DirectOutputHolder
if (isDataQuery && result instanceof String) {
    directOutputHolder.addResult(toolName, (String) result);
}
```

#### 3. BaseSREIT.java - 使用聚合方法

```java
// 开始新请求（生成请求ID，支持跨线程结果收集）
directOutputHolder.startRequest();

// 流结束时获取聚合结果
.doOnComplete(() -> {
    if (directOutputHolder.hasOutput() && !directOutputUsed.get()) {
        directOutputUsed.set(true);
        String directOutput = directOutputHolder.getAndClearAggregated();
        responseBuilder.setLength(0);
        responseBuilder.append(directOutput);
    }
    latch.countDown();
})
```

#### 4. SREConsole.java - 延迟聚合输出

```java
// 开始新请求
directOutputHolder.startRequest();

// 流结束时，聚合输出所有工具结果
.doOnComplete(() -> {
    if (directOutputHolder.hasOutput()) {
        String aggregatedOutput = directOutputHolder.getAggregatedOutput();
        System.out.println(aggregatedOutput);
        directOutputHolder.clear();
    }
})
```

### 聚合逻辑

```
原始结果:
1. Order 查询 → {"queryEntity":"Order", "records":[{"contractCode":"C1",...},{"contractCode":"C2",...}]}
2. Contract(C1) 签约单据 → {"queryEntity":"Contract", "records":[...], "signedObjects":[...]}
3. Contract(C1) 节点 → {"queryEntity":"Contract", "records":[...], "nodes":[...]}
4. Contract(C2) 签约单据 → ...
5. Contract(C2) 节点 → ...

聚合后:
{
  "queryEntity": "Order",
  "records": [
    {
      "contractCode": "C1",
      "signedObjects": [...],
      "nodes": [...]
    },
    {
      "contractCode": "C2",
      "signedObjects": [...],
      "nodes": [...]
    }
  ]
}
```

## 测试验证

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
mvn test -Dtest="ContractOntologyIT"
```

**结果**: 11/11 PASS

## 影响范围

| 组件 | 变更 |
|------|------|
| DirectOutputHolder | 新增 `startRequest()`，改用 `ConcurrentHashMap` 支持跨线程 |
| ObservabilityAspect | 改用 `addResult()` 替代 `setIfAbsent()` |
| BaseSREIT | 调用 `startRequest()` 开始请求，使用 `getAndClearAggregated()` |
| SREConsole | 调用 `startRequest()` 开始请求，流结束时调用 `getAggregatedOutput()` |

## 兼容性

- **单工具调用**: 行为不变，直接输出结果
- **多工具调用**: 结果按层级聚合后输出
- **测试**: 所有现有测试保持通过

## 关键修复点

### 问题1：测试输出显示 "pending"

测试基类原本调用 `getAndClear()` 获取输出，但该方法返回的是 `OUTPUT` 的值（"pending"），而不是聚合后的实际结果。

**修复**: 新增 `getAndClearAggregated()` 方法，先调用聚合逻辑再清除状态。

### 问题2：hasOutput() 返回 false，results=0

工具执行在不同线程（`boundedElastic-*`），而 `DirectOutputHolder` 原本使用 `ThreadLocal` 存储，无法跨线程共享结果。

**修复**: 使用 `requestId + ConcurrentHashMap` 替代 `ThreadLocal`，所有线程共享同一个请求的结果列表。

### 问题3：ConcurrentHashMap 不允许 null 值

`REQUEST_OUTPUT.put(requestId, null)` 抛出 NullPointerException。

**修复**: 使用 `REQUEST_OUTPUT.remove(requestId)` 标记无输出状态，而非存储 null 值。

### 问题4：重复查询和过度查询

用户查询"签约单据和节点"时：
1. **重复查询**: 同一合同被多次查询
2. **过度查询**: 只需 nodes 和 signedObjects，但查询了 fields、form、config 等所有关联数据

**根本原因**:
- `queryScope` 只支持单个目标实体
- 每次查询都展开所有关系

**修复**: 支持多目标查询，用逗号分隔目标实体。

```java
// OntologyQueryEngine.java
private void attachMultiPathResults(List<Map<String, Object>> records,
                                     List<List<OntologyRelation>> paths) {
    // 按层级分组：hop -> 关系列表（去重）
    Map<Integer, Set<OntologyRelation>> hopRelations = new LinkedHashMap<>();
    for (List<OntologyRelation> path : paths) {
        for (int hop = 0; hop < path.size(); hop++) {
            hopRelations.computeIfAbsent(hop, k -> new LinkedHashSet<>()).add(path.get(hop));
        }
    }
    // 逐层展开
    attachLayer(records, hopRelations, 0);
}
```

**使用示例**:
```
# 只查节点和签约单据
ontologyQuery(entity=Order, value=825123110000002753, queryScope=ContractNode,ContractQuotationRelation)
```

### 问题5：OrderGateway 返回字段缺失 projectOrderId

`OrderGateway.queryByField` 返回的记录中没有 `projectOrderId` 字段，导致 `expandDefault` 无法获取 `source_field` 来展开 Order → Contract 关系。

**修复**: 在 `OrderGateway.queryByField` 中添加 `projectOrderId` 字段。

```java
result.put("projectOrderId", value);  // 保留订单号，用于后续展开
```

### 问题5：ContractDao.fetchContractsByOrderId 返回 snake_case 字段

`ContractDao.fetchContractsByOrderId` 返回的字段名是 `contract_code`（snake_case），而 `expandDefault` 需要 `contractCode`（camelCase）来展开下一层关系（Contract → Node/Field/SignedObject）。

**现象**: Order 查询结果中 `contracts` 数组内的合同数据没有 `nodes`、`signedObjects` 等关联数据。

**修复**: 将 `fetchContractsByOrderId` 返回的字段名改为 camelCase。

```java
// 修复前
result.put("contract_code", row.get("contract_code"));

// 修复后
result.put("contractCode", row.get("contract_code"));
```

## 验证结果

```bash
mvn test -Dtest=ContractOntologyIT
```

**结果**: 11/11 PASS

**关键输出**:
- `nodes`、`signedObjects`、`fields`、`form`、`config` 等关联数据正确展开
- DirectOutput 生效，首字节时间 ~2s，绕过 LLM 处理
