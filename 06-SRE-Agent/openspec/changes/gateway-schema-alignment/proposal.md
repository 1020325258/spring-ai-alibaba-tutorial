## Why

`domain-ontology.yaml` 声明了每个实体的属性（attributes），但各 Gateway 的转换逻辑与 YAML 声明独立演化，没有任何机制保证两者一致。导致开发者在 ontology.html 看到实体有某个属性，但实际查询时 LLM 收到的数据中并不包含该属性，YAML 成为"文档"而非"运行时契约"。

## What Changes

- **新增** `OntologySchemaEnforcer`：在引擎层对 Gateway 出参做规范化，过滤未声明字段、补 null 给缺失的声明字段，每次出参不一致时输出 WARN 日志
- **新增** `GatewaySchemaValidator`：应用启动时做静态校验，检查 Gateway 注册与 YAML 实体是否对应、relation `via` 字段是否在 attributes 中声明
- **修改** `OntologyQueryEngine`：在 Gateway 调用后集成 SchemaEnforcer
- **修改** `OntologyEntity`：增加 `dynamic` 字段，用于标记 ContractField 等动态属性实体跳过字段集合校验
- **修复** YAML 与 Gateway 现有不一致（B 类修复）：
  - `ContractDao` 两处 SQL/结果 Map 缺字段
  - YAML `PersonalQuote.attributes` 语义错误（声明的是入参而非出参）
  - YAML `BudgetBill`/`ContractConfig` 缺少 Gateway 实际返回的字段
  - `ContractField` 标记 `dynamic: true`
  - `QueryScope` 枚举补充 3 个缺失实体

## Capabilities

### New Capabilities

- `schema-enforcement`：Gateway 出参与 YAML attributes 的运行时一致性保障能力——规范化、警告、启动校验

### Modified Capabilities

（无 spec 级别的行为变更，仅实现层补充）

## Impact

- **受影响代码**：`OntologyQueryEngine`、`EntityRegistry`、`ContractDao`、所有 Gateway（通过引擎层统一处理，各 Gateway 实现类无需修改）
- **YAML 配置**：`domain-ontology.yaml`（6 处修正）、`prompts/sre-agent.md`（QueryScope 可用值同步）
- **运行时行为**：LLM 收到的实体数据字段集合将严格等于 YAML 声明的 attributes；多余字段被过滤、缺失字段以 null 补充
- **无 Breaking Change**：对已有 `@Tool` 接口、`EntityDataGateway` 接口签名无影响
