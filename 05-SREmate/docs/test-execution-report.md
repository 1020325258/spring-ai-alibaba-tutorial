# SREmate 集成测试执行报告

> 最后更新: 2026-03-15 10:48:12

> 运行命令: `./05-SREmate/scripts/run-integration-tests.sh`

---

## ContractOntologyIT

### ✅ contractSignedObjects_shouldCallOntologyQuery

- **输入:** C1767173898135504的签约单据
- **输出:** {"queryEntity":"Contract","queryValue":"C1767173898135504","contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","signedObjects":[{"contract_code":"C1767173898135504","bill_code":"GBILL2512311...
- **耗时:** 2975ms
- **工具调用:**
  - `ontologyQuery` ✓ 412ms

### ✅ contractNodes_shouldCallOntologyQuery

- **输入:** C1767173898135504的合同节点
- **输出:** {"queryEntity":"Contract","queryValue":"C1767173898135504","contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","nodes":[{"nodeType":7,"fireTime":"2025-12-31 17:38:01"},{"nodeType":8,"fireTim...
- **耗时:** 1731ms
- **工具调用:**
  - `ontologyQuery` ✓ 412ms

### ✅ contractBasic_shouldCallOntologyQuery

- **输入:** C1767173898135504的合同基本信息
- **输出:** {"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20"}
- **耗时:** 1564ms
- **工具调用:**
  - `ontologyQuery` ✓ 412ms

### ✅ budgetBill_shouldCallOntologyQuery

- **输入:** 826031111000001859的报价单
- **输出:** {"error":"502 Bad Gateway from POST http://utopia-atom.nrs-escrow.ttb.test.ke.com/api/budget-bill/list"}
- **耗时:** 16720ms
- **工具调用:**
  - `callPredefinedEndpoint` ✓ 7148ms
  - `queryContractBasic` ✓ 49ms
  - `ontologyQuery` ✓ 75ms
  - `ontologyQuery` ✓ 412ms

### ✅ orderContract_contractSignedObjAndNodes_shouldCallOntologyQuery

- **输入:** 825123110000002753合同签约单据和节点
- **输出:** {"error":"502 Bad Gateway from POST http://utopia-atom.nrs-escrow.ttb.test.ke.com/api/budget-bill/list"}
- **耗时:** 8860ms
- **工具调用:**
  - `ontologyQuery` ✓ 75ms
  - `ontologyQuery` ✓ 412ms

### ✅ orderContract_signedObjects_shouldCallOntologyQuery

- **输入:** 825123110000002753合同的签约单据
- **输出:** {"queryEntity":"Order","queryValue":"825123110000002753","contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"amount":413580.67,"platformInstanceId":0,"ctime":"2025-12-31 11:10:48","signedObjects":[]},{"contractCode":"C1767150651718033","type":7,"status":8,"amount":0.00,"platformIns...
- **耗时:** 1381ms
- **工具调用:**
  - `ontologyQuery` ✓ 412ms

### ✅ orderContract_allData_shouldCallOntologyQuery

- **输入:** 825123110000002753下的合同数据
- **输出:** {"error":"502 Bad Gateway from POST http://utopia-atom.nrs-escrow.ttb.test.ke.com/api/budget-bill/list"}
- **耗时:** 8744ms
- **工具调用:**
  - `ontologyQuery` ✓ 75ms
  - `ontologyQuery` ✓ 412ms

### ✅ orderContract_contractNodes_shouldCallOntologyQuery

- **输入:** 825123110000002753合同节点
- **输出:** {"queryEntity":"Order","queryValue":"825123110000002753","contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"amount":413580.67,"platformInstanceId":0,"ctime":"2025-12-31 11:10:48","nodes":[{"nodeType":7,"fireTime":"2025-12-31 11:10:42"},{"nodeType":8,"fireTime":"2025-12-31 11:10:42...
- **耗时:** 1746ms
- **工具调用:**
  - `ontologyQuery` ✓ 412ms

### ✅ contractFields_shouldCallOntologyQuery

- **输入:** C1767173898135504的合同字段
- **输出:** {"legalPhone":"","legalCertificateType":"1","companyName":"","authedAgentCertificateNoList":"[]","floorId":"0","companyAgentName":"","legalName":"","billCodeList":"[\"GBILL251231173422430009\"]","cookroomCnt":"1","agentCertificateNo":"","haveHouseProve":"1","firstLaneSyncAttachMap":"{}","contractObj...
- **耗时:** 1556ms
- **工具调用:**
  - `ontologyQuery` ✓ 412ms

---

