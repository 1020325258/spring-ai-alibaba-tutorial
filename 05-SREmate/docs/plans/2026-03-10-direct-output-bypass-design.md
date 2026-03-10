# 数据查询旁路输出优化设计

- **日期**：2026-03-10
- **状态**：已实现

---

## 问题背景

SREmate 的典型使用场景之一是数据查询（如查合同数据、订单信息）。原始流程如下：

```
用户输入
  → LLM 决策调用工具
  → 工具执行（DB 查询，通常 < 200ms）
  → LLM 接收工具结果，生成自然语言归纳（1~5 秒）
  → 流式输出归纳文字
```

其中 **LLM 归纳阶段耗时最长**，但对于纯数据查询请求，用户需要的是原始 JSON 数据，LLM 的归纳不仅没有附加价值，还引入了显著延迟。

---

## 设计方案

### 核心思路

在工具调用完成后、LLM 开始归纳之前，将工具结果"截获"并直接输出，取消后续的 LLM 流式生成。

### 时序分析

Spring AI 流式调用（`ChatClient.stream()`）的内部时序如下：

```
[阶段 1] LLM 决策工具调用        → 无 content token 输出
[阶段 2] Spring AI 同步执行工具   → DirectOutputHolder.set() 在此阶段被调用
[阶段 3] 工具结果回传 LLM
[阶段 4] LLM 生成归纳文字        → doOnNext 开始接收 content token
```

**关键观察**：当 `doOnNext` 收到第一个 content token 时，所有工具调用已经完成。此时检查 `DirectOutputHolder` 是否有值，若有，说明本次请求属于数据查询，可以立即取消流并打印直接结果。

### 组件设计

#### DirectOutputHolder

数据查询工具的结果临时存储区，由 `ObservabilityAspect` 负责写入。

**线程安全要求**：工具调用运行在 Spring AI 的调度线程，`doOnNext` 运行在 Reactor 的调度线程，两者不是同一线程。因此必须使用 `AtomicReference` 而非 `ThreadLocal`。

```
原实现：ThreadLocal<String>   ← 跨线程不可见，旁路逻辑永远失效
新实现：AtomicReference<String>  ← 跨线程可见，保证正确性
```

#### ObservabilityAspect（已有，无需修改）

AOP 切面拦截所有 `@Tool` 方法。对白名单内的数据查询工具，在调用成功后将结果写入 `DirectOutputHolder`：

```java
if (isDataQuery && result instanceof String) {
    directOutputHolder.set((String) result);
}
```

**数据查询工具白名单**（`DATA_QUERY_TOOLS`）：
- `queryContractData`
- `queryContractsByOrderId`
- `queryContractInstanceId`
- `queryContractFormId`
- `queryContractConfig`
- `querySubOrderInfo`

#### SREConsole（旁路逻辑接入点）

在每次请求开始前清理上一次的残留状态，在 `doOnNext` 的首个 token 处检查旁路条件：

```
每次请求开始
  → directOutputHolder.clear()          清理残留
  → 启动流式订阅
  → doOnNext 首个 token 到达
      ├─ directOutputHolder.hasOutput() == true
      │     → 打印直接输出
      │     → 取消订阅（dispose）
      │     → 跳过 LLM 归纳
      └─ directOutputHolder.hasOutput() == false
            → 正常流式输出
```

### 完整数据流

```
数据查询请求（如"826030911000002645合同数据"）

用户输入 → LLM → 工具调用（queryContractsByOrderId）
                      ↓
              ObservabilityAspect.logToolCall()
                      ↓
              directOutputHolder.set(jsonResult)    ← AtomicReference 写入
                      ↓
              LLM 开始生成归纳文字（第一个 token）
                      ↓
              SREConsole.doOnNext(chunk) 被触发
                      ↓
              directOutputHolder.hasOutput() == true
                      ↓
              打印 jsonResult，dispose() 取消订阅
                      ↓
              LLM 归纳被中止，节省 1~5 秒
```

---

## 效果

| 场景 | 优化前 | 优化后 |
|---|---|---|
| 数据查询（合同、订单） | DB 查询 + LLM 归纳（1~5s） | DB 查询（< 200ms） |
| 知识问答、分析推理 | 正常流式输出 | 不受影响，行为不变 |

---

## 实现文件

| 文件 | 变更内容 |
|---|---|
| `infrastructure/service/DirectOutputHolder.java` | `ThreadLocal` → `AtomicReference` |
| `trigger/console/SREConsole.java` | 请求前 `clear()`；`doOnNext` 接入旁路检测和取消逻辑 |
| `aspect/ObservabilityAspect.java` | 无需修改，已有写入逻辑 |

---

## 后续优化方向

### 1. 支持多工具串联的旁路

当前设计假设单次请求只调用一个数据查询工具。若 LLM 串联调用多个工具（如先查实例 ID 再查表单 ID），`DirectOutputHolder` 会被最后一个工具结果覆盖，旁路输出的是最后一个工具的结果，可能不完整。

**优化思路**：将 `AtomicReference<String>` 改为 `CopyOnWriteArrayList<String>`，收集所有工具结果后合并输出。

### 2. 细化旁路触发条件

当前以"白名单工具是否被调用"作为旁路条件。未来可在工具调用时附加元数据（如 `outputMode=DIRECT`），由工具自身声明是否走旁路，而非在 Aspect 里维护白名单。

### 3. 旁路输出格式化

当前直接输出原始 JSON。可以在旁路路径上增加轻量格式化（高亮 key、缩进美化），提升可读性，同时仍然保留"不经 LLM 归纳"的性能优势。

### 4. Watch 模式支持

未来若实现主动监听（watch 命令），每条告警分析也可能包含数据查询步骤。届时旁路逻辑需要在 watch 的输出循环中复用，建议将旁路检测逻辑提取为独立方法。
