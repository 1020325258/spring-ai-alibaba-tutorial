## ADDED Requirements

### Requirement: SREAgentNodeName 枚举定义
系统 SHALL 提供 SREAgentNodeName 枚举，统一管理 SRE-Agent 所有节点的名称和展示标题。

#### Scenario: 枚举包含所有 Agent 节点
- **WHEN** 代码中引用 SREAgentNodeName 枚举
- **THEN** 枚举包含以下节点：router（意图识别）、queryAgent（数据查询）、investigateAgent（问题排查）、admin（智能推荐）、tool_call（工具调用）

#### Scenario: 枚举提供 displayTitle 映射
- **WHEN** 调用 SREAgentNodeName.displayTitle(nodeName)
- **THEN** 返回对应的中文展示标题（如 "router" → "意图识别"）

### Requirement: 节点名称解析
系统 SHALL 提供根据 nodeName 字符串解析为 SREAgentNodeName 枚举的能力。

#### Scenario: 有效 nodeName 解析
- **WHEN** 调用 SREAgentNodeName.fromNodeName("queryAgent")
- **THEN** 返回 QUERY_AGENT 枚举值

#### Scenario: 无效 nodeName 处理
- **WHEN** 调用 SREAgentNodeFromNodeName("unknown")
- **THEN** 返回 null（而非抛异常）