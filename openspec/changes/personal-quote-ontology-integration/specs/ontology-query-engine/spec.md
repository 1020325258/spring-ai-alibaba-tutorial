## ADDED Requirements

### Requirement: 多跳路径查询支持
系统 SHALL 支持三跳及以上的路径查询，每跳自动使用 `queryByFieldWithContext` 从父记录提取上下文参数。

#### Scenario: 三跳路径查询 PersonalQuote
- **WHEN** 查询路径为 Order → Contract → ContractQuotationRelation → PersonalQuote
- **THEN** 引擎在第三跳调用 PersonalQuoteGateway.queryByFieldWithContext，传入 ContractQuotationRelation 记录作为 parentRecord

#### Scenario: 多跳路径中每跳独立处理
- **WHEN** 路径有多跳，每跳的 Gateway 需要从父记录提取不同参数
- **THEN** 每跳的 `queryByFieldWithContext` 实现独立决定从 parentRecord 提取哪些字段
