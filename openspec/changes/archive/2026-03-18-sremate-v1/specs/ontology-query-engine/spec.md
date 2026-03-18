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
