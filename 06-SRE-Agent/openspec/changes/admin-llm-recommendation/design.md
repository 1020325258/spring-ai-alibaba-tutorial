## Context

当前 SRE-Agent 的路由架构：
- `RouterNode`：根据用户输入选择 `investigateAgent`、`queryAgent` 或 `admin`
- `AdminNode`：处理无法识别的请求，当前仅返回"无法理解"的占位文本

问题：用户输入"826033014000004927 可签约S单"，路由器未能匹配到 `PersonalSignableOrderInfo` 查询，错误地路由到 admin。admin 无法给出有效引导，用户体验差。

## Goals / Non-Goals

**Goals:**
- admin 节点能够根据用户输入推荐语义相似的能力（Skills + 实体）
- 推荐结果准确率 80%+（基于 LLM 语义理解）
- 无需向量检索基础设施，保持轻量
- 新增 Skill/实体后无需修改代码，能力列表自动更新

**Non-Goals:**
- 不处理完全无关的输入（如"今天天气"），仅返回"无法识别"
- 不替代 RouterNode 的职责，admin 仅在路由失败后介入
- 不实现多轮对话澄清（仅单次推荐）

## Decisions

### D1: 能力列表动态构建

**选择**：运行时从 `SkillRegistry` + `EntityRegistry` 读取
**替代方案**：硬编码能力列表
**理由**：
- 当前项目 Skills 数量约 10 个，动态构建成本低
- 新增 Skill 后无需修改 admin 代码，自动生效
- 与 `LlmSkillRoutingStrategy` 的能力来源保持一致

### D2: 推荐逻辑放在 AdminNode

**选择**：在 `AdminNode.apply()` 中调用 LLM
**替代方案**：创建独立的 `RecommendationService`
**理由**：
- 推荐逻辑简单，不需要独立服务
- AdminNode 是路由终点，直接处理即可
- 避免过度设计

### D3: 输出格式

**选择**：标准化"您可能想问：1. XXX 2. YYY"格式
**理由**：
- 结构化输出便于 UI 展示
- 用户可快速选择澄清意图

## Risks / Trade-offs

| 风险 | 缓解措施 |
|------|---------|
| LLM 推荐延迟 +100~300ms | admin 本就是兜底路径，延迟可接受 |
| LLM 推荐不准确 | 提供 Top-K 候选，用户选择；后续可优化 prompt |
| 无关输入浪费 LLM 调用 | 先做简单规则过滤（如长度 < 5 字符直接返回默认提示） |
