## 1. 后端 SSE 事件类型扩展

- [x] 1.1 修改 SREAgentGraphProcess 支持 JSON 格式的 thinking/conclusion 事件类型
- [x] 1.2 在 SREAgentGraphProcess 中添加 buildThinkingJson 和 buildConclusionJson 方法

## 2. 工具调用追踪集成

- [x] 2.1 修改 ObservabilityAspect，在 endToolCall 后触发 Thinking 事件发布
- [x] 2.2 集成 ThinkingEventPublisher，传递 TracingContext

## 3. Thinking 内容构建

- [x] 3.1 扩展 ThinkingOutputService，暴露构建单步结果的方法 buildSingleStep
- [x] 3.2 确保 ThinkingEventPublisher 能够接收 TracingContext 并构建输出

## 4. 前端适配

- [x] 4.1 修改 useChat.ts，解析 JSON 格式的 thinking 和 conclusion 类型
- [x] 4.2 实现 thinking 类型的渲染（可折叠卡片）
- [x] 4.3 实现 conclusion 类型的渲染（普通文本）

## 5. 验证测试

- [x] 5.1 启动 06-SRE-Agent 和 07-ChatUI 服务
- [x] 5.2 通过前端发送需要多步查询的问题
- [x] 5.3 验证 SSE 事件分阶段发送（先 thinking，后 conclusion）
- [x] 5.4 验证排查过程包含工具名称、参数、结果摘要
- [x] 5.5 验证最终结论在排查过程之后输出
