## Why

当前 `EntitySchemaMapper` 抽象层引入了不必要的复杂度：学习 Source 语法成本高、调试困难、复杂转换仍需扩展。实际上 Gateway 直接按 YAML 定义的属性组装返回更直观清晰。

**简化架构**：移除 EntitySchemaMapper，让 YAML 纯粹作为规范文档，Gateway 负责查询数据并按规范组装返回。

## What Changes

- **BREAKING** 移除 `EntitySchemaMapper` 和 `JsonPathResolver`
- **BREAKING** 移除 YAML 中的 `source` 和 `flattenPath` 配置（简化为属性定义）
- 重构所有 Ontology Gateway：直接编写解析逻辑，按 YAML 属性组装返回
- 提取通用工具方法 `JsonMappingUtils` 减少重复代码
- 补充/修正 YAML 中各实体的属性定义，确保类型正确

## Capabilities

### New Capabilities
- `ontology-gateway-assembly`: Gateway 按 YAML 属性定义组装实体返回，YAML 作为规范文档

### Modified Capabilities
- `gateway-yaml-driven`: **移除** EntitySchemaMapper 驱动模式，改为 Gateway 直接解析
- `entity-schema-field-mapping`: **简化** YAML 定义，移除 source 语法，仅保留属性声明

## Impact

**删除文件**：
- `EntitySchemaMapper.java`
- `JsonPathResolver.java`

**修改文件**：
- `domain-ontology.yaml` - 简化属性定义格式
- 所有 Gateway 实现（约 12 个）- 重构解析逻辑
- Gateway 依赖注入 - 移除 EntitySchemaMapper
