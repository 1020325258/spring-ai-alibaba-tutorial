## Why

PersonalQuote（个性化报价）数据当前直接挂在 Order 下，需要用户手动提供 billCodeList/subOrderNoList/changeOrderId 参数。但实际上这些参数来源于 ContractQuotationRelation（签约单据）的 billCode 字段——系统应该能自动从签约单据获取这些参数，而非要求用户手动提供。

这限制了用户查询体验：用户想知道"订单 826xxx 的个性化报价数据"时，系统无法自动检索，需要用户额外提供单据号。

## What Changes

1. **新增关系**：`ContractQuotationRelation → PersonalQuote`，通过 billCode 关联
2. **修改关系**：移除 `Order → PersonalQuote` 直接关系（改为通过 Contract → ContractQuotationRelation → PersonalQuote 三跳访问）
3. **实现 bindType 语义映射**：
   - `bindType=1`：billCode 表示报价单 → 对应 `billCodeList` 参数
   - `bindType=2`：billCode 表示 S单号 → 对应 `subOrderNoList` 参数
   - `bindType=3`：billCode 表示变更单号 → 对应 `changeOrderId` 参数
4. **修改 PersonalQuoteGateway**：支持从父记录（ContractQuotationRelation）自动提取 billCode 和 bindType

## Capabilities

### New Capabilities

- `personal-quote-relation`: PersonalQuote 通过 ContractQuotationRelation 关联的能力，实现 bindType 语义映射

### Modified Capabilities

- `entity-gateway`: 需扩展 `queryByFieldWithContext` 场景，支持 PersonalQuote 从父记录提取 billCode 和 bindType
- `ontology-query-engine`: 支持三跳路径查询（Order → Contract → ContractQuotationRelation → PersonalQuote）

## Impact

- **domain-ontology.yaml**：移除 `Order → PersonalQuote` 关系，新增 `ContractQuotationRelation → PersonalQuote` 关系
- **PersonalQuoteGateway**：重构 `queryByFieldWithContext` 方法，从父记录提取参数
- **prompts/sre-agent.md**：更新 PersonalQuote 的查询路径说明和场景表
- **集成测试**：新增 PersonalQuote 三跳查询测试用例
