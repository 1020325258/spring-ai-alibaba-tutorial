## ADDED Requirements

### Requirement: 从订单号直接查询弹窗可签约S单
系统 SHALL 支持以订单号为起点，通过 `ontologyQuery(entity=Order, queryScope=SignableOrderInfo)` 一跳查询弹窗可签约S单，无需经过合同中间层。

#### Scenario: 订单下有可签约S单
- **WHEN** 用户请求 `ontologyQuery(entity=Order, value={有效订单号}, queryScope=SignableOrderInfo)`
- **THEN** 系统调用 `sign-order-list` 接口（参数：`projectOrderId`），返回包含 `companyName`、`bindCode`、`goodsInfo`、`orderAmount`、`mustSelect` 等字段的 S 单列表

#### Scenario: 订单下无可签约S单
- **WHEN** 用户请求 `ontologyQuery(entity=Order, value={无S单的订单号}, queryScope=SignableOrderInfo)`
- **THEN** 系统返回空 `signableOrderInfos` 列表，工具调用状态为 ok

### Requirement: 响应解析正确处理公司分组结构
系统 SHALL 正确解析 `sign-order-list` 返回的两层结构：`data[]`（公司分组）→ `signableOrderInfos[]`（S单详情）。

#### Scenario: 多公司分组下的S单合并
- **WHEN** `sign-order-list` 返回多个公司分组，每组含若干 `signableOrderInfos`
- **THEN** 网关将所有分组的 S 单展平为一个列表，每条记录包含 `companyName`（来自分组）和 `bindCode`、`goodsInfo` 等（来自子项）

### Requirement: 实体别名覆盖"可签约S单"自然语言表达
`SignableOrderInfo` 实体 SHALL 包含别名"可签约S单"，使 LLM 能将用户的自然语言映射到正确的 queryScope。

#### Scenario: 用户说"可签约S单"时 LLM 使用正确 queryScope
- **WHEN** 用户提问"查询826031915000003212销售合同的可签约S单"
- **THEN** LLM 调用 `ontologyQuery` 时 `entity=Order`，`queryScope=SignableOrderInfo`
