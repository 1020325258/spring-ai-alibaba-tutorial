# QA 评估测试重设计

## 背景

当前 `QaPairEvaluationIT` 的评估方式暴露了实现细节（tool_call、json_output 类型），维护成本高且不够直观。目标是简化为纯自然语言的问答对维护，通过 LLM-as-Judge 进行语义评估。

## 设计决策

| 决策项 | 选择 | 理由 |
|--------|------|------|
| Judge 模型 | Qwen-Turbo | 成本低、速度快，评估任务足够 |
| 报告位置 | 覆盖 `docs/test-execution-report.md` | 统一入口 |
| 重试机制 | 无重试 | 简单直接，temperature=0 保证稳定性 |
| expected 格式 | 纯自然语言 | 移除所有结构化字段，降低维护门槛 |

## YAML 格式

```yaml
qa-pairs:
  - id: "query-contract-basic"
    question: "C1767173898135504的合同基本信息"
    expected: "返回该合同的基本信息，应包含合同号、合同状态等核心字段"

  - id: "query-order-contracts"
    question: "825123110000002753下的合同"
    expected: "返回该订单下的所有合同，每个合同应包含合同号"

  - id: "investigate-sign-dialog-no-quote"
    question: "订单825123110000002753发起提示无定软电报价，帮我排查"
    expected: "给出排查分析结论，说明该订单是否缺少个性化报价，并给出判断依据"
```

**变化：**
- 移除 `type/tool/params/mustContain` 所有结构化字段
- `expected` 改为纯自然语言描述

## 组件结构

```
src/test/java/com/yycome/sreagent/e2e/
├── QaPair.java              # 简化：id + question + expected
├── QaPairLoader.java        # 保持不变（解析逻辑简化）
├── EvaluationJudge.java     # 新增：调用 Qwen-Turbo 评估
├── QaEvaluationReporter.java # 新增：生成 Markdown 报告
├── QaPairEvaluationIT.java  # 简化：移除结构化断言逻辑
└── BaseSREAgentIT.java      # 移除 tool_call/json_output 验证方法
```

### EvaluationJudge

```java
public record JudgeResult(boolean pass, String reason) {}

public JudgeResult evaluate(String question, String actualOutput, String expected) {
    String prompt = buildJudgePrompt(question, actualOutput, expected);
    // 调用 Qwen-Turbo，temperature=0
    ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
    return parseJudgeResult(response);
}
```

### QaEvaluationReporter 输出格式

```markdown
# SRE-Agent QA 评估报告

> 执行时间: 2026-03-31 15:30:00

## 统计

| 状态 | 数量 |
|------|------|
| ✅ 通过 | 3 |
| ❌ 失败 | 1 |

---

## 详细结果

### ✅ query-contract-basic

**输入:** C1767173898135504的合同基本信息

**预期:** 返回该合同的基本信息，应包含合同号、合同状态等核心字段

**实际输出:**
```json
{"queryEntity":"Contract",...}
```

**评估结果:** ✅ 通过 — 输出包含合同号C1767...和状态字段，符合预期

---

### ❌ query-order-contracts

...
```

## Judge Prompt

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

**稳定性保障：**
- `temperature=0` 固定
- 输出格式严格 JSON，便于解析
- reason 限制字数避免过长输出

## 执行流程

```
┌─────────────────────────────────────────────────────────────┐
│                    QaPairEvaluationIT                       │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  1. QaPairLoader.load("qa-pairs/sre-agent-qa.yaml")         │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  2. 对每个 QaPair:                                           │
│     ├─ ask(question) → actualOutput                         │
│     └─ EvaluationJudge.evaluate(question, actual, expected) │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  3. 收集所有 JudgeResult，断言全部 pass                       │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  4. QaEvaluationReporter.generate(results)                  │
│     → 写入 docs/test-execution-report.md                    │
└─────────────────────────────────────────────────────────────┘
```

**关键点：**
- Judge 在每个测试用例内同步调用，失败立即抛断言
- 报告在所有测试完成后统一生成（`@AfterAll`）
- Qwen-Turbo 配置通过代码硬编码，无需 application.yml 改动

## 待删除代码

- `QaPair.Expected` 内部类及其 `isToolCallType()/isJsonOutputType()/isNaturalLanguageType()` 方法
- `BaseSREAgentIT` 中的 `assertToolCallExpected()/assertJsonOutputExpected()/assertNaturalLanguageExpected()` 方法
- `BaseSREAgentIT` 中的 `assertToolCalled()/assertToolParamEquals()/assertOutputField()/assertOutputHasRecords()` 方法
