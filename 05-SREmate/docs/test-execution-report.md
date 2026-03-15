# SREmate 集成测试执行报告

> 最后更新: 2026-03-15 10:20:53

> 运行命令: `./05-SREmate/scripts/run-integration-tests.sh`

---

## ContractOntologyIT

### ✅ orderContract_allData_shouldCallOntologyQuery

- **输入:** 825123110000002753下的合同数据
- **输出:** {"queryEntity":"Order","queryValue":"825123110000002753","contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"amount":413580.67,"platformInstanceId":0,"ctime":"2025-12-31 11:10:48","nodes":[{"nodeType":7,"fireTime":"2025-12-31 11:10:42"},{"nodeType":8,"fireTime":"2025-12-31 11:10:42...
- **耗时:** 3031ms
- **工具调用:**
  - `ontologyQuery` ✓ 636ms

---

