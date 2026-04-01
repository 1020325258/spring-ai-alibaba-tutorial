## ADDED Requirements

### Requirement: 诊断 Skill 文件存在且内容完整
系统 SHALL 在 `skills/sales-contract-sign-dialog-diagnosis/SKILL.md` 提供诊断 skill，包含：触发条件、两步查询路径、有效 S 单过滤规则（状态非 9001/9002）、决策矩阵、输出格式。

#### Scenario: Skill 文件可被 ReadSkillTool 读取
- **WHEN** agent 调用 `readSkill("sales-contract-sign-dialog-diagnosis")`
- **THEN** 返回完整 skill 内容，包含触发条件和排查步骤

### Requirement: Agent 按 Skill 两步诊断并输出结论
当用户描述合同弹窗提示"请先完成报价"时，agent SHALL 先查弹窗数据，再查有效 S 单，最终输出三种结论之一。

#### Scenario: 弹窗无数据且无有效 S 单 → 用户未下单
- **WHEN** 用户说"826032417000002739 订单的销售合同弹窗提示请先完成报价"
- **THEN** agent 依次调用 ontologyQuery 查 ContractSignableOrderInfo（空）、查 SubOrder（无有效状态），输出结论"用户尚未下单"

#### Scenario: 弹窗无数据但有有效 S 单 → 接口异常
- **WHEN** 弹窗查询返回空，但订单下存在状态非 9001/9002 的 S 单
- **THEN** agent 输出结论"弹窗接口逻辑异常，建议人工介入"

#### Scenario: 弹窗有数据 → 确认描述
- **WHEN** 弹窗查询返回有数据
- **THEN** agent 输出"弹窗可正常展示数据，请确认用户描述的具体场景"

### Requirement: 有效 S 单过滤规则
Skill SHALL 明确说明有效 S 单的定义：状态码不在 [9001, 9002] 范围内的 S 单视为有效。

#### Scenario: 过滤无效 S 单
- **WHEN** SubOrder 查询结果中包含状态为 9001 或 9002 的记录
- **THEN** agent 将这些记录视为无效 S 单，不计入"有有效 S 单"的判断
