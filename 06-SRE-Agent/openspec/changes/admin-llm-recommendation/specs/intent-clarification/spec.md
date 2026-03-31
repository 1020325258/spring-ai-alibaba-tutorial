## ADDED Requirements

### Requirement: Admin 节点提供智能能力推荐
当 RouterNode 无法匹配用户意图到具体 Skill 或查询实体时，系统 SHALL 调用 AdminNode，AdminNode SHALL 通过 LLM 分析用户输入和可用能力列表，输出语义相似的能力推荐。

#### Scenario: 用户输入模糊但语义关联已知能力
- **WHEN** 用户输入"826033014000004927 可签约S单"
- **AND** RouterNode 路由到 admin
- **THEN** AdminNode 返回"您可能想问：1. 查询销售合同弹窗可签约S单（PersonalSignableOrderInfo）"

#### Scenario: 用户输入口语化表达
- **WHEN** 用户输入"这个订单的签约单子"
- **AND** RouterNode 路由到 admin
- **THEN** AdminNode 返回包含 PersonalSignableOrderInfo 或 FormalSignableOrderInfo 的推荐列表

#### Scenario: 用户输入完全无关内容
- **WHEN** 用户输入"今天天气怎么样"
- **AND** RouterNode 路由到 admin
- **THEN** AdminNode 返回"抱歉，我无法理解您的问题。请描述您的业务需求，例如：查询订单合同、排查签约问题等。"

---

### Requirement: 能力列表动态构建
AdminNode SHALL 运行时从 SkillRegistry 和 EntityRegistry 读取可用能力列表，SHALL NOT 硬编码能力信息。

#### Scenario: 新增 Skill 后自动可推荐
- **WHEN** 系统新增一个 Skill `new-diagnosis-skill`
- **AND** 用户输入与该 Skill 语义相关
- **THEN** AdminNode 能推荐该新 Skill，无需修改代码

#### Scenario: 能力列表包含 Skills 和实体
- **WHEN** AdminNode 构建能力列表
- **THEN** 列表包含 Skill 名称 + 描述，以及实体名称 + displayName + aliases

---

### Requirement: 推荐输出格式标准化
AdminNode 的推荐输出 SHALL 采用标准化格式，便于用户理解和 UI 展示。

#### Scenario: 有匹配能力时的输出格式
- **WHEN** LLM 找到 1-3 个语义相似的能力
- **THEN** 输出格式为"您可能想问：\n1. {能力描述1}\n2. {能力描述2}\n3. {能力描述3}"

#### Scenario: 无匹配能力时的输出格式
- **WHEN** LLM 无法找到任何语义相似的能力
- **THEN** 输出格式为"抱歉，我无法理解您的问题。请描述您的业务需求，例如：{示例列表}"
