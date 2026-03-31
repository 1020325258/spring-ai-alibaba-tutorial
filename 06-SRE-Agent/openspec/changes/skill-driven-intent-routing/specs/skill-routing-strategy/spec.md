## ADDED Requirements

### Requirement: SkillRoutingStrategy 接口
系统 SHALL 提供 `SkillRoutingStrategy` 接口，RouterNode 通过该接口完成意图路由决策，不直接持有 ChatModel 路由逻辑。

#### Scenario: 用户描述匹配某个 Skill
- **WHEN** 用户输入的症状描述与某个 Skill 的 description 语义匹配
- **THEN** `SkillRoutingStrategy.route()` 返回该 Skill 的 name（如 `sales-contract-sign-dialog-diagnosis`）

#### Scenario: 用户意图为数据查询
- **WHEN** 用户输入表达查询订单、合同、节点、报价等数据的意图
- **THEN** `SkillRoutingStrategy.route()` 返回 `"query"`

#### Scenario: 用户意图为系统配置
- **WHEN** 用户输入询问系统配置、本体模型、环境信息或操作方法
- **THEN** `SkillRoutingStrategy.route()` 返回 `"admin"`

#### Scenario: 意图不明确
- **WHEN** 用户仅提供编号和症状，无法判断想做查询还是排查
- **THEN** `SkillRoutingStrategy.route()` 返回 `"unclear"`

---

### Requirement: LlmSkillRoutingStrategy 动态 Prompt 构建
`LlmSkillRoutingStrategy` SHALL 在构造时从 `SkillRegistry.listAll()` 读取所有 Skill 的 name 和 description，动态构建路由 Prompt，不得硬编码 Skill 条目。

#### Scenario: 新增 Skill 后路由自动感知
- **WHEN** `skills/` 目录新增一个包含 frontmatter 的 SKILL.md
- **THEN** 重启后 RouterNode 无需修改任何代码即可路由到新 Skill

#### Scenario: 措辞不同但语义相同的排查描述
- **WHEN** 用户输入"826033014000004927缺少定软电品类报价"
- **THEN** RouterNode 路由到 `investigateAgent`，`selectedSkill` = `sales-contract-sign-dialog-diagnosis`

---

### Requirement: selectedSkill 状态传递
RouterNode SHALL 在路由到 investigateAgent 时，将选定的 Skill name 写入 state 的 `selectedSkill` 字段；investigateAgent 通过 AgentNode 接收该字段并在调用 readSkill 时直接使用，不再由 LLM 二次决策。

#### Scenario: selectedSkill 写入 state
- **WHEN** RouterNode 判断路由目标为 investigateAgent
- **THEN** state 中 `routingTarget = "investigateAgent"` 且 `selectedSkill = <skillName>`

#### Scenario: investigateAgent 直接使用 selectedSkill
- **WHEN** investigateAgent 收到含 `selectedSkill` 的请求
- **THEN** 第一步调用 `readSkill(selectedSkill)` 加载 SOP，不再通过 Prompt 触发表决策

---

### Requirement: buildSkillsList 动态化
`AgentConfiguration.buildSkillsList()` SHALL 使用 `SkillRegistry.listAll()` 动态构建 Skill 列表字符串，不得包含硬编码的 Skill name 或 description。

#### Scenario: Skills 列表反映注册表实际内容
- **WHEN** `SkillRegistry` 中注册了 N 个 Skill
- **THEN** `buildSkillsList()` 返回的字符串包含所有 N 个 Skill 的 name 和 description
