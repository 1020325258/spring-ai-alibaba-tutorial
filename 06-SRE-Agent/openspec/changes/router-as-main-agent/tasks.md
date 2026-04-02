## 1. RouterNode 升级为主 Agent

- [ ] 1.1 在 RouterNode 新增 `SkillRegistry` 和 `EntityRegistry` 字段及构造函数参数（5 参数）
- [ ] 1.2 更新 `ROUTER_PROMPT`：新增 `answer` 意图分类描述，删除 `admin` 意图中的引导场景
- [ ] 1.3 新增 `ANSWER_PROMPT` 常量：包含历史上下文、系统能力列表、用户输入三段
- [ ] 1.4 将 `routeByLLM()` 重命名为 `classifyIntent()`，更新 intent switch：`answer`/低置信度 → `target="done"`，`admin` → `target="admin"`
- [ ] 1.5 在 `apply()` 中：当 `target=="done"` 时调用 `generateDirectAnswer()` 并写入 `state["result"]`
- [ ] 1.6 新增 `generateDirectAnswer(String input, String contextInfo)` 方法：使用 ANSWER_PROMPT 调用 chatModel，异常时返回默认帮助文本
- [ ] 1.7 新增 `buildAvailableCapabilities()` 方法：从 SkillRegistry 和 EntityRegistry 构建能力列表文本
- [ ] 1.8 编译验证 RouterNode 无报错（SREAgentGraphConfiguration 此时尚未更新，预期有构造参数不匹配报错）

## 2. AdminNode 瘦身

- [ ] 2.1 删除 AdminNode 的 `SkillRegistry`、`EntityRegistry`、`ChatModel`、`ReactAgent`、`TracingService` 字段及 import
- [ ] 2.2 构造函数缩减为单参数 `(EnvironmentConfig environmentConfig)`
- [ ] 2.3 删除 `recommendCapabilities()`、`buildAvailableCapabilities()`、`buildDefaultHelpResponse()` 方法
- [ ] 2.4 简化 `executeAdmin()`：直接调用 `handleEnvSwitch()`，无匹配时返回 `buildEnvListResponse()`

## 3. 事件分发与节点名称更新

- [ ] 3.1 `SREAgentNodeName`：将 `ADMIN` 的 `displayTitle` 从 "智能推荐" 改为 "后台管理"
- [ ] 3.2 `SREAgentEventDispatcher`：ROUTER case 新增 `routingTarget=="done"` 判断，匹配时调用 `buildMarkdownEvent(output)`，否则调用 `buildRoutingEvent(output)`

## 4. 配置类接线

- [ ] 4.1 `SREAgentGraphConfiguration`：删除 `adminAgent` 的 `@Autowired` 字段（ReactAgent）
- [ ] 4.2 更新 RouterNode 构造调用：传入 `messageWindowChatMemory`、`sessionProperties`、`skillRegistry`、`entityRegistry`
- [ ] 4.3 更新 AdminNode 构造调用：只传 `environmentConfig`
- [ ] 4.4 条件边 Map 新增 `"done", END` 映射
- [ ] 4.5 编译验证：`mvn compile -pl 06-SRE-Agent -q`，期望 BUILD SUCCESS

## 5. 测试验证

- [ ] 5.1 运行单元测试：`ObservabilityAspectAnnotationTest,ToolExecutionTemplateTest,ToolResultTest,QueryScopeTest,EntityRegistryTest,OntologyQueryEngineTest,PersonalQuoteGatewayTest`
- [ ] 5.2 检查 `log/sre-agent.log` 无新增 ERROR
- [ ] 5.3 运行集成测试：`./run-integration-tests.sh`
- [ ] 5.4 确认 query/investigate 场景通过；若有 admin 引导场景失败，验证语义等价后更新 qa-pairs 期望

## 6. 提交

- [ ] 6.1 `git add` 所有变更文件并提交，commit message 包含 `feat: RouterNode as main agent with direct answer capability`
