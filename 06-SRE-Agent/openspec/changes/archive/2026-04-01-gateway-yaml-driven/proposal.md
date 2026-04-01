## Why

当前已完成 `FormalSignableOrderInfoGateway` 和 `PersonalSignableOrderInfoGateway` 的 YAML 驱动改造试点，验证了"声明式字段映射"的可行性。其余 10 个 Gateway 仍使用硬编码解析逻辑，存在维护成本高、字段定义与实际返回不一致等问题。需要将所有 Gateway 统一改造为 YAML 配置驱动，并保留新旧方法一致性校验机制。

## What Changes

- **扩展** `JsonPathResolver` 支持更多解析模式（当前已支持 8 种模式）
- **改造** 剩余 10 个 Gateway，移除硬编码解析逻辑，改由 `domain-ontology.yaml` 配置驱动
- **新增** 每个 Gateway 保留旧方法 + 一致性校验：新方法输出与旧方法不一致时打印 ERROR 日志
- **更新** `domain-ontology.yaml`：为所有实体添加 `sourceType`、`endpoint`、`flattenPath` 和 `attributes[].source` 配置
- **后续** 验证通过后，删除各 Gateway 的旧解析方法

## Capabilities

### New Capabilities
- `gateway-yaml-driven`: 统一网关字段解析能力——所有 Gateway 的字段解析规则在 YAML 中声明，通过 `JsonPathResolver` 解析，实现声明式配置

### Modified Capabilities
- `signable-order-info-query`: 查询逻辑保持不变，但字段解析方式从硬编码改为 YAML 驱动（无行为变更）

## Impact

- **受影响代码**：11 个 Gateway（FormalSignableOrderInfo、PersonalSignableOrderInfo + 其余 9 个）、新增 `JsonPathResolver`、`EntitySchemaMapper`
- **YAML 配置**：`domain-ontology.yaml`（为所有实体添加解析规则）
- **接口签名**：无变化
- **运行时行为**：数据返回格式保持一致，新增新旧方法一致性校验日志
