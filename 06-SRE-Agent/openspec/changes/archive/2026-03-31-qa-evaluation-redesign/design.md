## Context

当前 `QaPairEvaluationIT` 采用结构化断言验证 Agent 输出：
- `tool_call` 类型：验证工具名和参数
- `json_output` 类型：验证 JSON 字段
- `natural_language` 类型：关键词匹配

这种方式存在以下问题：
1. 暴露实现细节（工具名、参数结构）
2. 维护成本高，业务人员难以理解
3. 提示词调整后需要同步修改 YAML 结构

新设计引入 LLM-as-Judge 机制，使用独立轻量模型进行语义评估。

## Goals / Non-Goals

**Goals:**
- 将 `expected` 简化为纯自然语言描述
- 使用 Qwen-Turbo 进行语义评估（temperature=0）
- 测试完成后生成完整 Markdown 报告
- 提高测试稳定性（无重试，依赖 prompt 质量和 temperature=0）

**Non-Goals:**
- 不实现重试或多数投票机制
- 不修改 application.yml 配置（模型硬编码）
- 不支持混合模式（自然语言 + 结构化断言）

## Decisions

### D1: Judge 模型选择 Qwen-Turbo

**备选方案：**
| 方案 | 优点 | 缺点 |
|------|------|------|
| 复用 Agent ChatModel | 无额外配置 | 成本高、响应慢 |
| Qwen-Turbo | 成本低、速度快 | 需单独配置 |
| Qwen-Plus | 全能、性价比高 | 成本略高于 Turbo |

**决策：** 选择 Qwen-Turbo，评估任务是简单语义判断，无需复杂推理。

### D2: Judge 输出格式

**决策：** 严格 JSON 格式 `{pass: boolean, reason: string}`

**理由：**
- 便于解析，避免正则提取不可靠
- reason 限制 30 字，避免过长输出影响响应时间

### D3: Judge Prompt 设计

```
你是一个输出评估器。判断【实际输出】是否满足【预期描述】的语义要求。

## 输入
- 问题：{question}
- 预期：{expected}
- 实际输出：{actualOutput}

## 判断规则
1. 语义一致即可，不要求措辞完全相同
2. 实际输出包含预期描述要求的核心信息即为通过
3. 只有明确缺失预期要求的内容才判定为不通过
4. 如果预期描述了多个要求，全部满足才通过

## 输出格式
严格输出以下 JSON，不要输出其他内容：
{"pass": true, "reason": "简要说明判断依据（30字以内）"}
```

### D4: 报告格式

**决策：** 覆盖 `docs/test-execution-report.md`

**结构：**
```markdown
# SRE-Agent QA 评估报告

> 执行时间: YYYY-MM-DD HH:mm:ss

## 统计

| 状态 | 数量 |
|------|------|
| ✅ 通过 | N |
| ❌ 失败 | M |

---

## 详细结果

### ✅/❌ {id}

**输入:** {question}

**预期:** {expected}

**实际输出:**
```
{actualOutput}
```

**评估结果:** ✅/❌ 通过/失败 — {reason}

---
```

## Risks / Trade-offs

### R1: Judge LLM 非确定性

**风险：** 同一评估不同次判定可能不同

**缓解：**
- temperature=0 固定
- prompt 具体化，避免模糊预期描述
- 维护者需确保 `expected` 描述足够具体

### R2: 后端数据变化导致测试失败

**风险：** 合同/订单数据被修改，导致实际输出与预期不符

**缓解：**
- `expected` 描述验证结构而非具体值
- 例如："返回合同信息，包含合同号字段" 而非 "合同号=C123"

### R3: Token 消耗线性增长

**风险：** 每个 QA pair 调用两次 LLM（Agent + Judge）

**缓解：**
- Judge 使用轻量模型 Qwen-Turbo
- 仅在特定时机运行（非每次 CI）
