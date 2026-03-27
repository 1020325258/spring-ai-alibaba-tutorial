# SRE-Agent 端到端测试执行报告

> 最后更新: 2026-03-27 20:43:24

> 运行命令: `./scripts/run-integration-tests.sh`

---

## QueryAgentIT

### ✅ query_formal_signable_order_info_by_order

- **输入:** 查询826031915000003212订单下正签合同的可签约S单
- **输出:** {"queryEntity":"Order","queryValue":"826031915000003212","records":[{"projectOrderId":"826031915000003212","formalSignableOrderInfos":[{"projectOrderId":"826031915000003212","companyName":"北京被窝装饰有限公司","companyCode":"V201601528","goodsInfo":"【复制】1750855380270","orderAmount":4.0,"orderCreateTime":"202...
- **耗时:** 13797ms
- **工具调用:**
  - `ontologyQuery` ✓ 2017ms

---

