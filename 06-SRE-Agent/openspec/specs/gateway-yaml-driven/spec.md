# gateway-yaml-driven Specification

## Purpose
TBD - created by archiving change gateway-yaml-driven. Update Purpose after archive.
## Requirements
### Requirement: Gateway 字段解析由 YAML 配置驱动
所有实现 `EntityDataGateway` 接口的 Gateway SHALL 通过 `domain-ontology.yaml` 中定义的 `source` 属性解析返回字段，而非硬编码实现。

#### Scenario: HTTP 接口返回数据解析
- **WHEN** Gateway 调用 HTTP 接口获取原始 JSON 数据
- **THEN** 使用 `EntitySchemaMapper.map(entity, rawJson, queryParams)` 根据 YAML 配置的 `source` 和 `flattenPath` 进行解析
- **AND** 返回的每条记录包含 YAML 中定义的所有字段

#### Scenario: YAML 配置包含 flattenPath
- **WHEN** 实体配置了 `flattenPath: "data[].items[]"`
- **THEN** `JsonPathResolver.flattenWithInheritance()` 自动展平嵌套数组，并继承外层字段

### Requirement: 新旧方法一致性校验
每个 Gateway SHALL 保留旧解析方法，并在运行时对比新旧方法输出。

#### Scenario: 一致性校验通过
- **WHEN** 新方法（YAML 驱动）与旧方法（硬编码）输出相同
- **THEN** 系统正常运行，无日志输出

#### Scenario: 一致性校验失败
- **WHEN** 新方法与旧方法输出不一致
- **THEN** 系统打印 ERROR 日志包含 newResult 和 oldResult 的完整内容
- **AND** 仍返回新方法结果（YAML 驱动）

### Requirement: 查询参数注入
YAML 配置的 `source` SHALL 支持 `$param.fieldName` 语法，从查询参数注入值。

#### Scenario: 查询参数注入
- **WHEN** 属性配置 `source: "$param.projectOrderId"`
- **THEN** 解析时从 queryParams 中获取对应值

