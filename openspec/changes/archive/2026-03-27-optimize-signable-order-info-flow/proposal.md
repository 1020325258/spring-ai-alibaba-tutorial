## Why

弹窗可签约S单（SignableOrderInfo）原来挂在合同维度下（Contract → SignableOrderInfo），但该数据实际属于订单维度，接口只需 `projectOrderId` 即可查询；此前合同类型路由逻辑（type=8/type=3）和中间层合同跳转均属冗余设计。同时，SKILL.md 对"无定软电报价"场景的排查流程描述不完整，缺少对 S 单状态的细化判断。

## What Changes

- **本体模型重构**：将 `SignableOrderInfo` 从 Contract 子节点改为 Order 直接子节点（删除 `Contract → SignableOrderInfo` 关系，新增 `Order → SignableOrderInfo` 关系，via `projectOrderId`）
- **Gateway 简化**：`SignableOrderInfoGateway` 去掉 contractType 路由逻辑，`queryByField("projectOrderId", value)` 直接调用 `sign-order-list` 接口
- **响应解析修复**：`sign-order-list` 返回结构为 `data[].signableOrderInfos[]`，原代码错误地在 `data[]` 层读字段导致全 null，修复为正确的两层解析
- **实体属性更新**：`SignableOrderInfo` 属性从 `orderNo/status/statusDesc` 更新为接口真实返回字段（companyName、bindCode、goodsInfo 等）
- **SKILL.md 优化**：触发条件新增"无定软电报价"；诊断路径明确说明直接通过 Order 起点查询；决策矩阵扩充为 4 行，区分"无S单"和"全部9001/9002"两种子情况
- **提示词更新**：`sre-agent.md` 决策样例表增加 SignableOrderInfo 的订单号示例行；`SignableOrderInfo` 实体别名新增"可签约S单"
- **测试补充**：`QueryAgentIT` 新增订单号起点的 SignableOrderInfo 查询测试；`InvestigateAgentIT` 诊断测试补充订单号

## Capabilities

### New Capabilities

- `signable-order-info-query`：从订单号直接查询弹窗可签约S单，引擎一跳到位，返回完整的套餐/S单绑定信息

### Modified Capabilities

- `sales-contract-sign-dialog-diagnosis`：排查流程由"合同号→弹窗数据"改为"订单号→弹窗数据（直查）"；决策矩阵细化了无可签约S单的两种子原因

## Impact

- `domain-ontology.yaml`：11 条关系不变，但 Contract→SignableOrderInfo 替换为 Order→SignableOrderInfo
- `SignableOrderInfoGateway.java`：逻辑大幅简化，去掉 contractType 常量和路由分支
- `sre-agent.md`：决策样例表新增 1 行
- `skills/sales-contract-sign-dialog-diagnosis/SKILL.md`：触发条件、查询路径、决策矩阵全部更新
- `QueryAgentIT.java`、`InvestigateAgentIT.java`：测试用例调整
