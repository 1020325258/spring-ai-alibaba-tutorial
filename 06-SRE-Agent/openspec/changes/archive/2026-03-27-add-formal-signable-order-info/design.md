## Context

SRE-Agent 已支持从订单号直查销售合同可签约S单（`SignableOrderInfo`，type=8），通过 `sign-order-list` 接口查询。正签合同（type=3）的可签约S单由另一个接口提供（`formalQuotation/list/v2?contractType=3`），返回结构完全相同（`data[].signableOrderInfos[]`）。

`QueryScope.java` 枚举对未知实体名返回 `null`，引擎自动通过本体图动态路由，无需修改枚举。

**改动前**：用户询问正签合同可签约S单 → 无匹配实体 → 无法响应

## Goals / Non-Goals

**Goals:**
- 新增 `FormalSignableOrderInfo` 实体，挂 Order 下，一跳直查
- 复用 `SignableOrderInfo` 的 Gateway 结构和响应解析逻辑
- LLM 能通过自然语言（"正签合同的可签约S单"）映射到正确 queryScope

**Non-Goals:**
- 不合并 `SignableOrderInfo` 和 `FormalSignableOrderInfo`（两者接口不同，职责分离更清晰）
- 不处理 type 路由到同一实体的动态分发（避免引入复杂的条件逻辑）

## Decisions

### 决策1：独立实体 vs. 复用 SignableOrderInfo + type 参数

**选择**：独立实体 `FormalSignableOrderInfo`

**理由**：
- `ontologyQuery` 工具不支持透传自定义参数（如 `contractType`）到 Gateway
- 独立实体符合本体模型"一实体一接口"的设计原则
- LLM 可通过实体名直接区分，无需额外参数推断

**放弃的方案**：在 `SignableOrderInfo` Gateway 中增加 contractType 分支 → 需要 parentRecord 或额外参数，引入脆弱耦合

### 决策2：响应解析复用

`formalQuotation/list/v2` 与 `sign-order-list` 返回结构相同（`data[].signableOrderInfos[]`），直接复制 `parseSignableOrders` 方法，不抽取公共基类（目前只有两个实现，过早抽象无益）。

## Risks / Trade-offs

- **实体增多使本体图更复杂**：每增加一种合同类型的可签约S单就需要一个实体 → 目前仅两种类型（type=8/type=3），可接受；若未来增多再考虑重构
- **`QueryScope` 枚举未同步**：动态路由依赖"未知 scope 返回 null"的隐式约定 → 若枚举语义变更需同步审查所有动态实体
