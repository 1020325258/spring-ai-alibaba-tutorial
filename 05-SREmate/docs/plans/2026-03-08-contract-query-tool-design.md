# 合同查询工具设计方案

## 背景

SREmate 需要支持 4 种合同数据查询场景，当前存在意图识别错误问题（输入合同编号时误调用订单工具）。本次重构将 4 种场景统一为一个工具，通过 `dataType` 参数驱动查询范围。

## 目标

1. 修复意图识别问题：`C` 前缀编号正确路由到合同查询工具
2. 4 种查询场景用一个 `@Tool` 覆盖，避免代码重复
3. 关联表并行查询，保证速度
4. 集成测试验证 LLM 意图识别准确性

## 工具设计

### 工具签名

```java
@Tool(description = "...")
public String queryContractData(String contractCode, String dataType)
```

### DataType 枚举

```java
public enum QueryDataType {
    ALL,            // 全量：contract + contract_node + contract_user + contract_quotation_relation + contract_field_sharding
    CONTRACT_NODE,  // 节点日志：contract + contract_node + contract_log
    CONTRACT_FIELD, // 字段数据：contract + contract_field_sharding_N（分表）
    CONTRACT_USER   // 签约人：contract + contract_user
}
```

### 意图→dataType 映射

| 用户输入关键词 | dataType |
|---|---|
| 合同数据、合同详情、合同信息 | `ALL` |
| 合同节点、节点数据、操作日志、合同日志 | `CONTRACT_NODE` |
| 合同字段、字段数据 | `CONTRACT_FIELD` |
| 签约人、合同用户、参与人 | `CONTRACT_USER` |

## 内部结构

### 私有 Helper 方法（每个对应一张表）

```
fetchContractBase(code)   → contract 主表
fetchNodes(code)          → contract_node
fetchLogs(code)           → contract_log
fetchUsers(code)          → contract_user
fetchFields(code)         → contract_field_sharding_N（分表路由）
fetchQuotations(code)     → contract_quotation_relation
```

### 并行查询组合

| dataType | 并行查询内容 |
|---|---|
| `ALL` | base + node + user + quotation + field |
| `CONTRACT_NODE` | base + node + log |
| `CONTRACT_FIELD` | base + field |
| `CONTRACT_USER` | base + user |

base 查询与关联表查询同时发起（分表名由 contractCode 直接计算，不依赖 base 结果）。

## 文件变更清单

| 文件 | 变更类型 | 说明 |
|---|---|---|
| `tools/QueryDataType.java` | 新增 | DataType 枚举 |
| `tools/MySQLQueryTool.java` | 修改 | 新增 `queryContractData` + 6 个 helper；删除上轮新增的 `queryContractByCode` |
| `prompts/sre-agent.md` | 修改 | 更新工具说明 + 4 条 few-shot 示例 |
| `ContractDataQueryIT.java` | 新增 | 4 种意图的集成测试 |

## 测试方案

集成测试 `ContractDataQueryIT.java`，4 个测试方法：

```
queryAllData_returnsFullAggregatedData()   → 验证含 contract_node / contract_user / contract_field_sharding
queryNodeData_returnsNodeAndLog()          → 验证含 contract_node / contract_log，不含 contract_user
queryFieldData_returnsFieldShardingData()  → 验证含 contract_field_sharding，不含 contract_node
queryUserData_returnsContractUsers()       → 验证含 contract_user，不含 contract_field_sharding
```

断言策略：
- 返回 JSON 包含预期字段
- 不包含 `"error"` 字段
- 各场景返回字段互不越界（反向验证不该有的字段不存在）
