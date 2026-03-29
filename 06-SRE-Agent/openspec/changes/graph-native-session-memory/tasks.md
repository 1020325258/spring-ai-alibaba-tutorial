## 1. 接口与策略实现

- [ ] 1.1 新建 ContextInjectionStrategy 接口（inject(List<Message> history, String currentInput): String）
- [ ] 1.2 实现 FullHistoryStrategy（注入全部 H/A 摘要对，格式：[历史对话]\n用户：...\nSRE：...\n\n[当前问题]\n{input}）
- [ ] 1.3 实现 SlidingWindowStrategy(int n)（注入最近 N 轮 H/A 对）

## 2. SREAgentGraph 重构

- [ ] 2.1 OverAllState 新增 conversationHistory（AppendStrategy）和 enrichedInput（ReplaceStrategy）key
- [ ] 2.2 新增 MemoryNode 内部类：读取 state["conversationHistory"] + state["input"]，调用 injectionStrategy，写 state["enrichedInput"]
- [ ] 2.3 initGraph() 在 START 后插入 MemoryNode 节点（START → MemoryNode → RouterNode → ...）
- [ ] 2.4 RouterNode 和 AgentNode 改为读取 state["enrichedInput"] 而非 state["input"]
- [ ] 2.5 AgentNode 执行完毕后将本轮 [HumanMessage(input), AssistantMessage(result)] 追加到 state["conversationHistory"]
- [ ] 2.6 移除 streamMessages(String) 中对子 Agent 的直接调用逻辑
- [ ] 2.7 新增 streamMessages(String input, String threadId) 重载：调用 compiledGraph.stream(inputs, RunnableConfig{threadId})，过滤终态节点并从 state["result"] 提取 AssistantMessage

## 3. AgentConfiguration 调整

- [ ] 3.1 新增 @Bean BaseCheckpointSaver memorySaver()（返回 new MemorySaver()）
- [ ] 3.2 将 memorySaver 注入 SREAgentGraph，在 getAndCompileGraph() 调用前设置 CompileConfig（SaverConfig.builder().register(memorySaver).build()）
- [ ] 3.3 queryAgent 增加 MessagesModelHook(maxMessages=20)
- [ ] 3.4 investigateAgent 增加 MessagesModelHook(maxMessages=20)
- [ ] 3.5 确认 queryAgent / investigateAgent 不注入 saver（保持无状态）

## 4. RunSseFilter 调整

- [ ] 4.1 从请求体 JSON 解析 threadId 字段（root.path("threadId").asText(null)）
- [ ] 4.2 threadId 非空时调用 sreAgent.streamMessages(input, threadId)，为空时调用原有无参重载

## 5. 验证

- [ ] 5.1 运行 ./run-integration-tests.sh 全量通过（现有测试走无参重载，行为不变）
- [ ] 5.2 Studio 手动验证：同一 threadId 两轮对话，日志确认第二轮 enrichedInput 包含第一轮历史
- [ ] 5.3 Studio 验证：不同 threadId 历史互不可见
- [ ] 5.4 验证无 threadId 请求正常响应（无报错，无历史）
