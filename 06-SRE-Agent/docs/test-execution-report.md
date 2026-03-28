# SRE-Agent 端到端测试执行报告

> 最后更新: 2026-03-28 01:43:12

> 运行命令: `./scripts/run-integration-tests.sh`

---

## SkillMechanismIT

### ✅ llm_should_call_read_skill_in_investigate_scenario

- **输入:** 排查合同C1767173898135504发起时缺少个性化报价的原因
- **输出:** **【数据查询】** 调用了 ontologyQuery，获取了 合同C1767173898135504的签约单据及关联的个性化报价数据

**【分析】** 关键发现：合同C1767173898135504存在多个签约单据，其中两个签约单据绑定了个性化报价，但有一个签约单据（billCode=GBILL251231174049710011）的个性化报价金额为0，且createTime字段为空，可能表示该报价未正确生成或绑定。

**【结论】** 问题原因在于某个签约单据的个性化报价金额为0，且创建时间为空，表明该报价可能未正确生成或绑定。

**【建议】** 需要检查签约单据GBILL2512...
- **耗时:** 9825ms
- **工具调用:**
  - `ontologyQuery` ✓ 455ms
  - `ontologyQuery` ✓ 1ms
  - `ontologyQuery` ✓ 393ms
  - `ontologyQuery` ✓ 7ms
  - `readSkill` ✓ 0ms

### ✅ readSkillTool_should_return_error_for_nonexistent

- **耗时:** 3ms
- **工具调用:** 无

### ✅ readSkillTool_should_load_missing_personal_quote

- **耗时:** 1ms
- **工具调用:** 无

### ✅ skillRegistry_should_be_injected

- **耗时:** 0ms
- **工具调用:** 无

### ✅ query_scenario_should_not_call_read_skill

- **输入:** 查询合同C1767173898135504的基本信息
- **输出:** {"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20"}]}
- **耗时:** 2714ms
- **工具调用:**
  - `ontologyQuery` ✓ 47ms

### ✅ llm_should_select_correct_skill_for_different_scenarios

- **输入:** 排查销售合同弹窗提示"请先完成报价"的原因
- **输出:** **【数据查询】** 调用了 ontologyQuery，获取了订单 825123110000002753 下的可签约 S 单信息

**【分析】** 根据返回的数据，该订单下存在多个可签约 S 单（如 S14251231110001838、S14251231110002125 等），且这些 S 单的状态均未在 [9001, 9002] 范围内，表明它们是有效的 S 单。

**【结论】** 用户描述不属实，系统弹窗数据正常，存在多条可签约 S 单，用户所描述的问题在当前数据中不存在。

**【建议】** 无需进一步操作，告知用户系统弹窗数据正常。
- **耗时:** 4797ms
- **工具调用:**
  - `ontologyQuery` ✓ 757ms
  - `readSkill` ✓ 0ms

---

## QueryAgentIT

### ✅ query_contract_basic_info

- **输入:** 查询合同C1767173898135504的基本信息
- **输出:** {"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20"}]}
- **耗时:** 3049ms
- **工具调用:**
  - `ontologyQuery` ✓ 45ms

### ✅ query_formal_signable_order_info_by_order

- **输入:** 查询826031915000003212订单下正签合同的可签约S单
- **输出:** {"queryEntity":"Order","queryValue":"826031915000003212","records":[{"projectOrderId":"826031915000003212","formalSignableOrderInfos":[{"projectOrderId":"826031915000003212","companyName":"北京被窝装饰有限公司","companyCode":"V201601528","goodsInfo":"【复制】1750855380270","orderAmount":4.0,"orderCreateTime":"202...
- **耗时:** 14286ms
- **工具调用:**
  - `ontologyQuery` ✓ 4748ms

### ✅ query_contract_quotation_relation

- **输入:** 查询合同C1767173898135504的签约单据
- **输出:** {"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contractQuotationRelations":[{"contractCode":"C1767173898135504","...
- **耗时:** 6537ms
- **工具调用:**
  - `ontologyQuery` ✓ 72ms

### ✅ query_signable_order_info_by_order

- **输入:** 查询826031915000003212销售合同的可签约S单
- **输出:** {"queryEntity":"Order","queryValue":"826031915000003212","records":[{"projectOrderId":"826031915000003212","signableOrderInfos":[{"projectOrderId":"826031915000003212","companyName":"北京被窝装饰有限公司","companyCode":"V201601528","goodsInfo":"【复制】1750855380270","orderAmount":4.0,"orderCreateTime":"2026-03-1...
- **耗时:** 8459ms
- **工具调用:**
  - `ontologyQuery` ✓ 609ms

### ✅ query_personal_quote

- **输入:** 查询合同C1767173898135504的个性化报价
- **输出:** {"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contractQuotationRelations":[{"contractCode":"C1767173898135504","...
- **耗时:** 25600ms
- **工具调用:**
  - `ontologyQuery` ✓ 467ms
  - `queryPersonalQuote` ✓ 1ms

### ✅ query_contract_nodes

- **输入:** 查询合同C1767173898135504的节点
- **输出:** {"queryEntity":"Contract","queryValue":"C1767173898135504","records":[{"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contractNodes":[{"nodeType":7,"fireTime":"2025-12-31 17:38:01"},{"...
- **耗时:** 4093ms
- **工具调用:**
  - `ontologyQuery` ✓ 76ms

### ✅ query_order_to_contracts

- **输入:** 查询订单825123110000002753下的合同
- **输出:** {"queryEntity":"Order","queryValue":"825123110000002753","records":[{"projectOrderId":"825123110000002753","contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"platformInstanceId":0,"amount":413580.67,"ctime":"2025-12-31 11:10:48"},{"contractCode":"C1767150651718033","type":7,"statu...
- **耗时:** 4679ms
- **工具调用:**
  - `ontologyQuery` ✓ 45ms

### ✅ query_order_with_multiple_scopes

- **输入:** 查询订单825123110000002753的合同和节点
- **输出:** {"queryEntity":"Order","queryValue":"825123110000002753","records":[{"projectOrderId":"825123110000002753","contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"platformInstanceId":0,"amount":413580.67,"ctime":"2025-12-31 11:10:48","contractNodes":[{"nodeType":7,"fireTime":"2025-12-3...
- **耗时:** 9592ms
- **工具调用:**
  - `ontologyQuery` ✓ 100ms

---

## InvestigateAgentIT

### ✅ investigate_should_pass_correct_params

- **输入:** 排查825123110000002753订单的个性化报价问题
- **输出:** **【数据查询】** 调用了 ontologyQuery，获取了 ContractQuotationRelation 的数据

**【分析】** 关键发现：合同 C1767150648920281 的签约单据为空，说明没有关联的签约单据。这表明在合同和个性化报价之间存在断点。

**【结论】** 合同与签约单据之间没有建立关联，导致个性化报价缺失。

**【建议】** 需要检查合同 C1767150648920281 是否生成了签约单据，并确保其与个性化报价正确绑定。
- **耗时:** 6146ms
- **工具调用:**
  - `ontologyQuery` ✓ 46ms
  - `ontologyQuery` ✓ 90ms
  - `ontologyQuery` ✓ 63ms
  - `readSkill` ✓ 0ms

---

