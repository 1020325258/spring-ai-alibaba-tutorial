# gateway-yaml-driven Specification

## Purpose

**DEPRECATED** - 此规范已被 `ontology-gateway-assembly` 替代。

原有 EntitySchemaMapper 驱动模式已被移除，Gateway 现在直接按 YAML 属性定义组装返回。请参考 `ontology-gateway-assembly` 规范。

## Migration

- EntitySchemaMapper 和 JsonPathResolver 已删除
- Gateway 直接编写解析逻辑，按 YAML 属性组装返回
- 使用 `JsonMappingUtils` 工具类处理字段映射
