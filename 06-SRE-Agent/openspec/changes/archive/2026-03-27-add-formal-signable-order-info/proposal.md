## Why

弹窗可签约S单分为两种合同类型：销售合同（type=8，已支持）和正签合同（type=3，缺失）。当用户查询正签合同下的可签约S单时，系统无法响应，需要补充对应的查询能力。

## What Changes

- **新增实体 `FormalSignableOrderInfo`**：挂载在 Order 下，通过 `projectOrderId` 直接调用 `formalQuotation/list/v2?contractType=3` 接口查询
- **新增 Gateway**：`FormalSignableOrderInfoGateway`，解析 `data[].signableOrderInfos[]` 两层结构（与 SignableOrderInfo 相同）
- **新增 endpoint**：`formal-sign-order-list`，注册到 `contract-endpoints.yml`
- **新增本体关系**：`Order → FormalSignableOrderInfo` via `projectOrderId`
- **更新提示词**：`sre-agent.md` 决策表新增 FormalSignableOrderInfo 示例行
- **补充测试**：`QueryAgentIT` 新增正签可签约S单查询测试

## Capabilities

### New Capabilities

- `formal-signable-order-info-query`：从订单号直接查询正签合同下的弹窗可签约S单，与 SignableOrderInfo 同构，接口不同

### Modified Capabilities

（无，现有 SignableOrderInfo 能力不变）

## Impact

- `domain-ontology.yaml`：新增 1 个实体，1 条关系（总计 12→13 实体，12→13 关系）
- `contract-endpoints.yml`：新增 1 个 endpoint（`formal-sign-order-list`）
- `FormalSignableOrderInfoGateway.java`：新建文件
- `sre-agent.md`：决策表新增 1 行
- `QueryAgentIT.java`：新增 1 个测试方法
