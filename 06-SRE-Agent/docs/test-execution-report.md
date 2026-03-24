# SREmate 集成测试执行报告

> 最后更新: 2026-03-24 23:19:41

> 运行命令: `./05-SREmate/scripts/run-integration-tests.sh`

---

## ContractOntologyIT

### ✅ contractInstance_shouldUseContractEntityAndContractInstanceScope

- **输入:** C1773303150687211的版式
- **输出:** {"queryEntity":"Contract","queryValue":"C1773303150687211","records":[{"contractCode":"C1773303150687211","type":3,"status":8,"amount":316107.00,"platformInstanceId":101831455,"projectOrderId":"826031210000000531","ctime":"2026-03-12 16:12:30","contractInstances":[{"instanceId":"101831455","formData...
- **耗时:** 9669ms
- **工具调用:**
  - `ontologyQuery` ✓ 561ms

### ✅ budgetBill_shouldUseOrderEntityAndBudgetBillScope

- **输入:** 826031111000001859的报价单
- **输出:** {"queryEntity":"Order","queryValue":"826031111000001859","records":[{"projectOrderId":"826031111000001859","budgetBills":[]}]}
- **耗时:** 5227ms
- **工具调用:**
  - `ontologyQuery` ✓ 2850ms

### ✅ personalQuote_withBillCode_shouldUsePersonalQuoteScope

- **输入:** 826031210000003581下GBILL260312104241050001的个性化报价
- **输出:** {"queryEntity":"Order","queryValue":"826031210000003581","records":[{"projectOrderId":"826031210000003581","contracts":[{"contractCode":"C1773288818023600","type":8,"status":1,"platformInstanceId":0,"amount":8.00,"ctime":"2026-03-12 12:13:38","contractQuotationRelations":[{"contractCode":"C177328881...
- **耗时:** 36782ms
- **工具调用:**
  - `queryPersonalQuote` ✓ 1043ms

### ✅ personalQuote_withSubOrder_shouldUsePersonalQuoteScope

- **输入:** 826031210000003581下S15260312120004471的个性化报价
- **输出:** {"queryEntity":"Order","queryValue":"826031210000003581","records":[{"projectOrderId":"826031210000003581","contracts":[{"contractCode":"C1773288818023600","type":8,"status":1,"platformInstanceId":0,"amount":8.00,"ctime":"2026-03-12 12:13:38","contractQuotationRelations":[{"contractCode":"C177328881...
- **耗时:** 32202ms
- **工具调用:**
  - `queryPersonalQuote` ✓ 737ms

### ✅ contractInstance_directQuery_shouldUseContractInstanceEntity

- **输入:** 145801的实例信息
- **输出:** {"queryEntity":"ContractInstance","queryValue":"145801","records":[{"instanceId":"145801","formData":{"id":145801,"formId":24455,"saveOrSubmit":null,"creator":2000000010336767,"currentOrg":"12057","cityCode":"110000","company":"XZXPT8888","application":"signContract","cformData":{"area":"120","packa...
- **耗时:** 4835ms
- **工具调用:**
  - `ontologyQuery` ✓ 251ms

### ✅ contractConfig_shouldUseContractEntityAndConfigScope

- **输入:** C1767173898135504的配置表
- **输出:** 我注意到您尝试查询的"配置表"信息在系统中没有找到对应的关联路径。这可能是因为：

1. 该合同没有配置表信息
2. 系统中未定义合同到配置表的关联关系

建议您确认：
- 合同编号是否正确
- 是否需要查询其他类型的关联数据（如节点、报价单等）

如果需要进一步帮助，请提供更多上下文信息。
- **耗时:** 2266ms
- **工具调用:**
  - `ontologyQuery` ✓ 47ms

### ✅ contractSignedObjects_shouldUseContractEntityAndQuotationRelationScope

- **输入:** C1767173898135504的签约单据
- **输出:** {"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contractQuotationRelations":[{"contractCode":"C1767173898135504","...
- **耗时:** 7011ms
- **工具调用:**
  - `ontologyQuery` ✓ 70ms

### ✅ subOrder_shouldUseOrderEntityAndSubOrderScope

- **输入:** 826031111000001859的S单
- **输出:** {"queryEntity":"Order","queryValue":"826031111000001859","records":[{"projectOrderId":"826031111000001859","budgetBills":[]}]}
- **耗时:** 4448ms
- **工具调用:**
  - `ontologyQuery` ✓ 2292ms

### ✅ orderContract_shouldUseOrderEntityAndContractScope

- **输入:** 825123110000002753下的合同
- **输出:** {"queryEntity":"Order","queryValue":"825123110000002753","records":[{"projectOrderId":"825123110000002753","contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"platformInstanceId":0,"amount":413580.67,"ctime":"2025-12-31 11:10:48"},{"contractCode":"C1767150651718033","type":7,"statu...
- **耗时:** 5139ms
- **工具调用:**
  - `ontologyQuery` ✓ 45ms

### ✅ contractFields_shouldUseContractEntityAndFieldScope

- **输入:** C1767173898135504的合同字段
- **输出:** {"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20"}]}
- **耗时:** 2997ms
- **工具调用:**
  - `ontologyQuery` ✓ 44ms

### ✅ orderContract_signedObjectsAndNodes_shouldUseOrderEntityAndMultipleScopes

- **输入:** 825123110000002753合同签约单据和节点
- **输出:** {"queryEntity":"Order","queryValue":"825123110000002753","records":[{"projectOrderId":"825123110000002753","contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"platformInstanceId":0,"amount":413580.67,"ctime":"2025-12-31 11:10:48","contractNodes":[{"nodeType":7,"fireTime":"2025-12-3...
- **耗时:** 15758ms
- **工具调用:**
  - `ontologyQuery` ✓ 107ms

### ✅ contractBasic_shouldUseContractEntity

- **输入:** C1767173898135504的合同基本信息
- **输出:** {"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20"}]}
- **耗时:** 3546ms
- **工具调用:**
  - `ontologyQuery` ✓ 45ms

### ✅ contractNodes_shouldUseContractEntityAndContractNodeScope

- **输入:** C1767173898135504的合同节点
- **输出:** {"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contractNodes":[{"nodeType":7,"fireTime":"2025-12-31 17:38:01"},{"...
- **耗时:** 5425ms
- **工具调用:**
  - `ontologyQuery` ✓ 81ms

---

