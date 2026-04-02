# session-context-memory Specification

## Purpose

为 SRE-Agent 增加会话上下文记忆能力，解决意图识别不准确、多轮对话上下文丢失的问题，同时提供观测能力评估记忆效果，支持总开关控制。

## Requirements

### Requirement: 会话上下文管理

SRE-Agent SHALL 实现会话上下文管理，支持用户画像和对话历史存储。

#### Scenario: 首次会话
- **WHEN** 用户首次发起请求，无 sessionId
- **THEN** 系统根据 X-User-Id 或 UUID 生成 sessionId
- **AND** 创建新的 SessionContext，包含用户画像和空历史

#### Scenario: 会话 ID 生成优先级
- **WHEN** 用户发起请求
- **THEN** sessionId 生成遵循以下优先级：
  1. 前端传入 X-Session-Id header（支持多标签页）
  2. X-User-Id + 时间戳（同一用户新会话）
  3. temp_UUID前8位（匿名临时会话）

#### Scenario: 用户画像存储
- **WHEN** 用户发起查询或排查请求
- **THEN** 系统提取用户输入中的实体类型，更新用户画像
- **AND** 存储常用实体类型、查询模式等

### Requirement: 历史对话压缩存储

系统 SHALL 对历史对话进行压缩存储，保留最近 N 轮完整对话，更早轮次转为摘要。

#### Scenario: 历史轮次压缩
- **WHEN** 对话轮次超过 max-recent-turns（默认5轮）
- **THEN** 保留最近2轮完整对话
- **AND** 更早轮次转为压缩摘要，格式：[日期] Q:问题 -> 意图;

#### Scenario: 历史数据读取
- **WHEN** RouterNode 或 AgentNode 需要历史数据
- **THEN** Router 获取精简上下文（用户画像 + 摘要）
- **AND** Agent 获取完整上下文（最近2轮完整 + 摘要）

### Requirement: 置信度评估与主动确认

RouterNode SHALL 输出意图识别的置信度，低于阈值时主动确认。

#### Scenario: 高置信度路由
- **WHEN** 置信度 >= confidence-threshold（默认0.6）
- **THEN** 正常路由到对应 Agent
- **AND** 传递预提取的参数给 Agent

#### Scenario: 低置信度确认
- **WHEN** 置信度 < confidence-threshold
- **THEN** 路由到 admin 节点
- **AND** 返回确认问题给用户："您是不是想查询...？"

### Requirement: 预提取参数传递

RouterNode SHALL 在意图识别时同步提取关键参数，传递给 Agent。

#### Scenario: 参数提取
- **WHEN** Router 进行意图识别
- **THEN** 同时提取 entity、value、queryScope 等参数
- **AND** 通过 state 传递给目标 Agent

#### Scenario: 参数使用
- **WHEN** Agent 接收请求
- **THEN** 可以选择使用预提取参数或自行解析
- **AND** 提示词引导 Agent 需要实时数据时重新查询

### Requirement: 记忆机制总开关

系统 SHALL 提供配置开关控制记忆功能开启/关闭。

#### Scenario: 开关开启
- **WHEN** sreagent.session.enabled = true
- **THEN** 启用会话上下文管理
- **AND** Router 和 Agent 使用历史上下文

#### Scenario: 开关关闭
- **WHEN** sreagent.session.enabled = false
- **THEN** 禁用会话上下文管理
- **AND** Router 和 Agent 行为与原版本一致，无历史数据

### Requirement: 存储类型配置

系统 SHALL 支持内存和 Redis 两种存储类型。

#### Scenario: 内存模式
- **WHEN** sreagent.session.store-type = memory
- **THEN** 使用 ConcurrentHashMap 存储会话上下文
- **AND** 适用于开发测试环境

#### Scenario: Redis 模式
- **WHEN** sreagent.session.store-type = redis
- **THEN** 使用 Redis 持久化会话上下文
- **AND** 支持跨进程、跨实例共享

### Requirement: 观测与评估能力

系统 SHALL 记录记忆相关指标，支持评估记忆效果。

#### Scenario: 意图识别评估
- **WHEN** 每次 Router 意图识别
- **THEN** 记录 intent、confidence、extractedParams
- **AND** 便于分析意图识别准确率

#### Scenario: 历史使用评估
- **WHEN** Agent 使用历史上下文
- **THEN** 记录历史轮次数、上下文长度
- **AND** 评估历史数据对结果的影响

#### Scenario: 会话活跃度统计
- **WHEN** 会话持续使用
- **THEN** 统计 totalTurns、queryCount、investigateCount
- **AND** 便于分析用户使用模式

### Requirement: 数据时效性处理

Agent 获取历史数据时 SHALL 识别数据可能过时的情况。

#### Scenario: 需要实时数据
- **WHEN** 用户问题包含"状态"、"现在"、"当前"、"最新"等词
- **THEN** 提示词引导 Agent 重新查询获取最新数据
- **AND** 不直接使用历史数据作为答案

## Configuration

### application.yml 配置项

```yaml
sreagent:
  session:
    # 总开关：开启/关闭记忆功能
    enabled: ${SESSION_ENABLED:true}
    # 存储方式：memory / redis
    store-type: ${SESSION_STORE_TYPE:memory}
    # 会话过期时间（小时）
    expire-hours: 24
    # 最近保留轮次数
    max-recent-turns: 5
    # 意图识别置信度阈值
    confidence-threshold: 0.6
    
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}
```

### 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| SESSION_ENABLED | true | 记忆功能总开关 |
| SESSION_STORE_TYPE | memory | 存储类型 |
| REDIS_HOST | localhost | Redis 地址 |
| REDIS_PORT | 6379 | Redis 端口 |

## Key Files

| 文件 | 操作 | 描述 |
|------|------|------|
| `infrastructure/session/SessionContext.java` | 新增 | 会话上下文模型 |
| `infrastructure/session/SessionContextManager.java` | 新增 | 会话上下文管理器 |
| `config/node/RouterNode.java` | 修改 | 集成上下文 + 置信度评估 |
| `config/node/AgentNode.java` | 修改 | 接收上下文 + 参数注入 |
| `config/SREAgentGraphConfiguration.java` | 修改 | 配置 SessionContextManager |
| `trigger/http/ChatController.java` | 修改 | sessionId 生成与传递 |

## Expected Outcomes

| 指标 | 改进前 | 改进后 |
|------|--------|--------|
| 意图识别准确率 | ~70% | ~85% |
| 参数提取成功率 | N/A | ~80% |
| 多轮对话理解 | 无 | 保留历史上下文 |
| 模糊输入处理 | 随机路由 | 主动确认 |