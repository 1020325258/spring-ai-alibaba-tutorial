# SREmate 集成测试执行报告

> 最后更新: 2026-03-11 16:50:36

> 运行命令: `./05-SREmate/scripts/run-integration-tests.sh`

---

## BudgetBillToolIT

### ✅ queryBudgetBill_byOrderId_shouldReturnBillFields

- **输入:** 826031111000001859的报价单
- **输出:** {"decorateBudgetList":[{"billType":100,"billTypeDesc":"标准报价","statusDesc":"正式版","billCode":"GBILL260311115521050009","originalBillCode":"GBILL260311115521050009","billTotalAmount":409969.9,"ctime":"2026-03-11 11:55:21"}],"personalBudgetList":[{"billType":103,"billTypeDesc":"协同报价","statusDesc":"已提交",...（截断）
- **耗时:** 10217ms
- **工具调用:**
  - `queryBudgetBillList` ✓ 2310ms
  - `callPredefinedEndpoint` ✓ 2309ms

### ✅ queryBudgetBill_naturalLanguage_shouldRecognizeIntent

- **输入:** 查询826031111000001859的报价单列表
- **输出:** {"decorateBudgetList":[{"billType":100,"billTypeDesc":"标准报价","statusDesc":"正式版","billCode":"GBILL260311115521050009","originalBillCode":"GBILL260311115521050009","billTotalAmount":409969.9,"ctime":"2026-03-11 11:55:21"}],"personalBudgetList":[{"billType":103,"billTypeDesc":"协同报价","statusDesc":"已提交",...（截断）
- **耗时:** 12445ms
- **工具调用:**
  - `queryBudgetBillList` ✓ 5441ms
  - `callPredefinedEndpoint` ✓ 5439ms

---

