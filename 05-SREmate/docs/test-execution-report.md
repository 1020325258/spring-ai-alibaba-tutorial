# SREmate 集成测试执行报告

> 最后更新: 2026-03-11 17:15:35

> 运行命令: `./05-SREmate/scripts/run-integration-tests.sh`

---

## BudgetBillToolIT

### ✅ queryBudgetBill_byOrderId_shouldReturnBillFieldsWithSubOrders

- **输入:** 826031111000001859的报价单
- **输出:** {"decorateBudgetList":[{"billType":100,"billTypeDesc":"标准报价","statusDesc":"正式版","billCode":"GBILL260311115521050009","originalBillCode":"GBILL260311115521050009","billTotalAmount":409969.9,"ctime":"2026-03-11 11:55:21","subOrders":[{"orderNo":"S14260311120002395","projectChangeNo":"","mdmCode":"V201...（截断）
- **耗时:** 32202ms
- **工具调用:**
  - `queryBudgetBillList` ✓ 2592ms
  - `callPredefinedEndpoint` ✓ 2204ms

### ✅ queryBudgetBill_naturalLanguage_shouldRecognizeIntent

- **输入:** 查询826031111000001859的报价单列表
- **输出:** {"decorateBudgetList":[{"billType":100,"billTypeDesc":"标准报价","statusDesc":"正式版","billCode":"GBILL260311115521050009","originalBillCode":"GBILL260311115521050009","billTotalAmount":409969.9,"ctime":"2026-03-11 11:55:21","subOrders":[{"orderNo":"S14260311120002395","projectChangeNo":"","mdmCode":"V201...（截断）
- **耗时:** 30098ms
- **工具调用:**
  - `queryBudgetBillList` ✓ 3364ms
  - `callPredefinedEndpoint` ✓ 2490ms

---

