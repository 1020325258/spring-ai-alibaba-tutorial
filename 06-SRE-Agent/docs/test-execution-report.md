# SRE-Agent 端到端测试执行报告

> 最后更新: 2026-03-28 10:28:37

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
  发现：返回的合同列表为空，未找到与订单 C176717...
- **耗时:** 7358ms
- **工具调用:**
  - `readSkill` ✓ 0ms

### ✅ readSkillTool_should_return_error_for_nonexistent

- **耗时:** 2ms
- **工具调用:** 无

### ✅ readSkillTool_should_load_missing_personal_quote

- **耗时:** 1ms
- **工具调用:** 无

### ✅ skillRegistry_should_be_injected

- **耗时:** 0ms
- **工具调用:** 无

### ✅ query_scenario_should_not_call_read_skill

- **输入:** 查询合同C1767173898135504的基本信息
- **输出:** > **[路由器]** → `query` 类型，路由至 **queryAgent**

**[queryAgent]**

```json{"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:2...
- **耗时:** 3638ms
- **工具调用:**
  - `ontologyQuery` ✓ 356ms

### ✅ llm_should_select_correct_skill_for_different_scenarios

- **输入:** 排查销售合同弹窗提示"请先完成报价"的原因
- **输出:** > **[路由器]** → `investigate` 类型，路由至 **investigateAgent**

**[investigateAgent]**

**【数据查询 · 逐步排查过程】**

▶ 步骤1 - 加载排查 SOP：
  已加载技能 sales-contract-sign-dialog-diagnosis，排查路径共 2 步

▶ 步骤2 - 验证用户描述（查询弹窗数据）：
  执行：ontologyQuery(entity=Order, value=825123110000002753, queryScope=SignableOrderInfo)
  发现：返回的 `s...
- **耗时:** 7221ms
- **工具调用:**
  - `ontologyQuery` ✓ 844ms
  - `ontologyQuery` ✓ 8ms
  - `readSkill` ✓ 0ms

---

## QueryAgentIT

### ✅ query_contract_basic_info

- **输入:** 查询合同C1767173898135504的基本信息
- **输出:** > **[路由器]** → `query` 类型，路由至 **queryAgent**

**[queryAgent]**

```json{"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:2...
- **耗时:** 2791ms
- **工具调用:**
  - `ontologyQuery` ✓ 43ms

### ✅ query_formal_signable_order_info_by_order

- **输入:** 查询826031915000003212订单下正签合同的可签约S单
- **输出:** > **[路由器]** → `query` 类型，路由至 **queryAgent**

**[queryAgent]**

```json{"queryEntity":"Order","queryValue":"826031915000003212","records":[{"projectOrderId":"826031915000003212","formalSignableOrderInfos":[{"projectOrderId":"826031915000003212","companyName":"北京被窝装饰有限公司","companyCode":"V201601528","g...
- **耗时:** 12407ms
- **工具调用:**
  - `ontologyQuery` ✓ 1794ms

### ✅ query_contract_quotation_relation

- **输入:** 查询合同C1767173898135504的签约单据
- **输出:** > **[路由器]** → `query` 类型，路由至 **queryAgent**

**[queryAgent]**

```json{"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:2...
- **耗时:** 6891ms
- **工具调用:**
  - `ontologyQuery` ✓ 79ms

### ✅ query_signable_order_info_by_order

- **输入:** 查询826031915000003212销售合同的可签约S单
- **输出:** > **[路由器]** → `query` 类型，路由至 **queryAgent**

**[queryAgent]**

```json{"queryEntity":"Order","queryValue":"826031915000003212","records":[{"projectOrderId":"826031915000003212","signableOrderInfos":[{"projectOrderId":"826031915000003212","companyName":"北京被窝装饰有限公司","companyCode":"V201601528","goodsIn...
- **耗时:** 10481ms
- **工具调用:**
  - `ontologyQuery` ✓ 640ms

### ✅ query_personal_quote

- **输入:** 查询合同C1767173898135504的个性化报价
- **输出:** > **[路由器]** → `query` 类型，路由至 **queryAgent**

**[queryAgent]**

```json{"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:2...
- **耗时:** 28181ms
- **工具调用:**
  - `ontologyQuery` ✓ 466ms
  - `queryPersonalQuote` ✓ 1ms

### ✅ query_contract_nodes

- **输入:** 查询合同C1767173898135504的节点
- **输出:** > **[路由器]** → `query` 类型，路由至 **queryAgent**

**[queryAgent]**

```json{"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:2...
- **耗时:** 4611ms
- **工具调用:**
  - `ontologyQuery` ✓ 68ms

### ✅ query_order_to_contracts

- **输入:** 查询订单825123110000002753下的合同
- **输出:** > **[路由器]** → `query` 类型，路由至 **queryAgent**

**[queryAgent]**

```json{"queryEntity":"Order","queryValue":"825123110000002753","records":[{"projectOrderId":"825123110000002753","contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"platformInstanceId":0,"amount":413580.67,"ctime":"202...
- **耗时:** 4564ms
- **工具调用:**
  - `ontologyQuery` ✓ 41ms

### ✅ query_order_with_multiple_scopes

- **输入:** 查询订单825123110000002753的合同和节点
- **输出:** > **[路由器]** → `query` 类型，路由至 **queryAgent**

**[queryAgent]**

```json{"queryEntity":"Order","queryValue":"825123110000002753","records":[{"projectOrderId":"825123110000002753","contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"platformInstanceId":0,"amount":413580.67,"ctime":"202...
- **耗时:** 9341ms
- **工具调用:**
  - `ontologyQuery` ✓ 85ms

---

## InvestigateAgentIT

### ✅ investigate_should_pass_correct_params

- **输入:** 排查825123110000002753订单的个性化报价问题
- **输出:** > **[路由器]** → `investigate` 类型，路由至 **investigateAgent**

**[investigateAgent]**

▶ 步骤2 - 查询订单的合同列表：
  执行：ontologyQuery(entity=Order, value=825123110000002753, queryScope=Contract)
  发现：订单825123110000002753有3个合同，分别是C1767150648920281（类型3，状态8，金额413580.67）、C1767150651718033（类型7，状态8，金额0.00）、C176715065242...
- **耗时:** 8127ms
- **工具调用:**
  - `ontologyQuery` ✓ 47ms
  - `readSkill` ✓ 0ms

---

