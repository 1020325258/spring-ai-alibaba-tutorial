## Why

当前 `domain-ontology.yaml` 中各实体的 attributes 声明与 Gateway 实际返回的字段缺乏统一维护机制。YAML 声明的字段与 Gateway 代码中的解析逻辑独立演化，导致：
1. ontology.html 展示的字段与实际返回不一致
2. 新增/修改字段时需要同时改 YAML 和 Gateway 代码，容易遗漏
3. 字段解析逻辑散落在各个 Gateway 中，难以统一管理和审查

本次试点：将 2 个典型 Gateway（FormalSignableOrderInfo、PersonalSignableOrderInfo）的字段解析逻辑迁移到 YAML 中维护，实现"声明式字段映射"。

## What Changes

- **新增** YAML 解析规则语法（source 字段）：支持简单字段、嵌套字段、数组展平、多数组合并、查询参数注入等场景
- **新增** `JsonPathResolver`：基于 Jackson JsonNode 的轻量级解析引擎，解析 YAML 中的 source 语法
- **改造** `FormalSignableOrderInfoGateway`：移除硬编码解析逻辑，改由 YAML 配置驱动
- **改造** `PersonalSignableOrderInfoGateway`：移除硬编码解析逻辑，改由 YAML 配置驱动
- **新增** 旧代码保留 + 一致性验证：旧解析逻辑标记 `@Deprecated`，新增测试验证新旧输出一致
- **后续** 验证通过后删除旧代码，逐步推广到其他 Gateway

## Capabilities

### New Capabilities
- `entity-schema-field-mapping`: 实体的字段解析规则声明能力——在 YAML 中定义每个字段的来源路径，实现 Gateway 返回数据的统一规范化

### Modified Capabilities
（无 spec 级别的行为变更）

## Impact

- **受影响代码**：`FormalSignableOrderInfoGateway`、`PersonalSignableOrderInfoGateway`、新增 `JsonPathResolver`
- **YAML 配置**：`domain-ontology.yaml`（新增 entities 的 source 解析规则）
- **接口签名**：无变化，`EntityDataGateway` 返回类型保持 `List<Map<String, Object>>`
- **运行时行为**：Gateway 返回的数据字段集合由 YAML 定义，无实质变化