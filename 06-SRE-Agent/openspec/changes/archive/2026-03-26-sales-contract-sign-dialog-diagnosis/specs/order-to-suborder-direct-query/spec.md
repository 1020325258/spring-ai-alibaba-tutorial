## ADDED Requirements

### Requirement: SubOrder 支持 homeOrderNo 直查
系统 SHALL 在 `domain-ontology.yaml` 中为 `SubOrder` 实体新增 `homeOrderNo` lookupStrategy，并新增 `Order → SubOrder` 关系，支持从订单号直接查询 S 单列表。

#### Scenario: 实体关系注册成功
- **WHEN** 系统启动
- **THEN** EntityRegistry 存在 `Order → SubOrder` 的关系路径，`SubOrder` 的 lookupStrategies 包含 `homeOrderNo` 字段

### Requirement: SubOrderGateway 支持 homeOrderNo 参数查询
`SubOrderGateway`（或对应实现）SHALL 当 `fieldName=homeOrderNo` 时，调用 `sub-order-info` 端点，仅传 `homeOrderNo` 参数，返回订单下所有 S 单。

#### Scenario: 按订单号查询 S 单列表
- **WHEN** 调用 `ontologyQuery(entity=Order, value=订单号, queryScope=SubOrder)`
- **THEN** 返回该订单下所有 S 单记录，每条记录包含状态字段

#### Scenario: 订单下无 S 单
- **WHEN** 调用 `ontologyQuery(entity=Order, value=订单号, queryScope=SubOrder)`，且订单下没有 S 单
- **THEN** 返回空列表
