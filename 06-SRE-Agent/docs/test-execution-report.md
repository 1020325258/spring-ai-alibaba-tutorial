# SRE-Agent 端到端测试执行报告

> 最后更新: 2026-03-27 14:11:08

> 运行命令: `./scripts/run-integration-tests.sh`

---

## QueryAgentIT

### ✅ query_signable_order_info_by_order

- **输入:** 查询826031915000003212销售合同的可签约S单
- **输出:** {"queryEntity":"Order","queryValue":"826031915000003212","records":[{"projectOrderId":"826031915000003212","signableOrderInfos":[{"projectOrderId":"826031915000003212","companyName":"北京被窝装饰有限公司","companyCode":"V201601528","goodsInfo":"【复制】1750855380270","orderAmount":4.0,"orderCreateTime":"2026-03-1...
- **耗时:** 14233ms
- **工具调用:**
  - `ontologyQuery` ✓ 669ms

---

