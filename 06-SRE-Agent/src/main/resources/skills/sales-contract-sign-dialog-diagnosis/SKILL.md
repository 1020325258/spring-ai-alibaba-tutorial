---
name: sales-contract-sign-dialog-diagnosis
description: 排查销售/正签合同弹窗提示"请先完成报价"的原因
---

# 合同弹窗诊断

## 触发条件

用户说"合同弹窗提示请先完成报价"、"合同发起时弹窗没有数据"、"销售合同/正签合同弹窗异常"

## 两步查询路径

### 第一步：查询弹窗数据

从 Contract 出发，查询 SignableOrderInfo（弹窗可签约S单）：

```
ontologyQuery(entity=Contract, value={合同号}, queryScope=SignableOrderInfo)
```

- 引擎自动将 Contract 记录作为 parentRecord 传给 SignableOrderInfoGateway
- Gateway 从 parentRecord 读取 `type`（合同类型）和 `projectOrderId`
- type=8（销售合同）→ 调用 `sign-order-list` 端点
- type=3（正签合同）→ TODO: 待配置

### 第二步：查询有效 S 单

从 Order 出发，直接查询 SubOrder（订单下S单）：

```
ontologyQuery(entity=Order, value={订单号}, queryScope=SubOrder)
```

或从 BudgetBill 出发查询：

```
ontologyQuery(entity=BudgetBill, value={报价单号}, queryScope=SubOrder)
```

## 有效 S 单定义

**有效 S 单**：状态码不在 [9001, 9002] 范围内的 S 单

- 9001：已取消
- 9002：已退款
- 其他状态：视为有效 S 单

## 决策矩阵

| 弹窗数据 | 有效 S 单 | 结论 |
|---------|----------|------|
| 空 | 无 | 用户尚未下单 |
| 空 | 有 | 弹窗接口逻辑异常，建议人工介入 |
| 有数据 | - | 弹窗可正常展示数据，请确认用户描述的具体场景 |

## 排查步骤

1. 根据用户提供的订单号/合同号，确定查询起点
2. 执行第一步：查询弹窗数据（SignableOrderInfo）
3. 执行第二步：查询 S 单状态（SubOrder），过滤无效状态（9001/9002）
4. 根据决策矩阵输出结论

## 输出格式

```json
{
  "弹窗数据": "有数据/无数据",
  "有效S单": "有/无",
  "结论": "用户尚未下单/弹窗接口异常/数据正常",
  "建议操作": ["具体操作建议"]
}
```

## Example

用户: "订单 826032417000002739 的销售合同弹窗提示请先完成报价"

Action:
1. 查询订单 826032417000002739 下的合同（type=8 销售合同）
2. 查询合同对应的弹窗数据（SignableOrderInfo）
3. 查询订单下的 S 单列表（SubOrder），过滤状态 9001/9002
4. 根据决策矩阵输出结论