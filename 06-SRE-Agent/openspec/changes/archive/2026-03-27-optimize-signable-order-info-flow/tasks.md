## 1. 本体模型重构

- [ ] 1.1 更新 `domain-ontology.yaml`：删除 `Contract → SignableOrderInfo` 关系
- [ ] 1.2 更新 `domain-ontology.yaml`：新增 `Order → SignableOrderInfo` 关系（via projectOrderId）
- [ ] 1.3 更新 `domain-ontology.yaml`：修正 `SignableOrderInfo` lookupStrategies 从 contractCode 改为 projectOrderId
- [ ] 1.4 更新 `domain-ontology.yaml`：更新 `SignableOrderInfo` attributes 为接口真实返回字段（companyName、bindCode、goodsInfo 等）
- [ ] 1.5 更新 `domain-ontology.yaml`：为 `SignableOrderInfo` 新增别名 "可签约S单"

## 2. Gateway 简化与修复

- [ ] 2.1 重写 `SignableOrderInfoGateway.queryByField`：去掉 contractType 常量和路由分支，直接以 `projectOrderId` 调用 `sign-order-list` 接口
- [ ] 2.2 修复 `SignableOrderInfoGateway.parseSignableOrders`：改为双层遍历 `data[].signableOrderInfos[]`，外层取 companyName/companyCode，内层取 S 单详情
- [ ] 2.3 删除 `queryByFieldWithContext` 中依赖 parentRecord 的 contractType 路由逻辑

## 3. 提示词与 SKILL 更新

- [ ] 3.1 更新 `sre-agent.md`：决策样例表新增 SignableOrderInfo 的订单号示例行
- [ ] 3.2 更新 `sre-agent.md`：删除基于合同号查询 SignableOrderInfo 的示例行
- [ ] 3.3 更新 `SKILL.md`（sales-contract-sign-dialog-diagnosis）：触发条件新增"无定软电报价"
- [ ] 3.4 更新 `SKILL.md`：查询路径改为从 Order 直接查询 SignableOrderInfo（一跳）
- [ ] 3.5 更新 `SKILL.md`：决策矩阵扩充为 4 行，区分"无S单"和"全部9001/9002"子情况

## 4. 测试补充与修复

- [ ] 4.1 `QueryAgentIT`：新增 `query_signable_order_info_by_order` 测试（以订单号查询 SignableOrderInfo，断言 entity=Order, queryScope=SignableOrderInfo）
- [ ] 4.2 `QueryAgentIT`：删除基于合同号查询 SignableOrderInfo 的测试（无业务需求）
- [ ] 4.3 `InvestigateAgentIT`：更新 `investigate_sales_contract_sign_dialog` 测试，问题中包含订单号
- [ ] 4.4 运行 E2E 测试 `./scripts/run-e2e-tests.sh`，确认所有测试通过

## 5. 验证

- [ ] 5.1 重启应用服务，访问 `/ontology` 页面确认图结构：Order → SignableOrderInfo 关系正确显示，无 Contract → SignableOrderInfo
- [ ] 5.2 手动验证：以订单号提问"查询XXX销售合同的可签约S单"，确认返回 companyName、bindCode、goodsInfo 等真实字段
