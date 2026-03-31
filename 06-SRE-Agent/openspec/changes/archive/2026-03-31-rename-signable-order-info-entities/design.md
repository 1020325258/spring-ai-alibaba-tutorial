## Context

本体论查询引擎以实体名（`entity name`）作为 `queryScope` 参数的合法值，LLM 在调用 `ontologyQuery` 时直接传入实体名字符串（如 `queryScope=SignableOrderInfo`）。实体名变更属于**外部可见的 API 变更**，会影响：
1. LLM 在 Prompt 引导下生成的参数值
2. `EntityRegistry` 按名称查找实体的路径

`SignableOrderInfoGateway.getEntityName()` 返回字符串 `"SignableOrderInfo"`，`EntityGatewayRegistry` 以此为 key 进行注册和查找。实体名变更必须在 Gateway、YAML、Prompt、SKILL.md 四处保持一致，否则查询引擎找不到对应 Gateway。

## Goals / Non-Goals

**Goals:**
- `SignableOrderInfo` 实体名改为 `PersonalSignableOrderInfo`，四处保持一致（YAML、Gateway、Prompt、SKILL）
- `FormalSignableOrderInfo` displayName 更新为"正签合同弹窗可签约S单"，aliases 补充"正签合同弹窗S单"
- `SignableOrderInfo` displayName 更新为"销售合同弹窗可签约S单"，aliases 补充"销售合同弹窗S单"

**Non-Goals:**
- 不改变 Gateway 的业务逻辑（接口调用、数据解析）
- 不改变 `FormalSignableOrderInfo` 的实体名（Java 类名保持不变）
- 不改变本体关系的语义，仅更新 `to` 字段的实体名引用

## Decisions

### 决策 1：`FormalSignableOrderInfo` 只更新 displayName，不重命名实体

`Formal`（正签）已准确表达业务含义，实体名重命名收益低、变更范围大。仅将 displayName 和 aliases 更新为更完整的描述即可。

### 决策 2：实体名变更在 4 个位置原子同步

变更顺序建议：
1. **domain-ontology.yaml**（实体名 + displayName + 关系 + aliases）
2. **Java Gateway 文件重命名**（文件名 + 类名 + getEntityName() 返回值 + 日志前缀）
3. **sre-agent.md**（queryScope 示例值）
4. **SKILL.md**（queryScope 调用示例）

4 个位置任意一处遗漏都会导致运行时路径查找失败。

## Risks / Trade-offs

| 风险 | 缓解措施 |
|------|---------|
| Gateway `getEntityName()` 与 YAML 实体名不一致，运行时 EntityRegistry 找不到路径 | 先改 YAML，再改 Gateway，修改后集成测试验证 `SignableOrderInfo` queryScope 可正常查询 |
| Prompt/SKILL.md 遗漏更新，LLM 仍传旧名 `SignableOrderInfo`，查询失败 | 更新后运行集成测试，覆盖排查场景 |

## Migration Plan

无数据迁移。变更为纯代码/配置重命名，集成测试验证通过即可部署。

回滚策略：git revert 本次提交，4 处同步回退。
