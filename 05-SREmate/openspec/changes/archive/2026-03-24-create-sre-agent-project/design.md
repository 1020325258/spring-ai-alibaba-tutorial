## Context

### 背景
- **05-SREmate**：现有数据查询助手，基于 Spring AI，单 Agent 架构，使用 DirectOutput 优化查询性能
- **04-AnalysisAgent**：已验证 Spring AI Graph 多 Agent 编排能力，使用 StateGraph 定义 Agent 节点

### 约束
- SRE-Agent 需要调用 05-SREmate 的数据查询能力
- 需要支持 Skill 机制封装排查 SOP
- 多 Agent 场景下，子 Agent 返回 JSON 而非直接输出

### 目标用户
SRE 值班人员、研发人员，需要排查业务数据异常问题

---

## Goals / Non-Goals

**Goals:**
1. 实现基于 Spring AI Graph 的多 Agent 编排架构
2. 支持 Supervisor 模式的主 Agent 路由能力
3. 集成 Skill 框架，支持排查 SOP 复用
4. 与 05-SREmate 通过 HTTP 通信获取数据

**Non-Goals:**
- 不包含 SREmate 的 DirectOutput 优化（多 Agent 场景不需要）
- 不复制 SREmate 的完整代码，仅复用 ontologyQuery 调用方式
- 暂不实现告警自动触发排查（MVP 阶段）

---

## Decisions

### D1: 使用 Spring AI Graph 而非 LangChain4j

**备选：** LangChain4j、AutoGen

**选择：** Spring AI Graph

**理由：**
1. 已在 04-AnalysisAgent 验证可行
2. 与现有 SREmate 技术栈一致
3. StateGraph 模式适合编排多个 Agent

### D2: Supervisor Agent 模式

**备选：** LlmRouting Agent、Handoffs

**选择：** Supervisor Agent

**理由：**
1. 更灵活，可以根据用户意图动态决定路由
2. 适合"查询"和"排查"两种不同交互模式
3. 04 项目已有类似实践

### D3: 通过 HTTP 调用 05-SREmate

**备选：** 共享数据库、消息队列

**选择：** HTTP API

**理由：**
1. 05-SREmate 已有 OntologyController
2. 简单直接，无需额外基础设施
3. 解耦两个 Agent，独立演进

### D4: Skill 存储方式

**备选：** 数据库、文件系统

**选择：** 文件系统（SKILL.md）

**理由：**
1. 与 Spring AI Skill 官方机制一致
2. 易于版本控制
3. 运维简单，修改无需重启服务

---

## Risks / Trade-offs

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 多 Agent 响应时间变长 | 用户体验下降 | 明确告知用户这是多轮排查 |
| Skill 匹配不准确 | 排查路径错误 | 优化 description，提高匹配精度 |
| 05-SREmate 服务不可用 | 无法查询数据 | 添加降级提示，引导用户直接查询 05 |

---

## Migration Plan

### Phase 1: 项目初始化
1. 复制 05-SREmate 到 06-SRE-Agent
2. 修改 pom.xml 中的 artifactId 和模块名
3. 移除 @DataQueryTool 相关代码

### Phase 2: 核心架构
1. 引入 Spring AI Graph 依赖
2. 参考 04-AnalysisAgent 创建 Graph 配置
3. 实现 Supervisor Agent + Query Agent + Investigate Agent

### Phase 3: Skill 集成
1. 集成 SkillRegistry
2. 实现 read_skill 工具
3. 添加首个排查 Skill（missing-personal-quote-diagnosis）

### Phase 4: 测试验证
1. 端到端测试排查场景
2. 验证 Agent 间通信正常

---

## Open Questions

1. **Q1:** SRE-Agent 的入口是 CLI 还是 HTTP？
   - 建议：先 CLI（复用 Console 能力），后续可扩展 HTTP

2. **Q2:** 是否需要独立部署？
   - 建议：MVP 阶段与 05 共同部署，通过不同端口区分

3. **Q3:** 排查 Skill 需要覆盖多少场景？
   - 建议：MVP 阶段先覆盖 3-5 个高频场景
