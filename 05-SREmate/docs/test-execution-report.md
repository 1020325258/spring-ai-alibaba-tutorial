# SREmate 集成测试执行报告

> 最后更新: 2026-03-16 00:01:30

> 运行命令: `./05-SREmate/scripts/run-integration-tests.sh`

---

## ContractOntologyIT

### ✅ orderContract_signedObjectsAndNodes_shouldCallOntologyQuery

- **输入:** 825123110000002753合同签约单据和节点
- **输出:** {"queryEntity":"Order","queryValue":"825123110000002753","records":[{"projectOrderId":"825123110000002753","contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"platformInstanceId":0,"amount":413580.67,"ctime":"2025-12-31 11:10:48","signedObjects":[],"nodes":[{"nodeType":7,"fireTime"...
- **耗时:** 2572ms
- **工具调用:**
  - `ontologyQuery` ✓ 489ms

---

