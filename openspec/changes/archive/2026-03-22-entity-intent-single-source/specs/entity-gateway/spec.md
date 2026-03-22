## MODIFIED Requirements

### Requirement: ContractInstance 实体定义
ContractForm SHALL 重命名为 ContractInstance，别名扩展以支持用户多种表达方式。

#### Scenario: 实体重命名
- **WHEN** 查询 ContractInstance 实体
- **THEN** 系统返回原 ContractForm 对应的版式数据

#### Scenario: 别名匹配
- **WHEN** 用户输入包含关键词 "实例"、"实例信息"、"版式"、"instanceId" 等
- **THEN** LLM 能通过别名匹配到 ContractInstance 实体

---

### Requirement: Gateway 重命名
ContractFormGateway SHALL 重命名为 ContractInstanceGateway，保持相同的查询逻辑。

#### Scenario: Gateway 自注册
- **WHEN** 应用启动
- **THEN** ContractInstanceGateway 通过 `registry.register(this)` 注册为 "ContractInstance"

#### Scenario: 查询逻辑不变
- **WHEN** 调用 `contractInstanceGateway.queryByField("instanceId", "101835395")`
- **THEN** 返回与原 ContractFormGateway 相同的版式数据
