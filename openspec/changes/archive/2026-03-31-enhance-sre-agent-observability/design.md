## Context

SRE-Agent 是一个基于 Spring AI Alibaba 的智能运维助手，通过 SSE 流式输出与前端交互。当前存在以下问题：

1. **前端流式体验差**：前端使用累积 50 字符才更新 UI 的策略，导致"一块一块"的输出效果
2. **日志缺失**：请求日志仅输出到控制台，无持久化，难以排查问题
3. **评估报告不完整**：测试报告截断输入输出，无法验证评估 Agent 工作是否正常
4. **路由映射 Bug**：RouterNode 返回 `query`/`investigate`，但图配置期望 `queryAgent`/`investigateAgent`

## Goals / Non-Goals

**Goals:**
- 实现真正的流式输出体验，每个 SSE 事件立即更新前端 UI
- 持久化请求日志到文件系统，按日期滚动，仅保留一天
- 评估报告显示完整的输入、输出和评估理由
- 修复 RouterNode 路由映射问题

**Non-Goals:**
- 不改变 SSE 协议或数据格式
- 不引入新的日志框架（使用 Logback）
- 不改变评估 Agent 的判断逻辑

## Decisions

### 1. 前端流式输出策略

**决策**：移除字符累积限制，改为每个 SSE 事件立即更新 UI

**备选方案**：
- A. 累积 50 字符更新（当前方案）- 用户体验差
- B. 每个 SSE 事件立即更新 - **选中方案**，体验最佳
- C. 使用 requestAnimationFrame 节流 - 增加复杂度，收益不明显

**理由**：SSE 本身已经是服务器推送的粒度，无需再在前端累积

### 2. 日志持久化方案

**决策**：使用 Logback RollingFileAppender，按日期滚动

**配置**：
- 日志目录：`log/`
- 文件名模式：`sre-agent.yyyy-MM-dd.log`
- 保留策略：`maxHistory=1` + `cleanHistoryOnStart=true`

**理由**：
- Logback 是 Spring Boot 默认日志框架，无额外依赖
- 按日期滚动便于按天查看和清理
- `cleanHistoryOnStart` 确保启动时清理过期文件

### 3. 评估报告格式

**决策**：显示完整内容，使用代码块包裹

**理由**：
- 评估报告是验证测试正确性的关键文档
- 截断会导致无法判断评估 Agent 是否正常工作
- Markdown 代码块保证可读性

## Risks / Trade-offs

| 风险 | 缓解措施 |
|------|---------|
| 高频 UI 更新可能影响性能 | 保持每 5 次更新解析一次 Markdown 的策略 |
| 日志文件可能占用磁盘 | 仅保留 1 天，启动时自动清理 |
| 完整输出可能使报告文件变大 | Markdown 格式已足够紧凑，影响可忽略 |
