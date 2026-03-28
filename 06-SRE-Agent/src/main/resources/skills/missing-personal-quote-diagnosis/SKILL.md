---
name: missing-personal-quote-diagnosis
description: 排查合同发起时缺少个性化报价的原因
---

# 排查缺失个性化报价

## 触发条件
用户说"合同发起时没有个性化报价"、"缺少个性化报价数据"、"排查个性化报价问题"

## 排查步骤

1. 查询订单下的合同列表
   - ontologyQuery(entity=Order, value={订单号}, queryScope=Contract)

2. 查询合同的签约单据
   - ontologyQuery(entity=Contract, value={合同号}, queryScope=ContractQuotationRelation)

3. 查询签约单据关联的个性化报价
   - ontologyQuery(entity=ContractQuotationRelation, value={签约单据ID}, queryScope=PersonalQuote)

4. **分析数据链并输出结论**
   - **必须按数据链逐步说明每步发现了什么、为何继续或终止排查**
   - 断点位置 + 可能原因 + 建议操作
   - **必须输出结论，不能只返回数据**

## 断点分析逻辑

```
数据链：订单 → 合同 → 签约单据 → 个性化报价

断点位置判断：
- 如果订单没有合同 → 断点：合同创建
- 如果合同没有签约单据 → 断点：签约单据生成
- 如果签约单据没有绑定报价 → 断点：报价绑定
- 如果有绑定报价 → 检查报价状态和金额

可能原因：
- 合同未生成
- 签约单据未生成
- 报价未创建或未绑定
- 报价状态异常（status不为1）
- 报价金额为0或异常
```

## Example

用户: "排查825123110000002753订单的个性化报价问题"
Action:
1. 查询订单的合同列表
2. 查询合同的签约单据
3. 查询签约单据的个性化报价
4. 分析数据链并输出结论（包含断点位置、可能原因、建议操作）