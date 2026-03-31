## Why

当前 SRE-Agent 存在三个可观测性和用户体验问题：
1. 前端输出"一块一块"显示，非真正的流式输出，用户体验僵硬
2. 后端请求日志未持久化，难以排查问题和回溯历史
3. 评估测试报告截断输入输出，无法验证评估 Agent 工作是否正常

## What Changes

- **前端流式输出优化**：移除 50 字符累积限制，改为每个 SSE 事件立即更新 UI
- **后端请求日志持久化**：新增日志配置和服务，按日期滚动，仅保留一天
- **评估报告完善**：显示完整的输入、输出和评估理由
- **Bug 修复**：RouterNode 路由映射修复（`query` → `queryAgent`，`investigate` → `investigateAgent`）

## Capabilities

### New Capabilities

- `request-logging`: 请求日志持久化能力，记录每次请求的完整输入输出到日志文件

### Modified Capabilities

- `streaming-output`: 前端流式输出行为变更，从累积输出改为即时输出
- `qa-evaluation-report`: 评估报告格式变更，显示完整内容而非截断

## Impact

- 前端：`07-ChatUI/frontend/src/composables/useChat.ts`
- 后端：
  - `06-SRE-Agent/src/main/resources/logback-spring.xml`（新增）
  - `06-SRE-Agent/src/main/java/com/yycome/sreagent/infrastructure/service/RequestLogService.java`（新增）
  - `06-SRE-Agent/src/main/java/com/yycome/sreagent/trigger/http/ChatController.java`
  - `06-SRE-Agent/src/main/java/com/yycome/sreagent/config/node/RouterNode.java`
- 测试：
  - `06-SRE-Agent/src/test/java/com/yycome/sreagent/e2e/QaEvaluationReporter.java`
  - `06-SRE-Agent/src/test/resources/qa-pairs/sre-agent-qa.yaml`
