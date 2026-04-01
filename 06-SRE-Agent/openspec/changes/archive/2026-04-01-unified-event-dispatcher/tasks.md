## 1. 后端：节点枚举和事件分发器

- [x] 1.1 新增 SREAgentNodeName 枚举（src/main/java/com/yycome/sreagent/config/node/SREAgentNodeName.java）
  - 定义 router、queryAgent、investigateAgent、admin、tool_call 枚举值
  - 提供 nodeName() 和 displayTitle() 方法
  - 提供 fromNodeName(String) 静态方法

- [x] 1.2 新增 SREAgentEventDispatcher 统一事件分发器（src/main/java/com/yycome/sreagent/config/SREAgentEventDispatcher.java）
  - 注入 ObjectMapper
  - 实现 dispatch(NodeOutput, Sinks.Many<ServerSentEvent<String>>) 方法
  - 实现 buildRoutingEvent()、buildToolCallEvent()、buildConclusionEvent()、buildMarkdownEvent() 私有方法

- [x] 1.3 新增 ThinkingContextHolder 支持事件列表收集
  - 扩展 ThinkingContext，添加 List<ThinkingEvent> toolEvents 字段
  - 新增 addToolEvent(ThinkingEvent) 和 getToolEvents() 方法

## 2. 后端：改造现有事件发送逻辑

- [x] 2.1 改造 SREAgentGraphProcess
  - 移除 resolveContent() 中的直接发送逻辑
  - 改为调用 SREAgentEventDispatcher.dispatch()
  - 注入 SREAgentEventDispatcher 实例

- [x] 2.2 改造 ObservabilityAspect
  - 移除 thinkingEventPublisher.publishStepThinking() 调用
  - 改为实时发送工具事件到 sink（而非收集到上下文）
  - 确保在工具调用完成后立即发送到前端

- [x] 2.3 改造 ThinkingEvent 模型
  - 移除 stepNumber 字段
  - 添加 nodeName 字段
  - 添加 displayTitle 字段
  - 更新 fromTracingContext() 方法

## 3. 前端：解析逻辑改造

- [x] 3.1 改造 useChat.ts 解析逻辑
  - 将 type 字段解析改为 nodeName 字段解析
  - 实现 findNode(nodeName) 函数
  - 更新 ThinkingBlock 接口，移除 stepNumber，添加 displayTitle

- [x] 3.2 改造 ThinkingNode.vue 展示
  - 移除 stepNumber 显示
  - 改为显示 displayTitle 或 stepTitle

- [ ] 3.3 验证前端解析兼容性
  - 确保能正确解析新的 nodeName 格式事件
  - 验证结论（queryAgent）、思考步骤（tool_call）、Markdown 文本都能正常展示

## 4. 测试验证

- [x] 4.1 运行单元测试验证枚举和分发器
  - 执行 ./scripts/run-unit-tests.sh

- [x] 4.2 运行集成测试验证端到端流程
  - 执行 ./run-integration-tests.sh
  - 验证路由决策、工具调用、结论输出都正常

- [ ] 4.3 前后端联调验证
  - 启动后端服务
  - 启动前端开发服务器
  - 发送测试请求，验证事件顺序和展示效果

## 5. 清理和文档

- [x] 5.1 删除不再使用的代码
  - 移除 SREAgentGraphProcess 中已迁移的 resolveContent 代码
  - 移除 ThinkingEventPublisher 中未使用的发布方法

- [x] 5.2 更新 CLAUDE.md 开发规范
  - 记录新的事件分发机制
  - 说明 nodeName 枚举的使用方式