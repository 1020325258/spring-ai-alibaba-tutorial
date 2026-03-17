# SREmate 集成测试执行报告

> 最后更新: 2026-03-17 21:25:07

> 运行命令: `./05-SREmate/scripts/run-integration-tests.sh`

---

## ContractOntologyIT

### ✅ contractForm_shouldCallOntologyQuery

- **输入:** C1767173898135504的版式
- **输出:** {"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","form":[{"contractCode":"C1767173898135504","platformInstanceId":0,...
- **耗时:** 2018ms
- **工具调用:**
  - `ontologyQuery` ✓ 472ms
  - `callPredefinedEndpoint` ✓ 87ms

---

