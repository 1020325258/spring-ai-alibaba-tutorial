# SRE-Agent QA 评估报告

> 执行时间: 2026-03-31 21:03:30

## 统计

| 状态 | 数量 |
|------|------|
| ✅ 通过 | 4 |
| ❌ 失败 | 0 |

---

## 详细结果

### ✅ query-contract-basic

**输入:** C1767173898135504的合同基本信息

**预期:** 返回该合同的基本信息，应包含合同号、合同状态等核心字段

**实际输出:**
```
{"type":"thinking","content":"**[路由器]** 路由至 **queryAgent**"}```json
{"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20"}]}
```
```

**评估结果:** ✅ 通过 — 包含合同号和状态等核心信息

---

### ✅ query-order-contracts

**输入:** 825123110000002753下的合同

**预期:** 返回该订单下的所有合同，每个合同应包含合同号

**实际输出:**
```
{"type":"thinking","content":"**[路由器]** 路由至 **queryAgent**"}```json
{"queryEntity":"Order","queryValue":"825123110000002753","records":[{"projectOrderId":"825123110000002753","contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"platformInstanceId":0,"amount":413580.67,"ctime":"2025-12-31 11:10:48"},{"contractCode":"C1767150651718033","type":7,"status":8,"platformInstanceId":0,"amount":0.00,"ctime":"2025-12-31 11:10:55"},{"contractCode":"C1767150652428677","type":8,"status":8,"pl...
```

**评估结果:** ✅ 通过 — 包含合同号信息

---

### ✅ query-contract-nodes

**输入:** C1767173898135504的节点

**预期:** 返回该合同的节点信息，应包含节点类型、触发时间等字段

**实际输出:**
```
{"type":"thinking","content":"**[路由器]** 路由至 **queryAgent**"}```json
{"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contractNodes":[{"nodeType":7,"fireTime":"2025-12-31 17:38:01"},{"nodeType":8,"fireTime":"2025-12-31 17:38:01"},{"nodeType":2,"fireTime":"2025-12-31 17:38:20"},{"nodeType":3,"fireTime":"2025-12-31 1...
```

**评估结果:** ✅ 通过 — 包含节点类型和触发时间信息

---

### ✅ investigate-sign-dialog-no-quote

**输入:** 订单825123110000002753发起提示无定软电报价，帮我排查

**预期:** 给出排查分析结论，说明该订单是否缺少个性化报价，并给出判断依据

**实际输出:**
```
{"type":"thinking","content":"**[路由器]** 路由至 **investigateAgent**"}▶ 步骤2 - 验证用户描述（查询弹窗数据）：
  执行：ontologyQuery(entity=Order, value=825123110000002753, queryScope=PersonalSignableOrderInfo)
  发现：返回了15条可签约S单数据，例如"bindCode":"S14251231110001838"、"goodsInfo":"定制柜-套外"等
  判断：弹窗数据非空，用户描述不属实，排查结束

**【分析】** 根据查询结果，订单825123110000002753下存在15条可签约S单，说明系统弹窗数据正常，存在可签约的S单。这与用户描述的"无定软电报价"不符。

**【结论】** 用户描述不属实，系统弹窗数据正常，存在可签约的S单。

**【建议】** 建议用户核实当前操作是否正确，确认是否选择了正确的合同类型或检查是否有其他筛选条件导致显示异常。
```

**评估结果:** ✅ 通过 — 实际输出包含核心结论和依据

---

