# SRE-Agent 会话上下文记忆机制增强方案

## 一、背景与问题

### 1.1 当前问题

| 问题 | 影响 |
|------|------|
| Router 无上下文 | 仅看当前输入，无法理解"然后"类多轮对话 |
| 无置信度评估 | 识别是否可靠未知，低质量输入也强制路由 |
| 无参数提取 | Agent 需重复解析参数，浪费 token |
| 数据过时风险 | 历史查询结果可能导致错误回答 |

### 1.2 参考 Claude Code

Claude Code 的核心机制：
- QueryEngine 管理多轮对话上下文
- 系统提示词包含历史摘要
- 工具调用前有参数提取和确认机制
- 通过提示词引导 Agent 自主判断数据时效性

---

## 二、目标

1. **意图识别增强** - 结合用户画像 + 历史摘要，提升准确率
2. **参数预提取** - Router 同步提取参数，减少 Agent 重复工作
3. **多轮对话理解** - 保留历史上下文，理解指代关系
4. **置信度评估** - 模糊输入主动确认，而非随机路由
5. **数据时效性** - 引导 Agent 自主判断何时需要重新查询
6. **总开关控制** - 支持功能开启/关闭，便于评估效果
7. **观测能力** - 记录指标，评估记忆效果

---

## 三、系统架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           完整执行流程                                   │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│ 1. 用户请求                                                             │
│    POST /chat (X-User-Id: user123, message: "查订单825123")            │
└─────────────────────────────────┬───────────────────────────────────────┘
                                  ↓
┌─────────────────────────────────────────────────────────────────────────┐
│ 2. ChatController 生成 SessionId                                       │
│    优先级: X-Session-Id > X-UserId+timestamp > UUID                    │
└─────────────────────────────────┬───────────────────────────────────────┘
                                  ↓
┌─────────────────────────────────────────────────────────────────────────┐
│ 3. SessionContextManager 获取会话上下文                                  │
│    - 用户画像 (frequentEntities, queryPatterns)                        │
│    - 历史对话 (recentTurns, historySummary)                             │
└─────────────────────────────────┬───────────────────────────────────────┘
                                  ↓
┌─────────────────────────────────────────────────────────────────────────┐
│ 4. RouterNode (记忆处理 + 意图识别)                                      │
│    输入: userInput + SessionContext.getRouterContext()                 │
│    输出: {intent, confidence, extractedParams, clarification}         │
│                                                                          │
│    - 置信度 >= 0.6 → 正常路由                                            │
│    - 置信度 < 0.6 → 返回确认问题                                          │
└─────────────────────────────────┬───────────────────────────────────────┘
                                  ↓
┌─────────────────────────────────────────────────────────────────────────┐
│ 5. AgentNode (使用记忆执行任务)                                          │
│    输入: input + SessionContext.getAgentContext() + extractedParams   │
│                                                                          │
│    构建增强输入:                                                         │
│    "## 历史对话\n%s\n\n## 预提取参数\n%s\n\n## 当前问题\n%s\n\n"         │
│    + "当用户询问'状态'、'现在'、'最新'时，请调用工具获取最新数据"        │
└─────────────────────────────────┬───────────────────────────────────────┘
                                  ↓
┌─────────────────────────────────────────────────────────────────────────┐
│ 6. 记录对话到 SessionContextManager                                     │
│    sessionContextManager.addTurn(sessionId, input, output, intent)     │
│    → 更新历史 → 压缩存储 → 保存到 Redis                                   │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 四、核心实现

### 4.1 新增文件

#### SessionContext.java

```java
package com.yycome.sreagent.infrastructure.session;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 会话上下文模型
 */
@Data
public class SessionContext {
    private String sessionId;
    private String userId;
    private String userType; // LOGIN / ANONYMOUS
    private LocalDateTime createdAt;
    private LocalDateTime lastActiveAt;
    
    // 用户画像
    private List<String> frequentEntities;   // 常用实体类型
    private List<String> queryPatterns;       // 查询模式
    private Map<String, String> paramDefaults; // 默认参数
    
    // 历史对话（压缩存储）
    private List<ConversationTurn> recentTurns;  // 最近 N 轮完整对话
    private String historySummary;              // 更早历史的压缩摘要
    private int totalTurns;                     // 总轮次数
    
    // 统计数据
    private int queryCount;
    private int investigateCount;
    
    @Data
    public static class ConversationTurn {
        private String userInput;
        private String agentOutput;
        private String intent;
        private LocalDateTime timestamp;
    }
    
    public void addTurn(String userInput, String agentOutput, String intent) {
        // 追加轮次
        // 超过 5 轮时压缩历史（保留最近 2 轮 + 更早摘要）
    }
    
    // 获取 Router 需要的精简上下文
    public String getRouterContext() { ... }
    
    // 获取 Agent 需要的完整上下文
    public String getAgentContext() { ... }
}
```

#### SessionContextManager.java

```java
package com.yycome.sreagent.infrastructure.session;

@Component
public class SessionContextManager {
    // 内存缓存 + Redis 持久化
    // 支持 memory / redis 两种存储类型
    
    public SessionContext getOrCreate(String sessionId) { ... }
    public void save(SessionContext context) { ... }
    public void addTurn(String sessionId, String input, String output, String intent) { ... }
}
```

### 4.2 修改文件

#### RouterNode.java

- 集成 SessionContextManager
- 增强 Prompt：输入包含用户画像 + 历史摘要
- 输出 JSON 格式：{intent, confidence, extractedParams, clarification}
- 置信度 < 0.6 时路由到 admin 返回确认

#### AgentNode.java

- 从 state 获取完整上下文
- 接收预提取参数
- 注入提示词引导数据时效性判断
- 执行完成后记录对话历史

#### ChatController.java

- 生成/接收 sessionId
- 传递给 Graph 执行

---

## 五、配置项

### application.yml

```yaml
sreagent:
  session:
    # 总开关
    enabled: ${SESSION_ENABLED:true}
    # 存储类型
    store-type: ${SESSION_STORE_TYPE:memory}
    # 会话过期时间（小时）
    expire-hours: 24
    # 最近保留轮次数
    max-recent-turns: 5
    # 置信度阈值
    confidence-threshold: 0.6
    
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
```

### 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| SESSION_ENABLED | true | 记忆功能总开关 |
| SESSION_STORE_TYPE | memory | 存储类型 |
| REDIS_HOST | localhost | Redis 地址 |
| REDIS_PORT | 6379 | Redis 端口 |

---

## 六、观测能力

### 记录指标

| 指标 | 说明 |
|------|------|
| intent | 识别出的意图 |
| confidence | 置信度 |
| extractedParams | 提取的参数 |
| totalTurns | 会话轮次 |
| historyLength | 历史上下文长度 |

### 评估方式

1. **A/B 测试**：开启 vs 关闭，对比意图识别准确率
2. **置信度分布**：分析置信度 < 0.6 的比例
3. **历史使用率**：Agent 使用历史上下文的频率

---

## 七、预期效果

| 指标 | 改进前 | 改进后 |
|------|--------|--------|
| 意图识别准确率 | ~70% | ~85% |
| 参数提取成功率 | N/A | ~80% |
| 多轮对话理解 | 无 | 完整上下文 |
| 模糊输入处理 | 随机路由 | 主动确认 |

---

## 八、实现顺序

| Phase | 任务 | 输出 |
|-------|------|------|
| 1 | 会话上下文管理 | SessionContext + SessionContextManager |
| 2 | Router 增强 | RouterNode 修改 + Prompt 优化 |
| 3 | Agent 上下文传递 | AgentNode 修改 |
| 4 | 配置与开关 | application.yml + 配置类 |
| 5 | 测试验证 | 集成测试 + 手工测试 |

---

## 九、关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 存储方式 | 配置可选 (memory/redis) | 开发测试用 memory，生产用 redis |
| 置信度阈值 | 0.6 | 平衡准确率和用户体验 |
| 参数传递 | 同时传递（可用可不用） | 灵活性高 |
| 数据时效性 | 提示词引导，非硬编码 | Claude 的做法，LLM 自主判断 |
| 总开关 | sreagent.session.enabled | 方便 A/B 测试评估效果 |