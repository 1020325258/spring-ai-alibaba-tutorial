## MODIFIED Requirements

### Requirement: queryByFieldWithContext 上下文查询
部分 Gateway SHALL 实现 `queryByFieldWithContext(fieldName, value, parentRecord)` 方法，允许从父记录中取额外参数（如 SubOrder 需要父记录的 `billCode`，PersonalQuote 需要父记录的 `billCode` 和 `bindType`）。

#### Scenario: SubOrder 双参数查询
- **WHEN** 引擎查询 SubOrder，父记录为 BudgetBill（含 billCode）
- **THEN** Gateway 使用 `quotationOrderNo`（来自关系配置）+ `homeOrderNo`（来自父记录 billCode）双参数查询

#### Scenario: PersonalQuote 从签约单据提取参数
- **WHEN** 引擎查询 PersonalQuote，父记录为 ContractQuotationRelation（含 billCode 和 bindType）
- **THEN** Gateway 根据 bindType 值将 billCode 映射到正确的参数：
  - bindType=1 → billCodeList
  - bindType=2 → subOrderNoList
  - bindType=3 → changeOrderId

#### Scenario: PersonalQuote 无效 bindType
- **WHEN** 引擎查询 PersonalQuote，父记录的 bindType 不在 1/2/3 范围
- **THEN** Gateway 返回空列表并记录 warn 日志

---

## REMOVED Requirements

### Requirement: queryWithExtraParams 额外参数查询

**Reason**: PersonalQuery 现在通过 `queryByFieldWithContext` 从父记录自动获取参数，不再需要 LLM 手动传入 extraParams。

**Migration**: `PersonalQuoteGateway.queryWithExtraParams` 方法保留但不再被引擎调用，仅作为独立查询入口（如直接查询 PersonalQuote）。
