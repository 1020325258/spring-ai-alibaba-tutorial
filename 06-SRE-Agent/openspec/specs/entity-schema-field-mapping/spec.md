# entity-schema-field-mapping Specification

## Purpose

定义 YAML 作为实体属性规范文档的格式和用途。

## Requirements

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
