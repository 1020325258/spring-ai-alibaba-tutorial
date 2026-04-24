## REMOVED Requirements

### Requirement: 实体的字段解析规则由 YAML 声明

**Reason**: YAML 不再驱动代码解析，改为作为规范文档。字段解析逻辑由 Gateway 直接编写。

**Migration**: Gateway 直接按 YAML 定义的属性组装返回，解析逻辑写在代码中。

### Requirement: 试点 Gateway 由 YAML 驱动字段解析

**Reason**: 不再使用 YAML 驱动模式，所有 Gateway 统一为直接解析模式。

**Migration**: 移除 EntitySchemaMapper 调用，Gateway 直接编写解析逻辑。

### Requirement: 新旧实现输出一致性验证

**Reason**: 不再需要新旧方法对比，移除旧代码。

**Migration**: 移除旧解析方法和一致性验证代码。

## ADDED Requirements

### Requirement: YAML 定义实体属性规范

系统 SHALL 在 `domain-ontology.yaml` 中定义每个实体的属性规范，包含属性名、类型、描述。

#### Scenario: 属性规范定义
- **WHEN** 定义实体属性时
- **THEN** 每个属性包含 `name`（属性名）、`type`（类型）、`description`（描述）
- **AND** 类型为 string、int、long 等基本类型

#### Scenario: YAML 作为文档
- **WHEN** 开发者需要了解实体结构时
- **THEN** 查看 `domain-ontology.yaml` 即可了解所有属性定义
- **AND** Gateway 实现遵循此规范
