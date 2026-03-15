# SREmate 集成测试执行报告

> 最后更新: 2026-03-15 11:46:02

> 运行命令: `./05-SREmate/scripts/run-integration-tests.sh`

---

## ContractOntologyIT

### ✅ contractSignedObjects_shouldCallOntologyQuery

- **输入:** C1767173898135504的签约单据
- **输出:** {"queryEntity":"Contract","queryValue":"C1767173898135504","contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","signedObjects":[{"contract_code":"C1767173898135504","bill_code":"GBILL2512311...
- **耗时:** 2657ms
- **工具调用:**
  - `ontologyQuery` ✓ 432ms

### ✅ contractNodes_shouldCallOntologyQuery

- **输入:** C1767173898135504的合同节点
- **输出:** {"queryEntity":"Contract","queryValue":"C1767173898135504","contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","nodes":[{"nodeType":7,"fireTime":"2025-12-31 17:38:01"},{"nodeType":8,"fireTim...
- **耗时:** 2207ms
- **工具调用:**
  - `ontologyQuery` ✓ 432ms

### ✅ contractBasic_shouldCallOntologyQuery

- **输入:** C1767173898135504的合同基本信息
- **输出:** {"queryEntity":"Contract","queryValue":"C1767173898135504","contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20"}
- **耗时:** 1890ms
- **工具调用:**
  - `ontologyQuery` ✓ 432ms

### ✅ budgetBill_shouldCallOntologyQuery

- **输入:** 826031111000001859的报价单
- **输出:** {"queryEntity":"BudgetBill","queryValue":"826031111000001859","budgetBills":[{"billCode":"GBILL260311115521050009","billType":"100","billTypeDesc":"标准报价","statusDesc":"正式版","originalBillCode":"GBILL260311115521050009","subOrders":[]},{"billCode":"GBILL260312161413930019","billType":"103","billTypeDe...
- **耗时:** 7458ms
- **工具调用:**
  - `ontologyQuery` ✓ 83ms
  - `ontologyQuery` ✓ 432ms

### ✅ orderContract_contractSignedObjAndNodes_shouldCallOntologyQuery

- **输入:** 825123110000002753合同签约单据和节点
- **输出:** {"queryEntity":"Order","queryValue":"825123110000002753","contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"amount":413580.67,"platformInstanceId":0,"ctime":"2025-12-31 11:10:48","nodes":[{"nodeType":7,"fireTime":"2025-12-31 11:10:42"},{"nodeType":8,"fireTime":"2025-12-31 11:10:42...
- **耗时:** 6898ms
- **工具调用:**
  - `ontologyQuery` ✓ 83ms
  - `ontologyQuery` ✓ 432ms

### ✅ orderContract_signedObjects_shouldCallOntologyQuery

- **输入:** 825123110000002753合同的签约单据
- **输出:** {"queryEntity":"Order","queryValue":"825123110000002753","contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"amount":413580.67,"platformInstanceId":0,"ctime":"2025-12-31 11:10:48","signedObjects":[]},{"contractCode":"C1767150651718033","type":7,"status":8,"amount":0.00,"platformIns...
- **耗时:** 1645ms
- **工具调用:**
  - `ontologyQuery` ✓ 432ms

### ✅ orderContract_allData_shouldCallOntologyQuery

- **输入:** 825123110000002753下的合同数据
- **输出:** {"queryEntity":"Order","queryValue":"825123110000002753","contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"amount":413580.67,"platformInstanceId":0,"ctime":"2025-12-31 11:10:48","nodes":[{"nodeType":7,"fireTime":"2025-12-31 11:10:42"},{"nodeType":8,"fireTime":"2025-12-31 11:10:42...
- **耗时:** 3514ms
- **工具调用:**
  - `ontologyQuery` ✓ 83ms
  - `ontologyQuery` ✓ 432ms

### ✅ orderContract_contractNodes_shouldCallOntologyQuery

- **输入:** 825123110000002753合同节点
- **输出:** {"queryEntity":"Order","queryValue":"825123110000002753","contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"amount":413580.67,"platformInstanceId":0,"ctime":"2025-12-31 11:10:48","nodes":[{"nodeType":7,"fireTime":"2025-12-31 11:10:42"},{"nodeType":8,"fireTime":"2025-12-31 11:10:42...
- **耗时:** 1213ms
- **工具调用:**
  - `ontologyQuery` ✓ 432ms

### ✅ contractFields_shouldCallOntologyQuery

- **输入:** C1767173898135504的合同字段
- **输出:** {"queryEntity":"Contract","queryValue":"C1767173898135504","contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","fields":{"legalPhone":"","legalCertificateType":"1","companyName":"","authedAg...
- **耗时:** 2435ms
- **工具调用:**
  - `ontologyQuery` ✓ 83ms
  - `ontologyQuery` ✓ 432ms

---

