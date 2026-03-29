## Context

SRE-Agent 使用本体论驱动的查询引擎（OntologyQueryEngine），通过 `domain-ontology.yaml` 定义实体关系图，每个实体对应一个 Gateway 实现。

**改动前状态**：
- `SignableOrderInfo` 挂在 Contract 下（Contract → SignableOrderInfo，via contractCode）
- `SignableOrderInfoGateway.queryByFieldWithContext` 从 parentRecord（Contract）中读取 `type` 和 `projectOrderId`，根据 type=8/type=3 路由到不同接口
- 查询路径：`ontologyQuery(entity=Order, queryScope=SignableOrderInfo)` 需要两跳：Order → Contract → SignableOrderInfo
- 响应解析代码错误地在 `data[]` 层读字段，实际数据在 `data[].signableOrderInfos[]`

**核心问题**：`sign-order-list` 接口只需要 `projectOrderId`，contractType 路由是误导性设计，实际上弹窗可签约S单属于订单维度而非合同维度。

## Goals / Non-Goals

**Goals:**
- 将 SignableOrderInfo 正确挂载在 Order 下，实现一跳直查
- 简化 Gateway 实现，去掉不必要的合同类型路由分支
- 修复响应解析逻辑，正确读取 `data[].signableOrderInfos[]`
- 完善 SKILL.md 的排查流程，覆盖"无定软电报价"场景

**Non-Goals:**
- 不处理 type=3（正签合同）的可签约S单查询（业务上暂无需求）
- 不改变 OntologyQueryEngine 的核心逻辑
- 不修改 `sign-order-list` 以外的 HTTP 接口定义

## Decisions

### 决策1：SignableOrderInfo 挂 Order 还是 Contract？

**选择**：挂 Order（`Order → SignableOrderInfo` via `projectOrderId`）

**理由**：
- `sign-order-list` 接口参数只有 `projectOrderId`，与合同无关
- "弹窗可签约S单"在业务上属于整个订单的维度，不特属于某一份合同
- 避免 Gateway 依赖 parentRecord 的 `type` 字段（脆弱的隐式耦合）

**放弃的方案**：保留 Contract → SignableOrderInfo + 另加 Order → SignableOrderInfo 双路径 → 冗余，维护成本高

### 决策2：Gateway 是否保留 contractType 路由？

**选择**：完全移除

**理由**：type=3（正签合同）端点未知且未配置，保留空分支徒增代码复杂度；业务需求明确只查销售合同可签约S单

### 决策3：响应解析层级

`sign-order-list` 接口返回结构：
```
data[]                     # 公司分组（companyName, companyCode, signableOrderInfos[]）
  └── signableOrderInfos[] # 真实S单数据（goodsInfo, bindCode, orderAmount 等）
```

**选择**：双层遍历，外层取公司信息，内层取S单详情，合并到同一条记录输出

### 决策4：SKILL.md 决策矩阵扩充

原矩阵只区分"弹窗空/有数据"，新矩阵在"弹窗空"下细分：
- 无 S 单 → 未报价或报价未下单（领域归属：报价或订单）
- 全部 9001/9002 → 已取消/退款（领域归属：报价或订单）
- 有有效 S 单 → 接口异常（领域归属：合同）

## Risks / Trade-offs

- **本体模型变更对运行中服务透明**：EntityRegistry 在启动时加载 YAML，变更后需重启服务才生效 → 影响范围小，接受
- **alias 扩充影响 LLM 识别**：新增"可签约S单"别名后，LLM 更易将自然语言映射到 SignableOrderInfo → 正向影响，无风险

## Migration Plan

1. 更新 `domain-ontology.yaml`（删旧关系，加新关系，更新实体属性）
2. 重写 `SignableOrderInfoGateway.java`（简化 + 修复解析）
3. 更新 `sre-agent.md` 提示词（决策样例表新增行）
4. 更新 `SKILL.md`（触发条件、查询路径、决策矩阵、Example）
5. 补充/修复测试（QueryAgentIT、InvestigateAgentIT）
6. 重启应用服务，刷新 ontology 页面验证图结构正确
