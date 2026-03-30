# 排查过程 Thinking 流式输出

## Summary

在 investigate agent 排查问题时，将排查过程以流式方式输出到前端页面，包含步骤、查询数据、结果等，以 Thinking 样式展示（可折叠卡片）。

## Motivation

用户在 07-ChatUI 前端使用 investigate agent 排查问题时，只能看到最终结论，无法看到排查过程（经历了哪些步骤、查询了哪些数据、数据结果怎样）。需要将这些中间过程以流式方式输出，提升用户体验。

## Proposed Changes

### 后端（06-SRE-Agent）

1. **扩展 TracingContext**
   - 新增字段：`stepNumber`、`stepTitle`、`recordCount`

2. **创建 ThinkingOutputService**
   - 从 TracingService 读取工具调用步骤
   - 构建 `<thinking>...</thinking>` Markdown 块

3. **修改 SREAgentGraph**
   - investigateAgent 直接透传 ReactAgent 流式输出
   - 在结果末尾追加 Thinking 步骤内容

4. **清理废弃代码**
   - 删除 SREAgentLoader.java
   - 删除 RunSseFilter.java
   - 移除 spring-ai-alibaba-studio 依赖

### 前端（07-ChatUI）

1. **复制 ThinkingNode.vue 组件**
   - 从 markstream-vue playground 复制可折叠 Thinking 组件

2. **注册自定义组件**
   - main.ts 中 setCustomComponents('chatui', { thinking: ThinkingNode })

3. **配置 customHtmlTags**
   - App.vue 中添加 custom-html-tags="['thinking']"

## Verification

运行 InvestigateAgentIT 集成测试验证：
- 测试通过，Thinking 块正确输出
- 包含步骤序号、工具名、参数、JSON 结果、耗时
