# Multi-Agent Orchestration

## Overview

基于 Spring AI Graph 实现多 Agent 编排能力，支持 Supervisor 模式的主 Agent 路由。

## Components

### Supervisor Agent
- 职责：理解用户意图，路由到合适的子 Agent
- 输入：用户原始问题
- 输出：路由决策 + 汇总结果

### Query Agent
- 职责：数据查询
- 复用 05-SREmate 的 ontologyQuery 能力
- 通过 HTTP 调用 05-SREmate

### Investigate Agent
- 职责：问题排查
- 调用 Skill 获取排查 SOP
- 通过 Query Agent 获取数据

## Data Flow

```
User Input
    ↓
Supervisor (意图识别 + 路由)
    ↓
Query Agent / Investigate Agent
    ↓ (通过 HTTP)
05-SREmate (数据查询)
    ↓
Result → Supervisor → User
```

## Configuration

- Graph 定义使用 StateGraph
- Agent 节点使用 AsyncNodeAction
- 路由使用 ConditionalEdges

---

## Acceptance Criteria (严格验收标准)

### 1. 编译验收

- [ ] `mvn compile` 必须通过，无编译错误
- [ ] `mvn test-compile` 必须通过，无测试代码编译错误

### 2. 多 Agent 消息传递验收

- [ ] **Supervisor → Query Agent**：用户查询请求能正确传递给 Query Agent
- [ ] **Supervisor → Investigate Agent**：用户排查请求能正确传递给 Investigate Agent
- [ ] **Query Agent → Supervisor**：查询结果能正确返回给 Supervisor
- [ ] **Investigate Agent → Supervisor**：排查结论能正确返回给 Supervisor
- [ ] **状态传递**：Graph 执行过程中，OverAllState 中的数据在各节点间正确传递

### 3. 各 Agent 执行验收

#### Supervisor Agent
- [ ] 能接收用户输入
- [ ] 能识别"查询"意图 → 路由到 Query Agent
- [ ] 能识别"排查"意图 → 路由到 Investigate Agent
- [ ] 能接收子 Agent 结果并输出最终答案

#### Query Agent
- [ ] 能接收查询请求
- [ ] 能调用 ontologyQuery 工具
- [ ] 能返回查询结果（JSON 格式）

#### Investigate Agent
- [ ] 能接收排查请求
- [ ] 能调用 read_skill 工具加载 Skill
- [ ] 能按 Skill 指令执行排查步骤
- [ ] 能返回排查结论

### 4. Graph 编排验收

- [ ] StateGraph 能正确初始化
- [ ] START → Supervisor 边正确连接
- [ ] Supervisor → Query Agent 边正确连接（条件路由）
- [ ] Supervisor → Investigate Agent 边正确连接（条件路由）
- [ ] 子 Agent → END 边正确连接

### 5. 集成测试验收

- [ ] 端到端测试：查询场景能正确返回数据
- [ ] 端到端测试：排查场景能正确返回结论

---

## Test Cases

### Unit Tests

| 测试类 | 验证内容 |
|--------|---------|
| `SupervisorAgentTest` | 意图识别、路由决策 |
| `QueryAgentTest` | 查询请求构造、结果解析 |
| `InvestigateAgentTest` | Skill 加载、排查步骤执行 |
| `GraphConfigurationTest` | StateGraph 初始化、边连接 |
| `MessagePassingTest` | Agent 间消息传递 |

### Integration Tests

| 测试类 | 验证内容 |
|--------|---------|
| `MultiAgentE2ETest` | 完整的多 Agent 编排流程 |
| `InvestigationSkillE2ETest` | Skill 加载和执行完整流程 |
