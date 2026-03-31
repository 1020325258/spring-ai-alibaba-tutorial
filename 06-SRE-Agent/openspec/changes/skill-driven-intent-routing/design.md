## Context

RouterNode 当前通过硬编码 Prompt 将用户意图分为 4 类（query/investigate/admin/unclear），分类粒度粗，导致语义相近但措辞不同的排查场景被误路由（如"缺少定软电品类报价"被归入 admin 而非 investigate）。

同时存在两个结构性问题：
1. **Skill 触发逻辑分散两处**：RouterNode Prompt 负责粗路由，`investigate-agent.md` 中维护 Skill 触发关键词表，新增 Skill 需同步修改两个文件
2. **`buildSkillsList` 硬编码**：`AgentConfiguration.buildSkillsList()` 未使用 `SkillRegistry.listAll()`，Skill 列表靠手工维护，无法感知新增 Skill

API 约束：`SkillRegistry.listAll()` 返回 `List<SkillMetadata>`，每个含 `getName()` 和 `getDescription()`，可直接作为路由 Prompt 的 Skill 条目。

## Goals / Non-Goals

**Goals:**
- RouterNode 在 Skill 粒度上做路由决策，直接输出 Skill name（或 query/admin/unclear）
- 路由 Prompt 从 `SkillRegistry.listAll()` 动态构建，新增 Skill 无需修改任何 Java 代码
- RouterNode 将选定的 Skill name 写入 state，investigateAgent 直接使用，无需二次 LLM 决策
- `buildSkillsList` 改为动态读取 `SkillRegistry`，消除硬编码
- 抽出 `SkillRoutingStrategy` 接口，为未来切换路由策略（embedding、hierarchical）预留扩展点

**Non-Goals:**
- 不实现 embedding-based 路由（当前 Skill 数量少，不需要）
- 不改变 StateGraph 拓扑（节点数量和连接关系不变）
- 不改变 queryAgent、adminAgent 的行为

## Decisions

### 决策 1：抽出 `SkillRoutingStrategy` 接口

**选择**：RouterNode 不直接调用 ChatModel，改为委托给注入的 `SkillRoutingStrategy`。

**接口**：
```java
public interface SkillRoutingStrategy {
    /**
     * 根据用户输入返回路由目标。
     * @return Skill name（如 "sales-contract-sign-dialog-diagnosis"）、
     *         "query"、"admin"、"unclear" 之一
     */
    String route(String userInput);
}
```

**理由**：RouterNode 保持稳定，路由智能可独立演进。未来换 embedding 只需新增一个 Spring Bean 实现并改配置，不动图结构。

**备选**：直接在 RouterNode 内部扩展 Prompt → 不可替换，扩展性差，放弃。

---

### 决策 2：`LlmSkillRoutingStrategy` 动态构建路由 Prompt

**选择**：`LlmSkillRoutingStrategy` 在构造时从 `SkillRegistry.listAll()` 读取所有 Skill 的 `name` + `description`，组装为路由 Prompt 中的条目。

**Prompt 结构**：
```
你是一个路由器，根据用户问题判断应该路由到哪个处理器。

可用的排查 Skill（用户描述匹配时直接回复 Skill name）：
- sales-contract-sign-dialog-diagnosis：排查销售合同发起时弹窗提示"请先完成报价"或"无定软电报价"的原因

其他路由规则：
- 用户想查询数据（订单、合同、节点、报价等）→ 只回复 "query"
- 用户询问系统配置、本体模型、实体列表、环境信息，或"怎么做"等操作性问题 → 只回复 "admin"
- 意图不明确，无法判断想做什么 → 只回复 "unclear"

用户问题: %s

注意：只回复一个单词（Skill name 或 query/admin/unclear），不要其他文字。
```

**理由**：Skill 的 `description` 字段天然承载语义触发信息，比 RouterNode 中硬编码的"弹窗提示"关键词更准确、更完整。

---

### 决策 3：`RouterNode` 路由目标映射规则

RouterNode 从 `SkillRoutingStrategy.route()` 得到结果后：
- 返回值是某个 Skill name → `routingTarget = "investigateAgent"`，同时写入 `selectedSkill = <skillName>`
- 返回值是 `"query"` → `routingTarget = "queryAgent"`
- 返回值是 `"admin"` / `"unclear"` / 其他 → `routingTarget = "admin"`

**State 变更**：`SREAgentGraphConfiguration` 新增 `selectedSkill` 字段（ReplaceStrategy）。

---

### 决策 4：investigateAgent 接收 `selectedSkill`

investigateAgent 的系统 Prompt 中删除 Skill 触发规则表，改为说明"selectedSkill 已由路由器预选，直接调用 readSkill(selectedSkill)"。

`AgentConfiguration` 构建 investigateAgent 时，将 `selectedSkill` 作为动态注入变量支持（通过 state 传递，investigateAgent 在 `AgentNode` 执行时可从 state 读取）。

**AgentNode 变更**：调用 `agent.streamMessages()` 前，从 state 读取 `selectedSkill`，拼入用户输入，格式：`{userInput}\n[selectedSkill: {skillName}]`。

---

### 决策 5：`buildSkillsList` 改为动态读取

将 `AgentConfiguration.buildSkillsList()` 改为：
```java
private String buildSkillsList(SkillRegistry skillRegistry) {
    StringBuilder sb = new StringBuilder("## Available Skills\n\n");
    skillRegistry.listAll().forEach(skill ->
        sb.append("- ").append(skill.getName()).append(": ").append(skill.getDescription()).append("\n")
    );
    return sb.toString();
}
```

## Risks / Trade-offs

| 风险 | 缓解措施 |
|------|---------|
| Skill 增多后路由 Prompt 变长，token 消耗增加、分类准确率下降 | 通过 `SkillRoutingStrategy` 接口，未来可切换为 embedding 实现，无需改动 RouterNode |
| investigateAgent 接收 `selectedSkill` 的注入方式依赖 AgentNode 字符串拼接，较脆 | investigate-agent.md 中明确说明 selectedSkill 标注格式，后续可考虑结构化参数传递 |
| LLM 偶发返回非预期值（如多词）时路由到 admin | RouterNode fallback 到 admin 是安全降级，用户会收到引导，不会报错 |
