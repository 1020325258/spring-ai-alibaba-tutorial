## ADDED Requirements

### Requirement: RouterNode 支持直接回答用户问题
RouterNode SHALL 在意图分类结果为 `answer` 或置信度低于阈值时，直接生成回答并写入 `state["result"]`，无需路由到其他 Agent 节点。

#### Scenario: answer 意图直接回答
- **WHEN** LLM 将用户输入分类为 `answer` 意图
- **THEN** RouterNode 调用 `generateDirectAnswer()` 生成回答
- **AND** 将回答写入 `state["result"]`
- **AND** 将 `routingTarget` 设为 `"done"`

#### Scenario: 低置信度直接回答
- **WHEN** LLM 分类置信度低于 `confidenceThreshold`（默认 0.6）
- **AND** 意图不是 `admin`
- **THEN** RouterNode 直接生成回答，不路由到 AdminNode

#### Scenario: 图流程在直接回答后终止
- **WHEN** `routingTarget == "done"`
- **THEN** StateGraph 通过 `"done" → END` 条件边直接终止，不经过其他节点

### Requirement: RouterNode 直接回答包含系统能力介绍
RouterNode 在生成直接回答时 SHALL 将 SkillRegistry（排查能力列表）和 EntityRegistry（可查询实体列表）注入到 LLM prompt，以便准确回答用户询问系统功能的问题。

#### Scenario: 用户询问系统能力
- **WHEN** 用户输入"有什么功能"、"能帮我做什么"等能力询问
- **THEN** RouterNode 的回答中包含可用的排查 Skill 描述和可查询实体名称
- **AND** 不暴露技术名称（如 `queryAgent`、`ontologyQuery`）

#### Scenario: 用户输入模糊时引导
- **WHEN** 用户输入过短或表述模糊（置信度不足）
- **THEN** RouterNode 输出引导性文本，提示用户描述具体业务需求

### Requirement: AdminNode 仅处理后台管理操作
AdminNode SHALL 仅处理环境查看和切换操作，不再承担 LLM 调用或用户引导职责。

#### Scenario: 环境切换命令路由到 admin
- **WHEN** LLM 将用户输入分类为 `admin` 意图（包含"环境"、"switch"、"env"等关键词）
- **THEN** RouterNode 将 `routingTarget` 设为 `"admin"`，路由到 AdminNode

#### Scenario: AdminNode 纯代码处理环境命令
- **WHEN** AdminNode 收到环境相关输入
- **THEN** 通过规则匹配（精确匹配 → 别名匹配 → 描述关键词匹配）识别目标环境并切换
- **AND** 不发起任何 LLM 调用

### Requirement: 前端事件区分路由指示器与直接回答内容
SREAgentEventDispatcher SHALL 根据 `routingTarget` 的值，对 `router` 节点输出不同类型的 SSE 事件。

#### Scenario: 路由场景输出路由指示器事件
- **WHEN** router 节点完成，`routingTarget` 为 `queryAgent`/`investigateAgent`/`admin`
- **THEN** 输出 ThinkingEvent（含 stepTitle: "路由至 X"）

#### Scenario: 直接回答场景输出内容事件
- **WHEN** router 节点完成，`routingTarget == "done"`
- **THEN** 输出 Markdown 内容事件（含 `nodeName`, `displayTitle`, `content`）
