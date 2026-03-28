# SRE-Agent 端到端测试执行报告

> 最后更新: 2026-03-28 11:04:45

> 运行命令: `./scripts/run-integration-tests.sh`

---

## QueryAgentIT

### ✅ query_order_to_contracts

- **输入:** 查询订单825123110000002753下的合同
- **输出:** > **[路由器]** → `query` 类型，路由至 **queryAgent**

**[queryAgent]**

```json
{"queryEntity":"Order","queryValue":"825123110000002753","records":[{"projectOrderId":"825123110000002753","contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"platformInstanceId":0,"amount":413580.67,"ctime":"20...
- **耗时:** 6135ms
- **工具调用:**
  - `ontologyQuery` ✓ 359ms

---

