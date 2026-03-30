## 1. 本体模型

- [ ] 1.1 在 `domain-ontology.yaml` entities 中新增 `FormalSignableOrderInfo` 实体（含 aliases、lookupStrategies、attributes）
- [ ] 1.2 在 `domain-ontology.yaml` relations 中新增 `Order → FormalSignableOrderInfo` via `projectOrderId`

## 2. Endpoint 注册

- [ ] 2.1 在 `contract-endpoints.yml` 中新增 `formal-sign-order-list` endpoint（`formalQuotation/list/v2?contractType=3`）

## 3. Gateway 实现

- [ ] 3.1 新建 `FormalSignableOrderInfoGateway.java`，`getEntityName()` 返回 `"FormalSignableOrderInfo"`
- [ ] 3.2 实现 `queryByField`：仅接受 `projectOrderId`，调用 `formal-sign-order-list` endpoint
- [ ] 3.3 实现 `parseSignableOrders`：双层遍历 `data[].signableOrderInfos[]`，合并公司信息与S单详情

## 4. 提示词更新

- [ ] 4.1 在 `sre-agent.md` 决策表中追加 `FormalSignableOrderInfo` 示例行

## 5. 测试

- [ ] 5.1 在 `QueryAgentIT` 中新增 `query_formal_signable_order_info_by_order` 测试（断言 `entity=Order, queryScope=FormalSignableOrderInfo`）
- [ ] 5.2 运行 E2E 测试 `./scripts/run-e2e-tests.sh` 确认通过
