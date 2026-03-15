# SREmate 集成测试执行报告

> 最后更新: 2026-03-15 22:39:53

> 运行命令: `./05-SREmate/scripts/run-integration-tests.sh`

---

## ContractOntologyIT

### ✅ contractSignedObjects_shouldCallOntologyQuery

- **输入:** C1767173898135504的签约单据
- **输出:** {"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","signedObjects":[{"contract_code":"C1767173898135504","bill_code":"...
- **耗时:** 2014ms
- **工具调用:**
  - `ontologyQuery` ✓ 430ms

### ✅ contractNodes_shouldCallOntologyQuery

- **输入:** C1767173898135504的合同节点
- **输出:** {"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","nodes":[{"nodeType":7,"fireTime":"2025-12-31 17:38:01"},{"nodeType...
- **耗时:** 1301ms
- **工具调用:**
  - `ontologyQuery` ✓ 430ms

### ✅ contractBasic_shouldCallOntologyQuery

- **输入:** C1767173898135504的合同基本信息
- **输出:** {"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20"}]}
- **耗时:** 1358ms
- **工具调用:**
  - `ontologyQuery` ✓ 430ms

### ✅ budgetBill_shouldCallOntologyQuery

- **输入:** 826031111000001859的报价单
- **输出:** {"error":"未找到BudgetBill: 826031111000001859"}
- **耗时:** 8586ms
- **工具调用:**
  - `ontologyQuery` ✓ 72ms
  - `ontologyQuery` ✓ 430ms

### ✅ orderContract_contractSignedObjAndNodes_shouldCallOntologyQuery

- **输入:** 825123110000002753合同签约单据和节点
- **输出:** {"queryEntity":"Order","queryValue":"825123110000002753","records":[{"projectOrderId":"825123110000002753","contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"platformInstanceId":0,"amount":413580.67,"ctime":"2025-12-31 11:10:48","signedObjects":[],"nodes":[{"nodeType":7,"fireTime"...
- **耗时:** 1996ms
- **工具调用:**
  - `ontologyQuery` ✓ 430ms

### ✅ orderContract_signedObjects_shouldCallOntologyQuery

- **输入:** 825123110000002753合同的签约单据
- **输出:** {"queryEntity":"Order","queryValue":"825123110000002753","records":[{"projectOrderId":"825123110000002753","contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"platformInstanceId":0,"amount":413580.67,"ctime":"2025-12-31 11:10:48","signedObjects":[]},{"contractCode":"C17671506517180...
- **耗时:** 2856ms
- **工具调用:**
  - `ontologyQuery` ✓ 72ms
  - `ontologyQuery` ✓ 430ms

### ✅ orderContract_allData_shouldCallOntologyQuery

- **输入:** 825123110000002753下的合同数据
- **输出:** {"queryEntity":"Order","queryValue":"825123110000002753","records":[{"projectOrderId":"825123110000002753","contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"platformInstanceId":0,"amount":413580.67,"ctime":"2025-12-31 11:10:48","nodes":[{"nodeType":7,"fireTime":"2025-12-31 11:10:...
- **耗时:** 22960ms
- **工具调用:**
  - `ontologyQuery` ✓ 7166ms
  - `callPredefinedEndpoint` ✓ 7162ms
  - `ontologyQuery` ✓ 48ms
  - `ontologyQuery` ✓ 72ms
  - `ontologyQuery` ✓ 430ms

### ✅ orderContract_contractNodes_shouldCallOntologyQuery

- **输入:** 825123110000002753合同节点
- **输出:** {"queryEntity":"Order","queryValue":"825123110000002753","records":[{"projectOrderId":"825123110000002753","contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"platformInstanceId":0,"amount":413580.67,"ctime":"2025-12-31 11:10:48","nodes":[{"nodeType":7,"fireTime":"2025-12-31 11:10:...
- **耗时:** 1489ms
- **工具调用:**
  - `ontologyQuery` ✓ 430ms

### ✅ contractConfig_shouldCallOntologyQuery

- **输入:** C1767173898135504的配置表
- **输出:** {"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","config":[{"contractCode":"C1767173898135504","projectOrderId":"825...
- **耗时:** 1526ms
- **工具调用:**
  - `ontologyQuery` ✓ 430ms

### ✅ contractFields_shouldCallOntologyQuery

- **输入:** C1767173898135504的合同字段
- **输出:** {"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","fields":[{"legalPhone":"","legalCertificateType":"1","companyName"...
- **耗时:** 1628ms
- **工具调用:**
  - `ontologyQuery` ✓ 430ms

### ✅ contractForm_shouldCallOntologyQuery

- **输入:** C1767173898135504的版式
- **输出:** {"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","form":[{"contractCode":"C1767173898135504","platformInstanceId":0,...
- **耗时:** 16364ms
- **工具调用:**
  - `ontologyQuery` ✓ 72ms
  - `ontologyQuery` ✓ 430ms

---

