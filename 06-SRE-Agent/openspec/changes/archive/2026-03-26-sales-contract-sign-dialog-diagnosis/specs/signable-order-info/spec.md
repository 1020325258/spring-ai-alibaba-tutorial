## ADDED Requirements

### Requirement: SignableOrderInfo 实体定义
系统 SHALL 在 `domain-ontology.yaml` 中注册 `SignableOrderInfo` 实体，并定义 `Contract → SignableOrderInfo` 关系。

#### Scenario: 实体加载成功
- **WHEN** 系统启动
- **THEN** EntityRegistry 中包含 `SignableOrderInfo` 实体，且存在 `Contract → SignableOrderInfo` 的关系路径

### Requirement: Gateway 根据合同类型路由接口
`SignableOrderInfoGateway` SHALL 实现 `queryByFieldWithContext`，从父 `Contract` 记录中读取 `type` 字段，路由到对应的弹窗接口：type=8（销售合同）→ `sign-order-list` 端点，type=3（正签合同）→ 对应端点。

#### Scenario: 销售合同（type=8）查询弹窗 S 单
- **WHEN** 父合同记录 `type=8`，调用 `queryByFieldWithContext`
- **THEN** Gateway 调用 `sign-order-list` 端点，入参为父合同的 `projectOrderId`，返回弹窗 S 单列表

#### Scenario: 正签合同（type=3）查询弹窗 S 单
- **WHEN** 父合同记录 `type=3`，调用 `queryByFieldWithContext`
- **THEN** Gateway 调用正签合同对应端点，入参为父合同的 `projectOrderId`，返回弹窗 S 单列表

#### Scenario: 未知合同类型返回空列表
- **WHEN** 父合同记录 `type` 不在已知路由范围内
- **THEN** Gateway 返回空列表，并记录 warn 日志

### Requirement: 多跳遍历自动传递父记录
从 `Order` 起始的多跳查询 SHALL 能到达 `SignableOrderInfo`，且引擎自动将 `Contract` 记录作为 parentRecord 传入 Gateway。

#### Scenario: ontologyQuery 多跳遍历
- **WHEN** 调用 `ontologyQuery(entity=Order, value=订单号, queryScope=SignableOrderInfo)`
- **THEN** 引擎沿 Order → Contract → SignableOrderInfo 路径遍历，Gateway 收到完整的 Contract parentRecord

### Requirement: ontology.html 正确展示新实体和关系
`ontology.html` SHALL 在页面中可视化展示 `SignableOrderInfo` 节点及 `Contract → SignableOrderInfo` 关系边，以及 `Order → SubOrder` 直查关系边。

#### Scenario: UI 展示新实体
- **WHEN** 访问 `ontology.html` 页面
- **THEN** Playwright 截图中可见 `SignableOrderInfo` 节点和指向它的关系边

#### Scenario: UI 展示 Order 到 SubOrder 直查关系
- **WHEN** 访问 `ontology.html` 页面
- **THEN** Playwright 截图中可见 `Order → SubOrder` 的关系边
