## MODIFIED Requirements

### Requirement: 排查弹窗提示"请先完成报价"或"无定软电报价"的原因
系统 SHALL 支持以订单号为起点，按如下两步流程排查弹窗异常：

第一步：验证用户描述是否属实
- 调用 `ontologyQuery(entity=Order, value={订单号}, queryScope=SignableOrderInfo)`
- 若弹窗数据为空 → 属实，进入第二步

第二步：定位原因
- 调用 `ontologyQuery(entity=Order, value={订单号}, queryScope=SubOrder)`
- 根据 S 单情况套用决策矩阵

决策矩阵：

| 弹窗数据 | S 单情况 | 结论 | 领域归属 |
|---------|---------|------|---------|
| 空 | 无 S 单 | 未报价或报价未下单 | 报价或订单 |
| 空 | 全部 9001/9002 | 所有S单已取消/退款，无可签约 | 报价或订单 |
| 空 | 有有效 S 单 | 弹窗接口逻辑异常，需人工介入 | 合同 |
| 有数据 | - | 弹窗数据正常，确认用户具体场景 | - |

#### Scenario: 订单无可签约S单且无有效S单（未报价场景）
- **WHEN** 用户提问"排查订单{订单号}销售合同弹窗提示请先完成报价的原因"
- **THEN** Agent 先调用 `readSkill` 加载 `sales-contract-sign-dialog-diagnosis`
- **THEN** Agent 调用 `ontologyQuery(entity=Order, queryScope=SignableOrderInfo)` 验证弹窗数据为空
- **THEN** Agent 调用 `ontologyQuery(entity=Order, queryScope=SubOrder)` 查询 S 单状态
- **THEN** Agent 输出四段式排查结论，说明无可签约S单的原因并给出领域归属

#### Scenario: 触发条件覆盖"无定软电报价"关键词
- **WHEN** 用户提问包含"无定软电报价"
- **THEN** Agent 识别为弹窗诊断场景，调用 `readSkill(skillName=sales-contract-sign-dialog-diagnosis)`
