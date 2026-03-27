# SRE-Agent 端到端测试执行报告

> 最后更新: 2026-03-27 11:33:07

> 运行命令: `./scripts/run-integration-tests.sh`

---

## InvestigateAgentIT

### ✅ investigate_should_pass_correct_params

- **输入:** 排查825123110000002753订单的个性化报价问题
- **输出:** **【数据查询】** 调用 `ontologyQuery` 查询订单 825123110000002753 的个性化报价数据。

**【分析】** 关键发现：
- 订单下有 3 份合同，其中合同 C1767150652428677（类型8）关联了 3 份报价单
- GBILL251231111103650005：个性化报价 200 元，createTime 正常，有报价文件 ✓
- GBILL251231111320240006：个性化报价 **0 元**，**createTime 为 null** ⚠️
- GBILL251231105150410004：个性化报价 200 元，报价单状态为...
- **耗时:** 6001ms
- **工具调用:**
  - `ontologyQuery` ✓ 642ms

---

