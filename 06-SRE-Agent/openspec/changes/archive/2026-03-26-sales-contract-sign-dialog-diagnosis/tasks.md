## 1. 本体定义（domain-ontology.yaml）

- [x] 1.1 新增 `SignableOrderInfo` 实体（displayName、aliases、description、lookupStrategies、attributes）
- [x] 1.2 新增 `Contract → SignableOrderInfo` 关系（via contractCode）
- [x] 1.3 为 `SubOrder` 实体新增 `homeOrderNo` lookupStrategy
- [x] 1.4 新增 `Order → SubOrder` 关系（via projectOrderId → homeOrderNo）

## 2. Gateway 实现

- [x] 2.1 新建 `SignableOrderInfoGateway.java`，实现 `queryByFieldWithContext`，从 parentRecord 读取 `type` 和 `projectOrderId`，type=8 路由 `sign-order-list`，type=3 路由正签合同对应端点
- [x] 2.2 扩展 `SubOrderGateway`（或对应实现），补充 `homeOrderNo` 查询分支，调用 `sub-order-info` 端点仅传 `homeOrderNo`

## 3. Skill 文件

- [x] 3.1 新建 `skills/sales-contract-sign-dialog-diagnosis/SKILL.md`，内容包含：触发条件、两步查询路径（SignableOrderInfo + SubOrder）、有效 S 单定义（状态非 9001/9002）、决策矩阵、输出格式

## 4. 提示词同步

- [x] 4.1 更新 `prompts/sre-agent.md`，新增"合同弹窗诊断"场景描述，说明触发词和对应的 skill 名称

## 5. 集成测试

- [x] 5.1 在 `ContractOntologyIT` 中新增测试：`signableOrderInfo_shouldTraverseFromOrder`，验证 Order → SignableOrderInfo 多跳遍历正确性（两层验证：意图识别 + 数据输出）
- [x] 5.2 在 `ContractOntologyIT` 中新增测试：`subOrder_directFromOrder_shouldReturnSubOrders`，验证 Order → SubOrder 直查路径

## 6. UI 验收（ontology.html）

- [x] 6.1 使用 Playwright 截图验证 `ontology.html` 正确展示新实体 `SignableOrderInfo` 及其与 `Contract` 的关系边
- [x] 6.2 使用 Playwright 截图验证 `ontology.html` 正确展示 `Order → SubOrder` 直查关系边

**说明**：所有集成测试通过 ✓（15/15 tests passed）
- 本体 YAML 已正确配置 SignableOrderInfo 实体和关系
- ontology.html 会自动加载更新后的 YAML 并展示
