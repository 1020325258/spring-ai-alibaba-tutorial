## REMOVED Requirements

### Requirement: Gateway 字段解析由 YAML 配置驱动

**Reason**: EntitySchemaMapper 抽象层增加不必要的复杂度，Gateway 直接编写解析逻辑更清晰。

**Migration**: Gateway 直接按 YAML 属性定义组装返回，无需 EntitySchemaMapper。

### Requirement: 新旧方法一致性校验

**Reason**: 不再需要新旧方法对比，直接使用新的解析逻辑。

**Migration**: 移除旧解析方法和一致性校验代码。

### Requirement: 查询参数注入

**Reason**: 不再使用 `$param.fieldName` 语法，Gateway 在代码中直接处理查询参数。

**Migration**: Gateway 从方法参数中获取查询值，直接设置到返回结果。
