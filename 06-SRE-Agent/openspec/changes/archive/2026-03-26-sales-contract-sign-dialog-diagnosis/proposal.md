## Why

当用户在发起销售合同或正签合同时，弹窗提示"请先完成报价"，目前 SRE agent 无法自动排查该现象的根本原因，只能依赖人工逐步调接口。通过新增排查 skill 和对应本体实体，让 agent 自动完成诊断并给出结论。

## What Changes

- 新增本体实体 `SignableOrderInfo`，挂在 `Contract` 节点下，通过 `queryByFieldWithContext` 从父合同记录获取 `type`（合同类型）和 `projectOrderId`，路由到对应的弹窗接口
- 新增 `SignableOrderInfoGateway`，根据合同类型（type=8 销售合同 / type=3 正签合同）路由到不同 HTTP 接口
- 扩展 `SubOrder` 实体，支持从 `Order` 直接查询（新增 `homeOrderNo` lookup strategy 和 `Order → SubOrder` 关系），用于诊断步骤中验证订单是否存在有效 S 单
- 新增诊断 skill：`sales-contract-sign-dialog-diagnosis`，引导 agent 完成两步诊断并输出结论

## Capabilities

### New Capabilities

- `signable-order-info`：本体实体 SignableOrderInfo 及其 Gateway，支持按合同类型查询弹窗可签约 S 单
- `order-to-suborder-direct-query`：Order → SubOrder 直查路径，支持按订单号查询 S 单状态
- `sign-dialog-diagnosis-skill`：销售/正签合同弹窗"请先完成报价"自动诊断 skill

### Modified Capabilities

（无现有 spec 级别行为变更）

## Impact

- `domain-ontology.yaml`：新增实体 `SignableOrderInfo`、`Contract → SignableOrderInfo` 关系，以及 `Order → SubOrder` 直查关系
- 新增 `SignableOrderInfoGateway.java`
- 现有 `SubOrderGateway.java`（如有）或对应实现：补充 `homeOrderNo` 查询分支
- `prompts/sre-agent.md`：同步更新场景描述
- `ContractOntologyIT.java`：新增集成测试用例
- 新增 `skills/sales-contract-sign-dialog-diagnosis/SKILL.md`
