---
name: missing-personal-quote-diagnosis
description: 排查合同发起时缺少个性化报价的原因
---

# 排查缺失个性化报价

## 触发条件
用户说"合同发起时没有个性化报价"、"缺少个性化报价数据"

## 排查步骤

1. 查询订单下的合同列表
   - ontologyQuery(entity=Order, value={订单号}, queryScope=Contract)

2. 查询合同的签约单据
   - ontologyQuery(entity=Contract, value={合同号}, queryScope=ContractQuotationRelation)

3. 查询签约单据关联的个性化报价
   - ontologyQuery(entity=ContractQuotationRelation, value={签约单据ID}, queryScope=PersonalQuote)

4. 分析数据链
   - 订单有合同？→ 合同有签约单据？→ 签约单据有绑定报价？

5. 输出结论
   - 断点位置 + 可能原因 + 建议操作

## 输出格式

```json
{
  "断点位置": "xxx",
  "可能原因": ["原因1", "原因2"],
  "建议操作": ["操作1", "操作2"]
}
```

## Example

用户: "订单 826031210000003581 发起合同没个性化报价，帮我排查"
Action:
1. 查询订单 826031210000003581 的合同
2. 查询合同的签约单据
3. 查询签约单据的个性化报价
4. 分析数据链哪里断了
5. 输出结论
