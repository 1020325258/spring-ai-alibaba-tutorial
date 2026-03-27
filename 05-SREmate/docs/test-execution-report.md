# SREmate 集成测试执行报告

> 最后更新: 2026-03-27 18:36:13

> 运行命令: `./05-SREmate/scripts/run-integration-tests.sh`

---

## ContractOntologyIT

### ✅ contractInstance_shouldUseContractEntityAndContractInstanceScope

- **输入:** C1773303150687211的版式
- **输出:** {"queryEntity":"Contract","queryValue":"C1773303150687211","records":[{"contractCode":"C1773303150687211","type":3,"status":8,"amount":316107.00,"platformInstanceId":101831455,"projectOrderId":"826031210000000531","ctime":"2026-03-12 16:12:30","contractInstances":[{"instanceId":"101831455","formData...
- **耗时:** 2114ms
- **工具调用:**
  - `ontologyQuery` ✓ 352ms

### ✅ budgetBill_shouldUseOrderEntityAndBudgetBillScope

- **输入:** 826031111000001859的报价单
- **输出:** {"queryEntity":"Order","queryValue":"826031111000001859","records":[{"projectOrderId":"826031111000001859","budgetBills":[]}]}
- **耗时:** 6358ms
- **工具调用:**
  - `ontologyQuery` ✓ 5467ms

### ✅ personalQuote_withBillCode_shouldUsePersonalQuoteScope

- **输入:** 826031210000003581下GBILL260312104241050001的个性化报价
- **输出:** {"queryEntity":"Order","queryValue":"826031210000003581","records":[{"projectOrderId":"826031210000003581","contracts":[{"contractCode":"C1773288818023600","type":8,"status":1,"platformInstanceId":0,"amount":8.00,"projectOrderId":"826031210000003581","ctime":"2026-03-12 12:13:38","contractQuotationR...
- **耗时:** 2248ms
- **工具调用:**
  - `ontologyQuery` ✓ 980ms

### ✅ personalQuote_withSubOrder_shouldUsePersonalQuoteScope

- **输入:** 826031210000003581下S15260312120004471的个性化报价
- **输出:** {"queryEntity":"Order","queryValue":"826031210000003581","records":[{"projectOrderId":"826031210000003581","contracts":[{"contractCode":"C1773288818023600","type":8,"status":1,"platformInstanceId":0,"amount":8.00,"projectOrderId":"826031210000003581","ctime":"2026-03-12 12:13:38","contractQuotationR...
- **耗时:** 2219ms
- **工具调用:**
  - `ontologyQuery` ✓ 837ms

### ✅ contractInstance_directQuery_shouldUseContractInstanceEntity

- **输入:** 145801的实例信息
- **输出:** {"queryEntity":"ContractInstance","queryValue":"145801","records":[{"instanceId":"145801","formData":{"id":145801,"formId":24455,"saveOrSubmit":null,"creator":2000000010336767,"currentOrg":"12057","cityCode":"110000","company":"XZXPT8888","application":"signContract","cformData":{"area":"120","packa...
- **耗时:** 1201ms
- **工具调用:**
  - `ontologyQuery` ✓ 69ms

### ✅ contractConfig_shouldUseContractEntityAndConfigScope

- **输入:** C1767173898135504的配置表
- **输出:** {"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contractConfigs":[{"contractCode":"C1767173898135504","projectOrde...
- **耗时:** 1574ms
- **工具调用:**
  - `ontologyQuery` ✓ 82ms

### ✅ contractSignedObjects_shouldUseContractEntityAndQuotationRelationScope

- **输入:** C1767173898135504的签约单据
- **输出:** {"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contractQuotationRelations":[{"contractCode":"C1767173898135504","...
- **耗时:** 1212ms
- **工具调用:**
  - `ontologyQuery` ✓ 47ms

### ✅ subOrder_shouldUseOrderEntityAndSubOrderScope

- **输入:** 826031111000001859的S单
- **输出:** {"queryEntity":"Order","queryValue":"826031111000001859","records":[{"projectOrderId":"826031111000001859","subOrders":[{"orderNo":"S14260311120002395","projectChangeNo":"","mdmCode":"V201800236","dueAmount":"840.0","status":"900"},{"orderNo":"S15260311120003962","projectChangeNo":"","mdmCode":"V201...
- **耗时:** 1397ms
- **工具调用:**
  - `ontologyQuery` ✓ 172ms

### ✅ orderContract_shouldUseOrderEntityAndContractScope

- **输入:** 825123110000002753下的合同
- **输出:** {"queryEntity":"Order","queryValue":"825123110000002753","records":[{"projectOrderId":"825123110000002753","contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"platformInstanceId":0,"amount":413580.67,"projectOrderId":"825123110000002753","ctime":"2025-12-31 11:10:48"},{"contractCod...
- **耗时:** 874ms
- **工具调用:**
  - `ontologyQuery` ✓ 31ms

### ✅ contractFields_shouldUseContractEntityAndFieldScope

- **输入:** C1767173898135504的合同字段
- **输出:** {"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contractFields":[{"legalPhone":"","legalCertificateType":"1","comp...
- **耗时:** 1186ms
- **工具调用:**
  - `ontologyQuery` ✓ 40ms

### ✅ subOrder_directFromOrder_shouldReturnSubOrders

- **输入:** 825123110000002753的S单列表
- **输出:** {"queryEntity":"Order","queryValue":"825123110000002753","records":[{"projectOrderId":"825123110000002753","subOrders":[{"orderNo":"S14251231110001838","projectChangeNo":"","mdmCode":"V201800236","dueAmount":"10.0","status":"1000"},{"orderNo":"S14251231110002125","projectChangeNo":"","mdmCode":"V201...
- **耗时:** 1032ms
- **工具调用:**
  - `ontologyQuery` ✓ 154ms

### ✅ orderContract_signedObjectsAndNodes_shouldUseOrderEntityAndMultipleScopes

- **输入:** 825123110000002753合同签约单据和节点
- **输出:** {"queryEntity":"Order","queryValue":"825123110000002753","records":[{"projectOrderId":"825123110000002753","contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"platformInstanceId":0,"amount":413580.67,"projectOrderId":"825123110000002753","ctime":"2025-12-31 11:10:48","contractQuota...
- **耗时:** 1308ms
- **工具调用:**
  - `ontologyQuery` ✓ 73ms

### ✅ contractBasic_shouldUseContractEntity

- **输入:** C1767173898135504的合同基本信息
- **输出:** {"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20"}]}
- **耗时:** 956ms
- **工具调用:**
  - `ontologyQuery` ✓ 28ms

### ✅ signableOrderInfo_shouldTraverseFromOrder

- **输入:** 825123117000001474的弹窗S单
- **输出:** {"queryEntity":"Order","queryValue":"825123117000001474","records":[{"projectOrderId":"825123117000001474","contracts":[{"contractCode":"C1767173891748434","type":3,"status":8,"platformInstanceId":0,"amount":413579.67,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:11","signableOrder...
- **耗时:** 1669ms
- **工具调用:**
  - `ontologyQuery` ✓ 589ms

### ✅ contractNodes_shouldUseContractEntityAndContractNodeScope

- **输入:** C1767173898135504的合同节点
- **输出:** {"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contractNodes":[{"nodeType":7,"fireTime":"2025-12-31 17:38:01"},{"...
- **耗时:** 1038ms
- **工具调用:**
  - `ontologyQuery` ✓ 47ms

---

