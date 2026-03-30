# SRE-Agent 端到端测试执行报告

> 最后更新: 2026-03-29 22:33:20

> 运行命令: `./scripts/run-integration-tests.sh`

---

## SkillMechanismIT

### ✅ llm_should_call_read_skill_in_investigate_scenario

- **输入:** 销售合同C1767173898135504发起时弹窗提示"请先完成报价"
- **输出:** **【数据查询 · 逐步排查过程】**

▶ 步骤1 - 加载排查 SOP：
  已加载技能 sales-contract-sign-dialog-diagnosis，排查路径共 2 步

▶ 步骤2 - 验证用户描述（查询弹窗数据）：
  执行：ontologyQuery(entity=Order, value=C1767173898135504, queryScope=SignableOrderInfo)
  发现：无法识别的 value 格式: C1767173898135504，实体 Order 支持的格式: [^\\d{15,}$]
  判断：数据不符合预期，可能实体类型或值格式错误...
- **耗时:** 34092ms
- **工具调用:**
  - `ontologyQuery` ✓ 57ms
  - `ontologyQuery` ✓ 47ms
  - `ontologyQuery` ✓ 0ms
  - `ontologyQuery` ✓ 415ms
  - `ontologyQuery` ✓ 7ms
  - `readSkill` ✓ 0ms

### ✅ readSkillTool_should_return_error_for_nonexistent

- **耗时:** 1ms
- **工具调用:** 无

### ✅ readSkillTool_should_load_sales_contract_sign_dialog_diagnosis

- **耗时:** 1ms
- **工具调用:** 无

### ✅ skillRegistry_should_be_injected

- **耗时:** 1ms
- **工具调用:** 无

### ✅ query_scenario_should_not_call_read_skill

- **输入:** 查询合同C1767173898135504的基本信息
- **输出:** **【数据查询 · 逐步排查过程】**

▶ 步骤1 - 加载排查 SOP：
  已加载技能 sales-contract-sign-dialog-diagnosis，排查路径共 2 步

▶ 步骤2 - 验证用户描述（查询弹窗数据）：
  执行：ontologyQuery(entity=Order, value=C1767173898135504, queryScope=SignableOrderInfo)
  发现：无法识别的 value 格式: C1767173898135504，实体 Order 支持的格式: [^\\d{15,}$]
  判断：数据不符合预期，可能实体类型或值格式错误...
- **耗时:** 5319ms
- **工具调用:**
  - `ontologyQuery` ✓ 49ms

### ✅ llm_should_select_correct_skill_for_different_scenarios

- **输入:** 排查销售合同弹窗提示"请先完成报价"的原因
- **输出:** ```json
{"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20"}]}
```**【数据查询 · 逐步排查过程】**

▶ 步骤1 - 加载排查 SOP：
  已加载技能 sales-...
- **耗时:** 7708ms
- **工具调用:**
  - `ontologyQuery` ✓ 699ms
  - `ontologyQuery` ✓ 1ms
  - `readSkill` ✓ 0ms

---

## QueryAgentIT

### ✅ query_contract_basic_info

- **输入:** 查询合同C1767173898135504的基本信息
- **输出:** **【数据查询 · 逐步排查过程】**

▶ 步骤1 - 加载排查 SOP：
  已加载技能 sales-contract-sign-dialog-diagnosis，排查路径共 2 步

▶ 步骤2 - 验证用户描述（查询弹窗数据）：
  执行：ontologyQuery(entity=Order, value=825123110000002753, queryScope=SignableOrderInfo)
  发现：返回了 15 条可签约 S 单数据，包括定制柜、地板、木门推拉门等商品信息，且每条数据都包含 bindCode（S 单号）、goodsInfo（商品信息）和 orderCre...
- **耗时:** 3008ms
- **工具调用:**
  - `ontologyQuery` ✓ 50ms

### ✅ query_formal_signable_order_info_by_order

- **输入:** 查询826031915000003212订单下正签合同的可签约S单
- **输出:** ```json
{"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20"}]}
``````json
{"queryEntity":"Order","queryValue":"82603191...
- **耗时:** 12454ms
- **工具调用:**
  - `ontologyQuery` ✓ 1992ms

### ✅ query_contract_quotation_relation

- **输入:** 查询合同C1767173898135504的签约单据
- **输出:** ```json
{"queryEntity":"Order","queryValue":"826031915000003212","records":[{"projectOrderId":"826031915000003212","formalSignableOrderInfos":[{"projectOrderId":"826031915000003212","companyName":"北京被窝装饰有限公司","companyCode":"V201601528","goodsInfo":"【复制】1750855380270","orderAmount":4.0,"orderCreateTi...
- **耗时:** 7678ms
- **工具调用:**
  - `ontologyQuery` ✓ 82ms

### ✅ query_signable_order_info_by_order

- **输入:** 查询826031915000003212销售合同的可签约S单
- **输出:** ```json
{"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contractQuotationRelations":[{"contractCode":"C17671738981...
- **耗时:** 12177ms
- **工具调用:**
  - `ontologyQuery` ✓ 630ms

### ✅ query_personal_quote

- **输入:** 查询合同C1767173898135504的个性化报价
- **输出:** ```json
{"queryEntity":"Order","queryValue":"826031915000003212","records":[{"projectOrderId":"826031915000003212","signableOrderInfos":[{"projectOrderId":"826031915000003212","companyName":"北京被窝装饰有限公司","companyCode":"V201601528","goodsInfo":"【复制】1750855380270","orderAmount":4.0,"orderCreateTime":"2...
- **耗时:** 40315ms
- **工具调用:**
  - `ontologyQuery` ✓ 1021ms
  - `queryPersonalQuote` ✓ 1ms

### ✅ query_contract_nodes

- **输入:** 查询合同C1767173898135504的节点
- **输出:** ```json
{"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contractQuotationRelations":[{"contractCode":"C17671738981...
- **耗时:** 5645ms
- **工具调用:**
  - `ontologyQuery` ✓ 77ms

### ✅ query_order_to_contracts

- **输入:** 查询订单825123110000002753下的合同
- **输出:** ```json
{"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contractNodes":[{"nodeType":7,"fireTime":"2025-12-31 17:38...
- **耗时:** 93091ms
- **工具调用:**
  - `ontologyQuery` ✓ 60ms

### ✅ query_order_with_multiple_scopes

- **输入:** 查询订单825123110000002753的合同和节点
- **输出:** ```json
{"queryEntity":"Order","queryValue":"825123110000002753","records":[{"projectOrderId":"825123110000002753","contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"platformInstanceId":0,"amount":413580.67,"ctime":"2025-12-31 11:10:48"},{"contractCode":"C1767150651718033","type":...
- **耗时:** 10104ms
- **工具调用:**
  - `ontologyQuery` ✓ 101ms

---

## InvestigateAgentIT

### ✅ investigate_should_pass_correct_params

- **输入:** 排查825123110000002753订单的合同弹窗提示请先完成报价
- **输出:** ```json
{"queryEntity":"Order","queryValue":"825123110000002753","records":[{"projectOrderId":"825123110000002753","contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"platformInstanceId":0,"amount":413580.67,"ctime":"2025-12-31 11:10:48","contractNodes":[{"nodeType":7,"fireTime":"2...
- **耗时:** 8879ms
- **工具调用:**
  - `ontologyQuery` ✓ 604ms
  - `readSkill` ✓ 0ms

---

