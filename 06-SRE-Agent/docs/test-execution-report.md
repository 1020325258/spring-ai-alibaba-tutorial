# SRE-Agent 端到端测试执行报告

> 最后更新: 2026-03-27 18:40:21

> 运行命令: `./scripts/run-integration-tests.sh`

---

## InvestigateAgentIT

### ✅ investigate_sales_contract_sign_dialog_no_quote_keyword

- **输入:** 订单825123110000002753的销售合同发起提示无定软电报价
- **输出:** **【数据查询】** 调用了 `ontologyQuery` 查询订单 825123110000002753 的可签约S单（SignableOrderInfo）和所有 S 单（SubOrder）。

**【分析】** 关键发现：
- **弹窗数据（SignableOrderInfo）**：存在 15 条可签约 S 单，例如 S14251231110001838、S14251231110002125 等，且这些 S 单的状态均为有效状态（status: 1000）。
- **S 单情况（SubOrder）**：订单下共有 15 条 S 单，全部状态为 1000（有效状态），没有 S 单处于取消或...
- **耗时:** 7715ms
- **工具调用:**
  - `ontologyQuery` ✓ 158ms
  - `ontologyQuery` ✓ 611ms
  - `readSkill` ✓ 0ms

---

