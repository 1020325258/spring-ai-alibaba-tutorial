## ADDED Requirements

### Requirement: 实体的字段解析规则由 YAML 声明

系统 SHALL 支持在 `domain-ontology.yaml` 中通过 `source` 字段声明每个属性的解析路径，实现字段映射的可配置化。

#### Scenario: 简单字段映射
- **WHEN** `source: "fieldName"` 被配置在 attributes 中
- **THEN** 解析时直接取 JSON 中的同名字段值

#### Scenario: 嵌套字段映射
- **WHEN** `source: "parent.child"` 被配置
- **THEN** 解析时取 `jsonNode.path("parent").path("child")` 的值

#### Scenario: 数组展平
- **WHEN** `source: "data[].signableOrderInfos[].fieldName"` 被配置（路径末尾为 `[]`）
- **THEN** 遍历数组并将每项展开为独立记录，外层路径的字段自动继承到每条展开记录

#### Scenario: 多数组合并
- **WHEN** `source: "listA[].field,listB[].field"` 被配置（逗号分隔多个路径）
- **THEN** 分别解析每个路径并合并结果

#### Scenario: 查询参数注入
- **WHEN** `source: "$param.fieldName"` 被配置
- **THEN** 从查询参数中获取值并写入结果

### Requirement: 试点 Gateway 由 YAML 驱动字段解析

系统 SHALL 将 `FormalSignableOrderInfoGateway` 和 `PersonalSignableOrderInfoGateway` 的字段解析逻辑迁移到 YAML 配置，Gateway 仅返回原始 JSON，由引擎层统一解析。

#### Scenario: FormalSignableOrderInfo 查询
- **WHEN** 查询 `FormalSignableOrderInfo` 实体
- **THEN** Gateway 返回原始 JSON，由 `JsonPathResolver` 根据 YAML 的 source 配置解析，返回与旧实现一致的字段集合

#### Scenario: PersonalSignableOrderInfo 查询
- **WHEN** 查询 `PersonalSignableOrderInfo` 实体
- **THEN** Gateway 返回原始 JSON，由 `JsonPathResolver` 根据 YAML 的 source 配置解析，返回与旧实现一致的字段集合

### Requirement: 新旧实现输出一致性验证

系统 SHALL 在改造过程中保留旧代码，并确保新旧实现的输出完全一致。

#### Scenario: 一致性测试通过
- **WHEN** 执行新旧解析方法对比测试
- **THEN** 两者返回的 `List<Map<String, Object>>` 完全相等（字段名和值均一致）

#### Scenario: 一致性测试失败
- **WHEN** 新旧解析方法返回结果不一致
- **THEN** 测试失败并标记差异点，需修复后重新验证