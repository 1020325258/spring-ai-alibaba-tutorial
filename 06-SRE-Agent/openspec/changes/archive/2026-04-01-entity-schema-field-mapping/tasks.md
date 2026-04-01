## 1. 实现 JsonPathResolver 解析引擎

- [x] 1.1 创建 `JsonPathResolver` 类：基于 Jackson JsonNode 实现简单字段、嵌套字段、数组展平、多数组合并、查询参数注入等解析方法
- [x] 1.2 单元测试：验证各 source 语法的解析正确性（35个测试）

## 2. 修改 domain-ontology.yaml 添加 source 配置

- [x] 2.1 为 `FormalSignableOrderInfo` 实体添加 `sourceType`、`endpoint`、`flattenPath` 和 `attributes[].source` 配置
- [x] 2.2 为 `PersonalSignableOrderInfo` 实体添加相同的配置

## 3. 改造 FormalSignableOrderInfoGateway

- [x] 3.1 保留旧方法 `parseSignableOrdersOld`，标记 `@Deprecated`
- [x] 3.2 新增 `parseSignableOrdersNew` 方法：调用 `JsonPathResolver` 根据 YAML 配置解析
- [x] 3.3 修改 `queryByField` 方法，默认调用新方法（旧方法保留用于对比测试）
- [x] 3.4 新增一致性验证测试：对比新旧方法输出，一致后删除旧代码

## 4. 改造 PersonalSignableOrderInfoGateway

- [x] 4.1 保留旧方法 `parseSignableOrdersOld`，标记 `@Deprecated`
- [x] 4.2 新增 `parseSignableOrdersNew` 方法：调用 `JsonPathResolver` 根据 YAML 配置解析
- [x] 4.3 修改 `queryByField` 方法，默认调用新方法
- [x] 4.4 新增一致性验证测试：对比新旧方法输出，一致后删除旧代码

## 5. 集成测试验证

- [x] 5.1 运行单元测试：`JsonPathResolverTest` 35个测试全部通过
- [x] 5.2 运行集成测试：`QaPairEvaluationIT` 3个测试全部通过
- [ ] 5.3 待全量推广时删除旧代码

## 6. 扩展能力（调研阶段）

- [ ] 6.1 评估其他 Gateway 的 YAML 驱动改造可行性
- [ ] 6.2 完善 JsonPathResolver 对更多解析模式的支持