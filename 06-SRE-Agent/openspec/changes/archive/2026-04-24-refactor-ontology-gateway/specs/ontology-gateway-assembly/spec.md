## ADDED Requirements

### Requirement: Gateway 按 YAML 属性定义组装实体返回

所有实现 `EntityDataGateway` 接口的 Gateway SHALL 根据 `domain-ontology.yaml` 中定义的 `attributes` 组装返回数据，YAML 作为规范文档而非配置驱动。

#### Scenario: 简单属性返回
- **WHEN** Gateway 查询到数据后
- **THEN** 返回的 `Map<String, Object>` 包含 YAML 中定义的所有属性
- **AND** 属性名与 YAML 定义的 `name` 一致
- **AND** 属性类型与 YAML 定义的 `type` 一致

#### Scenario: 属性来自查询参数
- **WHEN** 属性值需要从查询参数获取（如 contractCode）
- **THEN** Gateway 从查询参数中取值并设置到返回结果

#### Scenario: 属性需要格式化转换
- **WHEN** 属性需要格式化（如时间戳转字符串）
- **THEN** Gateway 在解析时进行格式化处理

### Requirement: Gateway 代码可读性

Gateway 的解析逻辑 SHALL 清晰易读，避免重复代码。

#### Scenario: 使用通用工具方法
- **WHEN** 存在重复的字段解析逻辑
- **THEN** 使用 `JsonMappingUtils` 工具方法
- **AND** 保持代码简洁

#### Scenario: 添加文档注释
- **WHEN** Gateway 的解析方法
- **THEN** 方法注释引用 YAML 定义的属性列表
- **AND** 说明每个属性的来源

### Requirement: YAML 作为规范文档

`domain-ontology.yaml` SHALL 作为实体属性的规范文档，定义属性名称、类型和描述。

#### Scenario: 属性定义完整
- **WHEN** 定义一个实体的属性
- **THEN** 每个属性包含 `name`、`type`、`description`
- **AND** 类型为 string、int、long 等基本类型

#### Scenario: 无配置驱动语法
- **WHEN** 定义属性时
- **THEN** 不使用 `source`、`flattenPath` 等配置驱动语法
- **AND** 仅声明属性规范
