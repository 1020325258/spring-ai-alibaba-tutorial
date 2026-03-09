# SREmate 增强设计文档

日期：2026-03-09

## 背景

SREmate 是基于 Spring AI Alibaba 的 SRE 值班助手，当前存在两个问题：

1. `querySubOrderInfo` 工具已实现但未在系统提示词中声明，Agent 无法感知并调用该工具
2. 数据查询场景下，LLM 在工具调用完成后还会生成一轮最终回答，造成不必要的延迟

---

## 任务一：修复 querySubOrderInfo 提示词缺失

### 问题描述

`ContractTool.querySubOrderInfo` 方法已完整实现，支持：
- `homeOrderNo`（订单号，必填）
- `quotationOrderNo`（报价单号 GBILL前缀，可选）
- `projectChangeNo`（变更单号，可选）

但 `src/main/resources/prompts/sre-agent.md` 的"可用工具"章节中完全没有该工具的说明，导致 Agent 不知道可以调用它。用户问"826030611000000795下GBILL260309110407580001的子单信息"时，Agent 无法正确响应。

**注意**：`sign-order-list`（查询可签约S单列表）和 `querySubOrderInfo`（查询子单基本信息）是两个不同接口，前者已在提示词中声明，后者未声明。

### 解决方案

在 `sre-agent.md` 的"可用工具"章节补充 `querySubOrderInfo` 的说明：
- 工具用途描述
- 参数说明（homeOrderNo必填，其余可选）
- 与 `sign-order-list` 的区别（避免 Agent 混淆）
- 触发场景示例

### 改动范围

仅修改 `src/main/resources/prompts/sre-agent.md`，无需改动 Java 代码。

---

## 任务二：数据查询场景性能优化 —— 跳过 LLM 最终回答生成

### 问题描述

当前完整流程：

```
用户输入 → LLM决策(工具路由+参数提取) → 工具执行(DB/HTTP) → LLM生成最终回答 → 输出
```

瓶颈在最后一步：LLM 拿到工具返回的 JSON 后，会再生成一轮自然语言回答（本质是把 JSON 转述一遍），这一步既耗时又没有价值（系统提示词已要求直接裸输出 JSON，LLM 的转述是多余的）。

### 设计方案

**保留** LLM 的工具路由和参数提取能力（这是 LLM 的核心价值）。
**移除** 数据查询类请求中 LLM 的最终回答生成步骤，工具结果直接输出给用户。

#### 请求分类

| 类型 | 示例 | 处理方式 |
|---|---|---|
| 数据查询类 | "查询合同C1772854666284956" | 工具结果直接输出，跳过LLM最终生成 |
| 诊断咨询类 | "数据库连接超时怎么办" | 保持现有流程，LLM生成分析建议 |

#### 实现架构

**核心机制**：利用已有的 `ObservabilityAspect`（AOP拦截所有 `@Tool` 调用）+ `TracingService`（已有工具调用结果存储），在 `SREConsole` 的流式输出层进行拦截判断。

```
用户输入
  │
  ▼
SREConsole.chat()
  │  启动流式输出，同时注册工具调用监听
  │
  ▼
LLM 决策阶段（工具路由 + 参数提取）
  │
  ▼
ObservabilityAspect 拦截 @Tool 调用
  │  工具执行完毕后，将结果写入 DirectOutputHolder（ThreadLocal）
  │  并标记本次请求类型为 DATA_QUERY
  │
  ▼
SREConsole 检测到 DirectOutputHolder 有值
  │  取消 LLM 流式输出的剩余部分（Subscription.cancel()）
  │  直接打印工具结果 JSON
  │
  ▼
输出完成
```

#### 关键组件设计

**1. DirectOutputHolder**（新增，infrastructure/service 层）

```java
// ThreadLocal 存储工具调用直接输出结果
public class DirectOutputHolder {
    private static final ThreadLocal<String> holder = new ThreadLocal<>();

    public static void set(String result) { holder.set(result); }
    public static String get() { return holder.get(); }
    public static boolean hasResult() { return holder.get() != null; }
    public static void clear() { holder.remove(); }
}
```

**2. 请求类型判断（数据查询 vs 诊断咨询）**

判断依据：工具是否属于数据查询工具集合。

数据查询工具白名单（在 ObservabilityAspect 中维护）：
- `queryContractData`
- `queryContractsByOrderId`
- `queryContractInstanceId`
- `queryContractFormId`
- `queryContractConfig`
- `querySubOrderInfo`
- `callPredefinedEndpoint`（部分场景）

诊断咨询工具（不拦截，继续走 LLM 生成）：
- `querySkills`
- `searchKnowledge`
- `listSkillCategories`
- 其他运维诊断类工具

**3. ObservabilityAspect 修改**

在工具调用后置处理中，判断工具类型：

```java
// 伪代码
@Around("@annotation(tool)")
public Object intercept(ProceedingJoinPoint pjp, Tool tool) throws Throwable {
    Object result = pjp.proceed();
    String methodName = pjp.getSignature().getName();

    if (isDataQueryTool(methodName)) {
        // 将结果写入 DirectOutputHolder，由 SREConsole 直接输出
        DirectOutputHolder.set(result.toString());
    }

    // 继续已有的 tracing 和 metrics 逻辑
    return result;
}
```

**4. SREConsole 修改**

在流式输出的 subscribe 回调中检测 DirectOutputHolder：

```java
// 伪代码（在当前 chatClient.stream() 的 subscribe 处）
AtomicBoolean directOutputDetected = new AtomicBoolean(false);

Flux<String> stream = sreAgent.prompt().user(input).stream().content();

stream.doOnNext(token -> {
    if (DirectOutputHolder.hasResult() && directOutputDetected.compareAndSet(false, true)) {
        // 直接输出工具结果，后续 LLM token 丢弃
        System.out.println(DirectOutputHolder.get());
    }
    // 若已检测到直接输出，不再打印 LLM token
    if (!directOutputDetected.get()) {
        System.out.print(token);
    }
})
.doFinally(signal -> DirectOutputHolder.clear())
.subscribe();
```

#### 边界情况处理

| 场景 | 处理方式 |
|---|---|
| 工具调用失败（抛异常） | DirectOutputHolder 不写入，走正常 LLM 错误回答流程 |
| 一次对话调用多个工具 | 取最后一个数据查询工具的结果输出 |
| 数据查询工具 + 诊断工具混合调用 | 以诊断工具为优先，走 LLM 生成 |
| ThreadLocal 泄漏 | doFinally 中强制 clear() |

### 改动范围

| 文件 | 变更类型 | 说明 |
|---|---|---|
| `infrastructure/service/DirectOutputHolder.java` | 新增 | ThreadLocal 容器 |
| `aspect/ObservabilityAspect.java` | 修改 | 工具后置处理中写入 DirectOutputHolder |
| `trigger/console/SREConsole.java` | 修改 | 流式输出中检测并优先展示工具结果 |

---

## 测试计划

### 任务一验证

```java
@Test
void querySubOrderInfo_shouldReturnSubOrderData() {
    String response = sreAgent.prompt()
        .user("826030611000000795下GBILL260309110407580001的子单信息")
        .call().content();

    assertThat(response).doesNotContain("没有找到对应的工具");
    // 验证工具被正确调用并返回数据
}
```

### 任务二验证

- 数据查询请求：验证响应时间明显低于当前（工具执行完毕即返回，不等待 LLM 生成）
- 诊断咨询请求：验证 LLM 仍正常生成分析建议，不受影响
- ThreadLocal 泄漏测试：多轮对话后验证无状态污染
