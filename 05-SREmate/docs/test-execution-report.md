# SREmate 集成测试执行报告

> 最后更新: 2026-03-15 11:01:57

> 运行命令: `./05-SREmate/scripts/run-integration-tests.sh`

---

## ContractOntologyIT

### ✅ budgetBill_shouldCallOntologyQuery

- **输入:** 826031111000001859的报价单
- **输出:** {"error":"503 Service Unavailable from POST http://utopia-atom.nrs-escrow.ttb.test.ke.com/api/budget-bill/list"}
- **耗时:** 2201ms
- **工具调用:**
  - `ontologyQuery` ✓ 66ms
  - `callPredefinedEndpoint` ✓ 64ms

---

