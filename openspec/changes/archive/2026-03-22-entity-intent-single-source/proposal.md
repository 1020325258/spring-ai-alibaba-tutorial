## Why

当前 SREmate 存在实体意图识别的维护分散问题：实体别名定义在 `domain-ontology.yaml`，但决策规则又硬编码在 `sre-agent.md` 和 `OntologyQueryTool.inferEntityFromValue()` 中。这种三处重复维护导致：

1. **灵活性丧失**：`inferEntityFromValue()` 根据 value 格式硬编码推断 entity（纯数字→Order，C开头→Contract），覆盖了 LLM 的判断，导致用户直接输入 `instanceId` 查询版式时被错误识别为订单号
2. **维护成本高**：新增实体需要同步修改 YAML、提示词、Java 代码三处
3. **信息不一致风险**：提示词中的决策表与 YAML 定义容易脱节

## What Changes

- **BREAKING**: 实体重命名 `ContractForm` → `ContractInstance`，别名扩展为 `["实例", "实例信息", "版式", "版式数据", "form_id", "版式ID", "instanceId"]`
- `EntityRegistry` 新增 `getEntitySummaryForPrompt()` 方法，自动生成实体摘要（含别名和查询入口）
- `sre-agent.md` 精简决策规则，移除硬编码表格，注入 `{{entity_summary}}`，保留少量样例
- `OntologyQueryTool.inferEntityFromValue()` 改为仅校验 entity 合法性，不再覆盖 LLM 判断
- `ContractFormGateway` 重命名为 `ContractInstanceGateway`
- 更新所有相关代码和测试的引用

## Capabilities

### New Capabilities

- `entity-summary-generation`: EntityRegistry 自动生成实体摘要供 LLM 提示词使用，实现"单一数据源"

### Modified Capabilities

- `ontology-query-engine`: 实体识别逻辑从"代码硬编码"改为"信任 LLM 判断 + 代码校验"
- `entity-gateway`: ContractForm 重命名为 ContractInstance，更新别名定义

## Impact

**代码变更**：
- `domain-ontology.yaml`: 实体重命名 + 别名扩展
- `EntityRegistry.java`: 新增方法
- `AgentConfiguration.java`: 注入 entity_summary
- `sre-agent.md`: 精简决策规则
- `OntologyQueryTool.java`: 移除格式推断逻辑
- `ContractFormGateway.java` → `ContractInstanceGateway.java`
- 相关测试类同步更新

**向后兼容**：
- API 层面：工具参数不变，只是 entity 取值范围扩展
- 数据层面：无影响
