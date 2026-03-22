## ADDED Requirements

### Requirement: 单入口查询
系统 SHALL 提供 `OntologyQueryEngine.query(entityName, value, queryScope)` 作为对外唯一入口，LLM 只需调用该方法一次即可完成完整的关联数据查询。

#### Scenario: 仅查询起始实体
- **WHEN** `queryScope` 为 `null`、`"default"` 或 `"list"`
- **THEN** 返回起始实体的记录列表，不展开任何关联数据

#### Scenario: 展开单一目标实体
- **WHEN** `queryScope` 为单一实体名（如 `"ContractNode"`）
- **THEN** 引擎沿 YAML 关系路径展开，起始实体的每条记录挂载对应的子实体数据

#### Scenario: 展开多目标实体
- **WHEN** `queryScope` 为逗号分隔的多个实体名（如 `"ContractNode,ContractQuotationRelation"`）
- **THEN** 引擎并行展开所有目标路径，每条记录包含所有指定目标的关联数据

#### Scenario: 起始实体无数据
- **WHEN** 查询起始实体返回空记录
- **THEN** 方法返回 `null`

---

### Requirement: LookupStrategy 值格式匹配
系统 SHALL 根据 YAML 中配置的 `lookupStrategies` 正则 pattern 自动匹配入参格式，决定传给 Gateway 的字段名。

#### Scenario: 纯数字订单号
- **WHEN** `entityName=Order`，`value` 匹配 `^\d{15,}$`
- **THEN** 使用 `field=projectOrderId` 查询

#### Scenario: C前缀合同号
- **WHEN** `entityName=Contract`，`value` 匹配 `^C\d+`
- **THEN** 使用 `field=contractCode` 查询

#### Scenario: 无匹配策略
- **WHEN** `value` 不匹配实体的任何 pattern
- **THEN** 抛出 `IllegalArgumentException`，说明支持的格式列表

---

### Requirement: 并行执行同层关系
系统 SHALL 使用 `CompletableFuture` 并行查询同一层级内的多条记录和多个关系。

#### Scenario: 同层多条记录并行
- **WHEN** 起始实体返回多条记录，每条记录需展开同一关系
- **THEN** 多条记录的关联查询并发执行，不串行等待

#### Scenario: 同层多目标关系并行
- **WHEN** 同一层有多个目标关系（如 ContractNode 和 ContractQuotationRelation）
- **THEN** 两个关系的查询并发执行

---

### Requirement: resultKey 自动推导
系统 SHALL 从目标实体名自动推导结果在记录中的 key，规则为：首字母小写 + 加复数 `s`。

#### Scenario: ContractNode 的 key
- **WHEN** 目标实体为 `ContractNode`
- **THEN** 结果挂载在父记录的 `contractNodes` 字段下

#### Scenario: SubOrder 的 key
- **WHEN** 目标实体为 `SubOrder`
- **THEN** 结果挂载在父记录的 `subOrders` 字段下

---

### Requirement: 路径不存在时快速失败
系统 SHALL 在找不到 entityName → target 路径时抛出 `IllegalArgumentException`，而不是静默返回空数据。

#### Scenario: 路径不存在
- **WHEN** `queryScope` 指定的目标实体在 YAML 关系图中与起始实体无路径
- **THEN** 抛出异常，提示检查 `domain-ontology.yaml`

---

### Requirement: 实体参数校验
OntologyQueryTool SHALL 仅校验 entity 参数的合法性，不再根据 value 格式推断 entity。LLM 的判断 SHALL 被信任。

#### Scenario: 校验 entity 合法性
- **WHEN** LLM 传入 entity 参数
- **THEN** 校验该 entity 在 EntityRegistry 中存在，不存在则返回错误

#### Scenario: 信任 LLM 判断不覆盖
- **WHEN** LLM 传入 entity=ContractInstance，value="101835395"（纯数字）
- **THEN** 直接使用 LLM 判断的 entity，不被代码推断为 Order

#### Scenario: 纯数字 instanceId 正确识别
- **WHEN** 用户输入 "101835395的实例信息"
- **THEN** LLM 识别 entity=ContractInstance，value="101835395"，查询成功

---

### Requirement: 支持任意实体作为查询入口
用户 SHALL 能够直接查询任意在 YAML 中定义了 lookupStrategies 的实体，不限于 Order 或 Contract。

#### Scenario: 直接查询 ContractInstance
- **WHEN** 用户输入 "查 instanceId=101835395 的实例"
- **THEN** LLM 识别 entity=ContractInstance，正确返回版式数据

#### Scenario: 直接查询 ContractNode
- **WHEN** 用户输入 "查合同号 C1767150648920281 的节点"
- **THEN** LLM 识别 entity=Contract, queryScope=ContractNode

---

### Requirement: 多跳路径查询支持
系统 SHALL 支持三跳及以上的路径查询，每跳自动使用 `queryByFieldWithContext` 从父记录提取上下文参数。

#### Scenario: 三跳路径查询 PersonalQuote
- **WHEN** 查询路径为 Order → Contract → ContractQuotationRelation → PersonalQuote
- **THEN** 引擎在第三跳调用 PersonalQuoteGateway.queryByFieldWithContext，传入 ContractQuotationRelation 记录作为 parentRecord

#### Scenario: 多跳路径中每跳独立处理
- **WHEN** 路径有多跳，每跳的 Gateway 需要从父记录提取不同参数
- **THEN** 每跳的 `queryByFieldWithContext` 实现独立决定从 parentRecord 提取哪些字段
