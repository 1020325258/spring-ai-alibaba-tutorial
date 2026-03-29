## ADDED Requirements

### Requirement: Gateway 统一接口
系统 SHALL 定义 `EntityDataGateway` 接口，所有实体网关 SHALL 实现该接口，并在 `@PostConstruct` 中向 `EntityGatewayRegistry` 自注册。

#### Scenario: Gateway 自注册
- **WHEN** 应用启动，Gateway Bean 初始化完成
- **THEN** Gateway 通过 `registry.register(this)` 注册，引擎可通过 `getEntityName()` 查找

#### Scenario: 未找到 Gateway
- **WHEN** 引擎请求未注册的实体名对应的 Gateway
- **THEN** `EntityGatewayRegistry` 抛出异常，明确提示哪个实体缺少 Gateway 实现

---

### Requirement: queryByField 标准查询
Gateway SHALL 实现 `queryByField(fieldName, value)` 方法，接受 YAML 中 `via.target_field` 作为 fieldName。

#### Scenario: 字段查询返回列表
- **WHEN** 调用 `gateway.queryByField("contractCode", "C123")`
- **THEN** 返回 `List<Map<String, Object>>`，无结果时返回空列表，不返回 null

---

### Requirement: queryByFieldWithContext 上下文查询
部分 Gateway SHALL 实现 `queryByFieldWithContext(fieldName, value, parentRecord)` 方法，允许从父记录中取额外参数（如 SubOrder 需要父记录的 `billCode`）。

#### Scenario: SubOrder 双参数查询
- **WHEN** 引擎查询 SubOrder，父记录为 BudgetBill（含 billCode）
- **THEN** Gateway 使用 `quotationOrderNo`（来自关系配置）+ `homeOrderNo`（来自父记录 billCode）双参数查询

---

### Requirement: queryWithExtraParams 额外参数查询
部分 Gateway SHALL 实现 `queryWithExtraParams(fieldName, value, extraParams)` 方法，用于首层关系需要用户传入额外参数的场景（如 PersonalQuote）。

#### Scenario: PersonalQuote 额外参数
- **WHEN** LLM 调用时传入 `extraParams={"subOrderNoList": "...", "billCodeList": "..."}`
- **THEN** Gateway 将额外参数合并到 HTTP 请求体中

---

### Requirement: YAML 驱动新增实体
新增实体 SHALL 只需"实现 Gateway + 更新 domain-ontology.yaml"，无需修改 OntologyQueryEngine 代码。

#### Scenario: 新增实体可查询
- **WHEN** 在 YAML 中添加新实体定义和关系，并实现对应 Gateway
- **THEN** 引擎自动识别新路径，`ontologyQuery` 工具可查询该实体，无需修改工具层代码
