## Context

SRE Agent 基于本体论查询引擎（OntologyQueryEngine），通过 YAML 定义实体关系，在多跳遍历时引擎自动将父节点完整记录传给子节点 Gateway（`queryByFieldWithContext`）。

当前排查"合同弹窗提示请先完成报价"需人工调接口，排查路径固定且可自动化：
1. 查弹窗数据（`getSignOrderList/V3`）是否为空
2. 查订单下是否存在有效 S 单（状态非 9001/9002）
3. 据此得出结论

`Contract` 实体已有 `type`（合同类型）和 `projectOrderId` 字段，引擎多跳遍历时会自动传入，无需改动工具接口。

## Goals / Non-Goals

**Goals:**
- 新增 `SignableOrderInfo` 实体，挂在 `Contract` 下，通过父合同的 `type` 字段路由接口
- 新增 `Order → SubOrder` 直查路径，支持按订单号查 S 单状态
- 新增诊断 skill，引导 agent 两步完成诊断

**Non-Goals:**
- 不支持三种及以上合同类型的弹窗诊断（当前仅 type=8 销售合同、type=3 正签合同）
- 不自动修复问题，只诊断和给出结论

## Decisions

### 决策 1：SignableOrderInfo 挂在 Contract 下，而非 Order 下

**选择**：`Contract → SignableOrderInfo`

**原因**：
- `getSignOrderList/V3` 需要 `projectOrderId`，但路由到哪个接口取决于合同类型 `type`
- `Contract` 记录同时含有这两个字段
- 引擎在多跳遍历时自动将 `Contract` 记录作为 `parentRecord` 传给 Gateway，Gateway 直接读取，无需修改工具接口
- 挂在 `Order` 下则无法获得合同类型，需要额外 `filters` 参数破坏接口简洁性

**备选方案**：
- `Order → SignableOrderInfo` + `filters={contractType}` 参数：需改 `OntologyQueryTool`、Engine、Gateway 接口，改动面大
- 两个独立实体（`SalesSignableOrderInfo` / `FormalSignableOrderInfo`）：语义重复，扩展性差

### 决策 2：Order → SubOrder 直查路径（方案 A）

**选择**：在现有 `SubOrder` 实体上新增 `homeOrderNo` lookupStrategy，并新增 `Order → SubOrder` 关系

**原因**：
- `sub-order-info` 接口本身支持只传 `homeOrderNo`，Gateway 扩展代价极小
- `SubOrder` 业务上就属于订单，直查是合理的
- 避免为诊断场景新建重复实体

**备选方案**：
- 新建 `OrderSubOrder` 实体：语义清晰但造成冗余

### 决策 3：不修改 QueryWithExtraParams / ontologyQuery 接口

**选择**：完全复用现有的 `queryByFieldWithContext` 机制

**原因**：通过将 `SignableOrderInfo` 挂在 `Contract` 下，所需参数（`type`、`projectOrderId`）自然从父节点流入，无需新参数传递机制。

## Risks / Trade-offs

- **[风险] 合同类型扩展**：如未来新增合同类型（非 type=3/8），需在 Gateway 内补充分支，不影响接口。→ 缓解：Gateway 内集中维护类型路由逻辑，扩展成本低
- **[风险] 订单下多合同场景**：`ontologyQuery(Order, orderNo, SignableOrderInfo)` 会为订单下每个合同都查询弹窗数据。→ 缓解：Skill 引导 LLM 按用户意图过滤对应合同类型的结果
- **[Trade-off] SubOrder 多路径查询**：SubOrder 现在既可从 BudgetBill 到达，也可从 Order 直查，语义稍复杂。→ 通过 lookupStrategy 字段名区分（`quotationOrderNo` vs `homeOrderNo`），Gateway 内部路由清晰

## Open Questions

（无）
