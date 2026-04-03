## Why

SubOrderGateway 和 PersonalQuoteGateway 目前使用硬编码的 JSON 解析方法，而其他 HTTP 实体（BudgetBill、FormalSignableOrderInfo、PersonalSignableOrderInfo）已经统一使用 YAML source 配置驱动的 `EntitySchemaMapper` 解析。这种不一致导致：
1. 新增字段时需要修改 Java 代码，无法通过配置快速响应
2. 解析逻辑分散，维护成本高
3. 缺少一致性校验机制

## What Changes

- 为 `SubOrder` 实体添加 YAML source 配置（sourceType、endpoint、flattenPath、attributes.source）
- 为 `PersonalQuote` 实体添加 YAML source 配置
- 改造 `SubOrderGateway` 使用 `EntitySchemaMapper.map()` 替代硬编码解析
- 改造 `PersonalQuoteGateway` 使用 `EntitySchemaMapper.map()` 替代硬编码解析
- 保留旧的解析方法用于新旧输出一致性校验

## Capabilities

### New Capabilities

无新能力，仅重构现有解析逻辑。

### Modified Capabilities

无需求变更，仅实现方式变更。

## Impact

**代码变更**：
- `domain-ontology.yaml`：SubOrder 和 PersonalQuote 实体配置
- `SubOrderGateway.java`：注入 EntitySchemaMapper，调用 map()，添加一致性校验
- `PersonalQuoteGateway.java`：注入 EntitySchemaMapper，调用 map()，添加一致性校验

**影响范围**：
- SubOrder 和 PersonalQuote 的数据查询输出（预期保持一致）
- 无 API 变更，无破坏性变更
