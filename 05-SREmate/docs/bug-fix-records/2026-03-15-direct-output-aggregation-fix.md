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

`DirectOutput` 机制在**第一个 LLM token 到达时**立即输出第一个工具结果并终止流，导致后续工具调用的结果被丢弃。

```java
// 原始逻辑 (SREConsole.java)
.doOnNext(chunk -> {
    if (firstTokenMs[0] < 0 && directOutputHolder.hasOutput()) {
        // 首个 token 到达时立即输出并终止流
        String directOutput = directOutputHolder.getAndClear();
        System.out.println(directOutput);
        current.dispose();  // 终止流
        return;
    }
})
```

## 修复方案

### 核心思路

1. **延迟输出**: 不在首个 token 到达时立即输出，等待流结束
2. **收集所有结果**: `DirectOutputHolder` 改为收集所有工具结果
3. **聚合输出**: 在流结束时聚合多个 `ontologyQuery` 结果

### 代码变更

#### 1. DirectOutputHolder.java - 支持多结果收集

```java
@Component
public class DirectOutputHolder {
    private static final AtomicReference<String> OUTPUT = new AtomicReference<>();
    private static final ThreadLocal<List<ToolResult>> RESULTS = ThreadLocal.withInitial(ArrayList::new);

    public static class ToolResult {
        public final String toolName;
        public final String result;
        public final long timestamp;
    }

    /** 添加工具结果（收集模式） */
    public void addResult(String toolName, String result) {
        RESULTS.get().add(new ToolResult(toolName, result));
        OUTPUT.set("pending");
    }

    /** 获取所有收集的结果 */
    public List<ToolResult> getResults() {
        return new ArrayList<>(RESULTS.get());
    }

    /** 检查是否有多个工具结果需要聚合 */
    public boolean hasMultipleResults() {
        return RESULTS.get().size() > 1;
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

#### 3. SREConsole.java - 延迟聚合输出

```java
.doOnNext(chunk -> {
    // 不再在首个 token 到达时立即输出
    // 正常输出 LLM 内容
    if (!directOutputHolder.hasOutput()) {
        System.out.print(chunk);
        responseBuilder.append(chunk);
    }
})
.doOnComplete(() -> {
    // 流结束时，聚合输出所有工具结果
    if (directOutputHolder.hasOutput()) {
        String aggregatedOutput = aggregateToolResults(directOutputHolder);
        System.out.println(aggregatedOutput);
    }
})

/**
 * 聚合多个 ontologyQuery 结果
 * 场景：先查 Order 获取合同列表，再查每个合同的签约单据和节点
 */
private String aggregateToolResults(DirectOutputHolder holder) {
    List<ToolResult> results = holder.getResults();

    if (results.size() == 1) {
        return results.get(0).result;
    }

    // 多个结果：按层级合并
    return aggregateOntologyQueryResults(results);
}
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
| DirectOutputHolder | 新增多结果收集功能 |
| ObservabilityAspect | 改用 `addResult()` 替代 `setIfAbsent()` |
| SREConsole | 延迟输出，流结束时聚合 |

## 兼容性

- **单工具调用**: 行为不变，直接输出结果
- **多工具调用**: 结果按层级聚合后输出
- **测试**: 所有现有测试保持通过
