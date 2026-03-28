# SRE-Agent 端到端测试执行报告

> 最后更新: 2026-03-28 10:39:00

> 运行命令: `./scripts/run-integration-tests.sh`

---

## SkillMechanismIT

### ✅ llm_should_call_read_skill_in_investigate_scenario

- **输入:** 排查合同C1767173898135504发起时缺少个性化报价的原因
- **输出:** > **[路由器]** → `investigate` 类型，路由至 **investigateAgent**

**[investigateAgent]**

**【数据查询 · 逐步排查过程】**

▶ 步骤1 - 加载排查 SOP：
  已加载技能 missing-personal-quote-diagnosis，排查路径共 4 步

▶ 步骤2 - 查询订单下的合同列表：
  执行：ontologyQuery(entity=Order, value=C1767173898135504, queryScope=Contract)
  发现：报错"无法识别的 value 格式: C1767...
- **耗时:** 11427ms
- **工具调用:**
  - `ontologyQuery` ✓ 384ms
  - `ontologyQuery` ✓ 6ms
  - `readSkill` ✓ 1ms

### ✅ readSkillTool_should_return_error_for_nonexistent

- **耗时:** 6ms
- **工具调用:** 无

### ✅ readSkillTool_should_load_missing_personal_quote

- **耗时:** 2ms
- **工具调用:** 无

### ✅ skillRegistry_should_be_injected

- **耗时:** 1ms
- **工具调用:** 无

### ✅ query_scenario_should_not_call_read_skill

- **输入:** 查询合同C1767173898135504的基本信息
- **输出:** > **[路由器]** → `query` 类型，路由至 **queryAgent**

**[queryAgent]**

```json{"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:2...
- **耗时:** 3045ms
- **工具调用:**
  - `ontologyQuery` ✓ 49ms

### ✅ llm_should_select_correct_skill_for_different_scenarios

- **输入:** 排查销售合同弹窗提示"请先完成报价"的原因
- **输出:** > **[路由器]** → `investigate` 类型，路由至 **investigateAgent**

**[investigateAgent]**

**【数据查询 · 逐步排查过程】**

▶ 步骤1 - 加载排查 SOP：
  已加载技能 sales-contract-sign-dialog-diagnosis，排查路径共 2 步

▶ 步骤2 - 验证用户描述（查询弹窗数据）：
  执行：ontologyQuery(entity=Order, value=825123110000002753, queryScope=SignableOrderInfo)
  发现：返回了 15...
- **耗时:** 8231ms
- **工具调用:**
  - `ontologyQuery` ✓ 633ms
  - `readSkill` ✓ 0ms

---

## QueryAgentIT

### ✅ query_contract_basic_info

- **输入:** 查询合同C1767173898135504的基本信息
- **输出:** > **[路由器]** → `query` 类型，路由至 **queryAgent**

**[queryAgent]**

```json{"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:2...
- **耗时:** 3152ms
- **工具调用:**
  - `ontologyQuery` ✓ 42ms

### ✅ query_formal_signable_order_info_by_order

- **输入:** 查询826031915000003212订单下正签合同的可签约S单
- **输出:** > **[路由器]** → `query` 类型，路由至 **queryAgent**

**[queryAgent]**

````json{"entity": "Order", "value": "826031915000003212", "queryScope": "FormalSignableOrderInfo"}
````
- **耗时:** 1020ms
- **工具调用:** 无

### ✅ query_contract_quotation_relation

- **输入:** 查询合同C1767173898135504的签约单据
- **输出:** > **[路由器]** → `query` 类型，路由至 **queryAgent**

**[queryAgent]**

```json{"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:2...
- **耗时:** 6880ms
- **工具调用:**
  - `ontologyQuery` ✓ 210ms

### ✅ query_signable_order_info_by_order

- **输入:** 查询826031915000003212销售合同的可签约S单
- **输出:** > **[路由器]** → `query` 类型，路由至 **queryAgent**

**[queryAgent]**

```
```json
{
  "entity": "Order",
  "queryScope": "SignableOrderInfo",
  "value": "826031915000003212"
}
```
```
- **耗时:** 1054ms
- **工具调用:** 无

### ✅ query_personal_quote

- **输入:** 查询合同C1767173898135504的个性化报价
- **输出:** > **[路由器]** → `query` 类型，路由至 **queryAgent**

**[queryAgent]**

```json{"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:2...
- **耗时:** 25812ms
- **工具调用:**
  - `ontologyQuery` ✓ 458ms

### ✅ query_contract_nodes

- **输入:** 查询合同C1767173898135504的节点
- **输出:** > **[路由器]** → `query` 类型，路由至 **queryAgent**

**[queryAgent]**

```json{"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:2...
- **耗时:** 4834ms
- **工具调用:**
  - `ontologyQuery` ✓ 68ms

### ✅ query_order_to_contracts

- **输入:** 查询订单825123110000002753下的合同
- **输出:** > **[路由器]** → `query` 类型，路由至 **queryAgent**

**[queryAgent]**

```json{
  "entity": "Order",
  "value": "825123110000002753",
  "queryScope": "Contract"
}
```
- **耗时:** 5975ms
- **工具调用:** 无

### ✅ query_order_with_multiple_scopes

- **输入:** 查询订单825123110000002753的合同和节点
- **输出:** > **[路由器]** → `query` 类型，路由至 **queryAgent**

**[queryAgent]**

```json{"queryEntity":"Order","queryValue":"825123110000002753","records":[{"projectOrderId":"825123110000002753","contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"platformInstanceId":0,"amount":413580.67,"ctime":"202...
- **耗时:** 13647ms
- **工具调用:**
  - `ontologyQuery` ✓ 89ms

---

## InvestigateAgentIT

### ✅ investigate_should_pass_correct_params

- **输入:** 排查825123110000002753订单的个性化报价问题
- **输出:** > **[路由器]** → `investigate` 类型，路由至 **investigateAgent**

**[investigateAgent]**

▶ 步骤2 - 查询订单的合同列表：
  执行：ontologyQuery(entity=Order, value=825123110000002753, queryScope=Contract)
  发现：订单825123110000002753下有3个合同，合同号分别为C1767150648920281、C1767150651718033和C1767150652428677。其中，合同C1767150648920281的金额为41...
- **耗时:** 13027ms
- **工具调用:**
  - `ontologyQuery` ✓ 43ms
  - `readSkill` ✓ 0ms

---

