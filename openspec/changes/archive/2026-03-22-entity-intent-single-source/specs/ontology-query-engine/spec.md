## MODIFIED Requirements

### Requirement: 实体参数校验
OntologyQueryTool SHALL 仅校验 entity 参数的合法性，不再根据 value 格式推断 entity。LLM 的判断 SHALL 被信任。

#### Scenario: 校验 entity 合法性
- **WHEN** LLM 传入 entity 参数
- **THEN** 校验该 entity 在 EntityRegistry 中存在，不存在则返回错误

#### Scenario: 信任 LLM 判断不覆盖
- **WHEN** LLM 传入 entity=ContractInstance，value="101835395"（纯数字）
- **THEN** 直接使用 LLM 判断的 entity，不被代码推断为 Order

#### Scenario: 纯数字 instanceId 正确识别
- **WHEN** 用户输入 "101835395的实例信息"
- **THEN** LLM 识别 entity=ContractInstance，value="101835395"，查询成功

---

## ADDED Requirements

### Requirement: 支持任意实体作为查询入口
用户 SHALL 能够直接查询任意在 YAML 中定义了 lookupStrategies 的实体，不限于 Order 或 Contract。

#### Scenario: 直接查询 ContractInstance
- **WHEN** 用户输入 "查 instanceId=101835395 的实例"
- **THEN** LLM 识别 entity=ContractInstance，正确返回版式数据

#### Scenario: 直接查询 ContractNode
- **WHEN** 用户输入 "查合同号 C1767150648920281 的节点"
- **THEN** LLM 识别 entity=Contract, queryScope=ContractNode
