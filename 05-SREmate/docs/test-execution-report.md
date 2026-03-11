# SREmate 集成测试执行报告

> 最后更新: 2026-03-11 15:59:13

> 运行命令: `./05-SREmate/scripts/run-integration-tests.sh`

---

## BudgetBillToolIT

### ✅ queryBudgetBill_byOrderId_shouldReturnBillFields

- **输入:** 826031111000001859的报价单
- **输出:** {"decorateBudgetList":[{"billType":100,"billTypeDesc":"标准报价","statusDesc":"正式版","billCode":"GBILL260311115521050009","originalBillCode":"GBILL260311115521050009"}],"personalBudgetList":[{"billType":103,"billTypeDesc":"协同报价","statusDesc":"已提交","billCode":"GBILL260311140550730001","originalBillCode":"...（截断）
- **耗时:** 6944ms
- **工具调用:**
  - `queryBudgetBillList` ✓ 2073ms
  - `callPredefinedEndpoint` ✓ 2072ms

### ✅ queryBudgetBill_naturalLanguage_shouldRecognizeIntent

- **输入:** 查询826031111000001859的报价单列表
- **输出:** {"decorateBudgetList":[{"billType":100,"billTypeDesc":"标准报价","statusDesc":"正式版","billCode":"GBILL260311115521050009","originalBillCode":"GBILL260311115521050009"}],"personalBudgetList":[{"billType":103,"billTypeDesc":"协同报价","statusDesc":"已提交","billCode":"GBILL260311140550730001","originalBillCode":"...（截断）
- **耗时:** 7528ms
- **工具调用:**
  - `queryBudgetBillList` ✓ 2850ms
  - `callPredefinedEndpoint` ✓ 2831ms

---

