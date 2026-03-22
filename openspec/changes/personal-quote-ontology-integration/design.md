## Context

当前 SREmate 本体论引擎支持多跳路径查询，PersonalQuote 实体直接挂在 Order 下，通过 `extraParams` 手动传入参数。实际上 PersonalQuote 的查询参数（billCodeList/subOrderNoList/changeOrderId）来源于 ContractQuotationRelation（签约单据）的 `billCode` 和 `bindType` 字段。

**现有架构**：
```
Order → PersonalQuote (extraParams 手动传入)
```

**目标架构**：
```
Order → Contract → ContractQuotationRelation → PersonalQuote
                                                   ↑
                                          自动提取 billCode + bindType
```

**关键接口**：
- `EntityDataGateway.queryByFieldWithContext(fieldName, value, parentRecord)` - 已存在，支持从父记录获取额外参数
- `OntologyQueryEngine.attachLayer()` - 已支持层级展开，非首层自动调用 `queryByFieldWithContext`

## Goals / Non-Goals

**Goals:**
- PersonalQuote 可通过三跳路径（Order → Contract → ContractQuotationRelation → PersonalQuote）自动查询
- 用户查询"订单的个性化报价"时，系统自动从签约单据提取参数，无需手动提供
- bindType 语义正确映射：1→billCodeList, 2→subOrderNoList, 3→changeOrderId

**Non-Goals:**
- 不改变 PersonalQuote HTTP 接口本身（仍是同一个接口）
- 不支持从其他路径查询 PersonalQuote（如直接从 Contract 查询）
- 不处理 bindType 其他值（当前业务只有 1/2/3）

## Decisions

### Decision 1: 使用 queryByFieldWithContext 而非扩展 YAML

**选择**：在 PersonalQuoteGateway 中重写 `queryByFieldWithContext`，从父记录提取 billCode 和 bindType

**理由**：
- `queryByFieldWithContext` 接口已存在，专为这种场景设计
- SubOrder 已用此模式（从 BudgetBill 提取 homeOrderNo）
- 无需修改引擎代码，符合 YAML 驱动架构

**替代方案**：在 YAML 中定义 bindType 映射规则
- 优点：配置化，可扩展
- 缺点：增加 YAML 复杂度，当前只有 3 种映射，代码硬编码更简洁

### Decision 2: 移除 Order → PersonalQuote 直接关系

**选择**：删除 YAML 中 `Order → PersonalQuote` 关系，强制走三跳路径

**理由**：
- 消除歧义：PersonalQuote 参数来源明确（签约单据）
- 保持模型正确性：PersonalQuote 业务上属于签约单据，不应直接挂在订单下
- 减少维护负担：无需维护两套查询路径

**替代方案**：保留双路径，LLM 根据用户意图选择
- 缺点：增加 LLM 决策复杂度，可能产生混淆

### Decision 3: 聚合多个 ContractQuotationRelation 的 billCode

**选择**：PersonalQuoteGateway 接收单个 ContractQuotationRelation 记录，逐条查询后引擎层聚合

**理由**：
- 符合现有架构：引擎逐条展开子节点，Gateway 只处理单条记录
- 引擎已有并行查询机制，多条签约单据并行查询
- 无需修改引擎逻辑

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| 多合同多签约单据时查询次数多 | 引擎已并行查询，性能可接受 |
| bindType 值不在 1/2/3 范围 | Gateway 中加防御性校验，返回空列表并打 warn 日志 |
| LLM 仍需识别 PersonalQuote 实体 | 提示词中更新查询路径说明，引导用户走三跳 |
