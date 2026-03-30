## ADDED Requirements

### Requirement: Gateway 出参规范化

系统 SHALL 在每次 Gateway 查询返回后，将结果规范化为 YAML 中声明的 attributes 集合：保留声明属性（缺失补 null）、过滤未声明字段（输出 WARN 日志），以 `_` 开头的系统字段始终保留。

#### Scenario: 过滤 Gateway 多返回的字段
- **WHEN** Gateway 返回的记录包含 YAML attributes 中未声明的字段
- **THEN** 该字段从记录中移除，并输出 WARN 日志注明实体名和多余字段名

#### Scenario: 补 null 给 Gateway 缺失的声明字段
- **WHEN** YAML 声明了某属性但 Gateway 返回的记录中不包含该字段
- **THEN** 记录中该字段值设为 null，并输出 WARN 日志注明实体名和缺失字段名

#### Scenario: 系统字段不参与校验
- **WHEN** Gateway 返回的记录包含 `_` 前缀的字段（如 `_hint`）
- **THEN** 该字段不被过滤，不触发 WARN，直接保留到输出

#### Scenario: dynamic 实体跳过字段集合校验
- **WHEN** 实体在 YAML 中标记为 `dynamic: true`（如 ContractField）
- **THEN** SchemaEnforcer 直接返回 Gateway 原始结果，不做任何过滤或补 null

#### Scenario: 无 attributes 声明的实体跳过校验
- **WHEN** 实体在 YAML 中未声明 attributes 列表或列表为空
- **THEN** SchemaEnforcer 直接返回 Gateway 原始结果

### Requirement: 启动时静态校验

系统 SHALL 在 Spring 上下文完全刷新后执行静态结构校验，不依赖任何 DB/HTTP 查询。

#### Scenario: Gateway 注册了 YAML 中不存在的实体
- **WHEN** `EntityGatewayRegistry` 中存在 `getEntityName()` 返回值在 `EntityRegistry` 中找不到对应实体的 Gateway
- **THEN** 抛出 `IllegalStateException`，阻止应用启动

#### Scenario: YAML 实体无 Gateway 注册
- **WHEN** `EntityRegistry` 中存在实体，但 `EntityGatewayRegistry` 中没有对应 Gateway
- **THEN** 输出 WARN 日志，应用正常启动

#### Scenario: relation via 字段未在目标实体 attributes 中声明
- **WHEN** YAML 中某条 relation 的 `via.target_field` 未出现在目标实体的 attributes 列表中
- **THEN** 输出 WARN 日志注明 relation 路径和缺失字段名，应用正常启动

#### Scenario: 启动摘要日志
- **WHEN** 应用启动完成
- **THEN** 以 INFO 级别输出所有已注册 Gateway 的实体名列表，供审计

### Requirement: YAML 与 Gateway 现存不一致修复

系统 SHALL 修复现已发现的 6 处 YAML 与 Gateway 出参不一致，作为 SchemaEnforcer 生效前的前置修正。

#### Scenario: ContractGateway 按 projectOrderId 查询时返回该字段
- **WHEN** 调用 `ContractGateway.queryByField("projectOrderId", value)`
- **THEN** 返回的每条记录中包含 `projectOrderId` 字段

#### Scenario: ContractNodeGateway 返回 contractCode 字段
- **WHEN** 调用 `ContractNodeGateway.queryByField("contractCode", value)`
- **THEN** 返回的每条记录中包含 `contractCode` 字段

#### Scenario: PersonalQuote attributes 声明出参字段
- **WHEN** 查询 PersonalQuote 实体
- **THEN** YAML attributes 中声明的字段与 Gateway 实际返回字段一致（`billCode`、`personalContractPrice`、`organizationCode`、`organizationName`、`createTime`、`quoteFileUrl`、`quotePrevUrl`）

#### Scenario: QueryScope 枚举覆盖所有已注册实体
- **WHEN** LLM 传入 `queryScope="SignableOrderInfo"` 或 `"FormalSignableOrderInfo"` 或 `"PersonalQuote"`
- **THEN** `QueryScope.fromString()` 能正确解析，不走 fallback 路径
