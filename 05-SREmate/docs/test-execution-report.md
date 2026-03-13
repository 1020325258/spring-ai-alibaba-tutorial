# SREmate 集成测试执行报告

> 最后更新: 2026-03-14 00:03:31

> 运行命令: `./05-SREmate/scripts/run-integration-tests.sh`

---

## OntologyGoldenSetIT

### ✅ goldenSet_subOrderViaQuote

- **输入:** 826031111000001859报价单下的S单
- **输出:** {"code":2000,"message":"操作成功","data":[{"orderNo":"S14260311120002395","status":900,"scmFullNo":"","projectChangeNo":"","retailSaleProjectNo":"826031111000004296","mdmCode":"V201800236","orderCreateTime":1773202491000,"dueAmount":840.00,"compositOrderNo":"CT202603110000127636"},{"orderNo":"S152603111...（截断）
- **耗时:** 18534ms
- **工具调用:**
  - `querySubOrderInfo` ✓ 197ms
  - `callPredefinedEndpoint` ✓ 195ms

### ✅ goldenSet_contractSignedObjects

- **输入:** 825123110000002753合同的签约单据
- **输出:** 该订单号 `825123110000002753` 没有对应的合同签约单据。请确认订单号是否正确，或检查是否有其他相关合同编号需要查询。
- **耗时:** 2041ms
- **工具调用:**
  - `queryContractSignedObjects` ✓ 404ms

### ✅ goldenSet_contractBasic

- **输入:** 825123110000002753下的合同基本信息
- **输出:** {"contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"amount":413580.67,"platformInstanceId":0,"ctime":"2025-12-31 11:10:48","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 11:10:42"},{"nodeType":8,"fireTime":"2025-12-31 11:10:42"},{"nodeType":9,"fireTime":"2025-12-31 11:10:54...（截断）
- **耗时:** 18121ms
- **工具调用:**
  - `queryContractsByOrderId` ✓ 169ms

### ✅ goldenSet_contractNodes

- **输入:** 825123110000002753下的合同节点
- **输出:** {   "contractCode": "825123110000002753",   "contracts": [     {       "contractCode": "C1767150648920281",       "type": 3,       "status": 8,       "amount": 413580.67,       "platformInstanceId": 0,       "ctime": "2025-12-31 11:10:48",       "contract_node": [         {           "nodeType": 7, ...（截断）
- **耗时:** 24293ms
- **工具调用:**
  - `queryContractData` ✓ 47ms
  - `queryContractData` ✓ 48ms
  - `queryContractData` ✓ 53ms
  - `queryContractsByOrderId` ✓ 153ms
  - `queryContractData` ✓ 5ms

### ✅ goldenSet_budgetBill

- **输入:** 826031111000001859的报价单
- **输出:** 该订单下没有报价单数据。
- **耗时:** 15506ms
- **工具调用:**
  - `queryBudgetBillList` ✓ 14025ms
  - `callPredefinedEndpoint` ✓ 14023ms

---

