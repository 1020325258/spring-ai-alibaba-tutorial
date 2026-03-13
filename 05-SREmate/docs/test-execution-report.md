# SREmate 集成测试执行报告

> 最后更新: 2026-03-14 00:08:16

> 运行命令: `./05-SREmate/scripts/run-integration-tests.sh`

---

## OntologyGoldenSetIT

### ✅ goldenSet_subOrderViaQuote

- **输入:** 826031111000001859报价单下的S单
- **输出:** 请提供具体的订单号（纯数字格式，如 826031111000001859）以查询对应的子单信息。
- **耗时:** 2880ms
- **工具调用:**
  - `querySubOrderInfo` ✓ 2ms
  - `callPredefinedEndpoint` ✓ 1ms

### ✅ goldenSet_contractSignedObjects

- **输入:** 825123110000002753合同的签约单据
- **输出:** {"contract_code":"C1767150652428677","bill_code":"GBILL251231111103650005","company_code":"V201800236","bind_type":0,"status":1,"ctime":"2025-12-31 11:11:11","mtime":"2025-12-31 11:11:11"}
- **耗时:** 6021ms
- **工具调用:**
  - `queryContractSignedObjects` ✓ 51ms
  - `queryContractSignedObjects` ✓ 50ms
  - `queryContractSignedObjects` ✓ 54ms
  - `queryContractsByOrderId` ✓ 665ms

### ✅ goldenSet_contractBasic

- **输入:** 825123110000002753下的合同基本信息
- **输出:** {   "contractInfo": [     {       "contractCode": "C1767150648920281",       "type": 3,       "status": 8,       "amount": 413580.67,       "platformInstanceId": 0,       "projectOrderId": "825123110000002753",       "ctime": "2025-12-31 11:10:48",       "nodes": [         {           "nodeType": 7,...（截断）
- **耗时:** 15201ms
- **工具调用:**
  - `queryContractNodes` ✓ 50ms
  - `queryContractNodes` ✓ 49ms
  - `queryContractNodes` ✓ 49ms
  - `queryContractBasic` ✓ 51ms
  - `queryContractBasic` ✓ 48ms
  - `queryContractBasic` ✓ 48ms
  - `queryContractsByOrderId` ✓ 150ms

### ✅ goldenSet_contractNodes

- **输入:** 825123110000002753下的合同节点
- **输出:** {"nodeType":7,"fireTime":"2025-12-31 11:10:42"},{"nodeType":8,"fireTime":"2025-12-31 11:10:42"},{"nodeType":9,"fireTime":"2025-12-31 11:10:54"},{"nodeType":2,"fireTime":"2025-12-31 11:10:54"}
- **耗时:** 3999ms
- **工具调用:**
  - `queryContractNodes` ✓ 50ms
  - `queryContractsByOrderId` ✓ 142ms

### ✅ goldenSet_budgetBill

- **输入:** 826031111000001859的报价单
- **输出:** {"decorateBudgetList":[{"billType":100,"billTypeDesc":"标准报价","statusDesc":"正式版","billCode":"GBILL260311115521050009","originalBillCode":"GBILL260311115521050009","billTotalAmount":409969.9,"ctime":"2026-03-11 11:55:21","subOrders":[]}],"personalBudgetList":[{"billType":103,"billTypeDesc":"协同报价","sta...（截断）
- **耗时:** 18050ms
- **工具调用:**
  - `queryBudgetBillList` ✓ 3082ms
  - `callPredefinedEndpoint` ✓ 2539ms

---

