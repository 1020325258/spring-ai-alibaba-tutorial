## 1. 新增 SkillRoutingStrategy 接口和 LLM 实现

- [x] 1.1 新建 `SkillRoutingStrategy` 接口（`config/node/routing/SkillRoutingStrategy.java`），声明 `String route(String userInput)` 方法
- [x] 1.2 新建 `LlmSkillRoutingStrategy`（`config/node/routing/LlmSkillRoutingStrategy.java`），构造时注入 `ChatModel` 和 `SkillRegistry`，从 `skillRegistry.listAll()` 动态构建路由 Prompt
- [x] 1.3 在 `SREAgentGraphConfiguration`（或新建 `RoutingConfiguration`）中将 `LlmSkillRoutingStrategy` 注册为 Spring Bean

## 2. 改造 RouterNode

- [x] 2.1 `RouterNode` 构造函数改为接收 `SkillRoutingStrategy`（移除直接持有 `ChatModel`）
- [x] 2.2 `RouterNode.apply()` 中：调用 `strategy.route(input)` 获取结果，若结果为 Skill name 则写 `routingTarget = "investigateAgent"` + `selectedSkill = result`；否则按 query/admin/unclear 规则写 `routingTarget`
- [x] 2.3 `SREAgentGraphConfiguration` 中更新 RouterNode 构造，注入 `SkillRoutingStrategy` Bean

## 3. State 新增 selectedSkill 字段

- [x] 3.1 `SREAgentGraphConfiguration.keyStrategyFactory` 中新增 `selectedSkill` 键（ReplaceStrategy）

## 4. AgentNode 传递 selectedSkill 给 investigateAgent

- [x] 4.1 `AgentNode.apply()` 中读取 `state["selectedSkill"]`，若非空则将其拼入用户输入：`{input}\n[selectedSkill: {skillName}]`，再调用 agent

## 5. 更新 investigate-agent.md

- [ ] 5.1 删除 `investigate-agent.md` 中的 Skill 触发规则表章节
- [ ] 5.2 新增说明：路由器已预选 Skill，第一步直接调用 `readSkill([selectedSkill] 标注中的 Skill name)`

## 6. 修复 buildSkillsList 动态化

- [ ] 6.1 `AgentConfiguration.buildSkillsList()` 改为遍历 `skillRegistry.listAll()`，用 `skill.getName()` 和 `skill.getDescription()` 构建列表，删除硬编码字符串

## 7. 测试验证

- [ ] 7.1 新增端到端测试用例（`sre-agent-qa.yaml` 或 `InvestigateAgentIT`）：输入"826033014000004927缺少定软电品类报价"，断言路由到 investigateAgent 且 `readSkill` 被调用
- [ ] 7.2 运行集成测试 `./run-integration-tests.sh`，确保原有排查和查询用例全部通过
