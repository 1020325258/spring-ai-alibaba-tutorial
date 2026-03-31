## 1. 构建能力列表工具方法

- [x] 1.1 在 `AdminNode` 中注入 `SkillRegistry` 和 `EntityRegistry`（或通过 `OntologyQueryEngine` 获取实体信息）
- [x] 1.2 创建 `buildAvailableCapabilities()` 方法，返回格式化的能力列表字符串
  - Skills: `skillName + description`（从 `SkillRegistry.listAll()` 获取）
  - 实体: `entityName + displayName + aliases`（从 `EntityRegistry` 或 YAML 配置获取）

## 2. 实现 LLM 推荐逻辑

- [x] 2.1 在 `AdminNode` 中注入 `ChatModel`（若未注入）
- [x] 2.2 创建 `recommendCapabilities(String userInput, String capabilities)` 方法
- [x] 2.3 设计推荐专用 prompt 模板：
  - 输入：用户输入 + 能力列表
  - 输出：Top-K 相似能力或"无法识别"
  - 格式要求：标准化"您可能想问..."输出

## 3. 重构 AdminNode.apply()

- [x] 3.1 修改 `AdminNode.apply()` 方法，调用 `recommendCapabilities()`
- [x] 3.2 添加简单规则过滤（如输入长度 < 5 字符，直接返回默认提示）
- [x] 3.3 返回推荐结果字符串

## 4. 更新测试

- [x] 4.1 在 `InvestigateAgentIT` 或新建 `AdminNodeIT` 中添加测试用例：
  - 用户输入"可签约S单" → 推荐包含 PersonalSignableOrderInfo
  - 用户输入"今天天气" → 返回"无法识别"提示
- [x] 4.2 运行集成测试验证功能（注：有一个预存在的 PersonalQuote 测试失败，与本变更无关）

## 5. 文档更新

- [x] 5.1 更新 `CLAUDE.md` 说明 admin 节点的推荐能力
