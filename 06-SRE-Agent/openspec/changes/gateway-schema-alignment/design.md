## Context

当前架构中，`domain-ontology.yaml` 声明了每个实体的 attributes，但 Gateway 实现类各自独立地将原始数据（SQL 行 / HTTP JSON）转换为 `Map<String, Object>`，两侧无任何契约绑定。`OntologyQueryEngine` 直接将 Gateway 原始输出透传给上层，导致：

- LLM 看到的字段取决于 Gateway 实现，而非 YAML 声明
- 开发者修改 YAML 属性列表后，Gateway 不一定同步更新
- ontology.html UI 展示的属性与实际查询结果字段不一致

核心约束：Gateway 接口签名不能变（`List<Map<String, Object>>`），不能要求 Gateway 知道 YAML 定义。

## Goals / Non-Goals

**Goals:**
- 在引擎层建立运行时校验：Gateway 出参字段集合必须与 YAML attributes 一致
- 提供清晰的不一致告警（WARN 日志），让开发者能快速定位哪个 Gateway 缺/多了字段
- 启动时静态校验：无需跑 DB/HTTP 即可发现 Gateway 注册与 YAML 的结构性偏差
- 修复现存的 6 处具体不一致问题

**Non-Goals:**
- 不改变 Gateway 接口签名
- 不做字段值的类型转换（已由各 Gateway 负责）
- 不对 `object`/`array` 类型属性的内部结构做递归校验
- 不阻断应用启动（校验失败只产生日志，不抛异常，ContractField `dynamic` 实体除外）

## Decisions

### 决策 1：校验在引擎层而非 Gateway 层

**选择**：在 `OntologyQueryEngine` 调用 Gateway 之后做规范化，而非要求每个 Gateway 自己校验。

**理由**：Gateway 不应感知 YAML 结构（单一职责）；引擎已持有 `EntityRegistry`，天然是做 schema 校验的位置；新增 Gateway 无需记住实现校验逻辑。

**备选**：在每个 Gateway 内部调用 SchemaEnforcer → 违反单一职责，且新 Gateway 容易遗忘。

### 决策 2：规范化语义（过滤 + 补 null）

**选择**：对每条记录，只保留 YAML 声明的属性，缺失的属性补充为 null，多余的字段过滤掉并打 WARN。

**理由**：LLM 收到的数据与 YAML 完全一致，ontology.html 看到的即是实际数据的 key 集合；null 比"字段不存在"对 LLM 更友好（显式表达"有此属性但无值"）。

**备选**：只过滤、不补 null → LLM 无法区分"属性不存在"和"属性值为空"。

### 决策 3：`dynamic` 标记处理动态属性实体

**选择**：在 `OntologyEntity` 增加 `dynamic: true` 标记，SchemaEnforcer 对此类实体直接放行。

**理由**：`ContractField` 返回的是业务扩展字段的动态 key-value 集合，字段数和名称不固定，强制 schema 约束无意义。

### 决策 4：启动校验用 `ContextRefreshedEvent`

**选择**：`GatewaySchemaValidator` 实现 `ApplicationListener<ContextRefreshedEvent>`，在 Spring 上下文完全刷新后执行。

**理由**：所有 Gateway 通过 `@PostConstruct` 向 `EntityGatewayRegistry` 注册，`ContextRefreshedEvent` 触发时注册已全部完成；比 `@DependsOn` 声明更健壮，不随 Gateway 数量增长而膨胀。

### 决策 5：`_` 前缀字段豁免过滤

**选择**：以 `_` 开头的 key（如 `_hint`、`_rawData`）始终保留，不纳入 schema 校验。

**理由**：这些是引擎内部元数据（如 PersonalQuoteGateway 的提示性记录），不属于业务属性，不应被声明在 YAML 中，也不应被过滤。

## Risks / Trade-offs

- **[风险] 补 null 改变了现有测试断言** → 缓解：`assertFirstRecordHasField` 类断言不受影响；只有精确匹配 key 集合的断言需调整（当前测试均为宽松验证）
- **[风险] Gateway 多返回字段被过滤，WARN 日志产生大量噪声** → 缓解：先修复 B 类不一致再启用 SchemaEnforcer，确保上线时无存量 WARN
- **[Trade-off] 启动校验无法检测字段名拼写错误**（Gateway 返回 `contractcode`，YAML 声明 `contractCode`）→ 运行时 WARN 可捕获，不需要启动时检测

## Migration Plan

1. 先修复 B 类已知不一致（YAML 修正 + DAO 修正），确保现有 Gateway 出参与 YAML 完全对齐
2. 添加 `OntologySchemaEnforcer` 并接入引擎（此时如有遗漏的不一致会被 WARN 暴露）
3. 添加 `GatewaySchemaValidator` 启动校验
4. 运行集成测试验证无回归

无需回滚策略（纯增量机制，不改变任何对外接口）。

## Open Questions

（无）
