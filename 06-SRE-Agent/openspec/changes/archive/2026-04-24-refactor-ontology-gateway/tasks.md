## 1. 准备工作

- [x] 1.1 创建 `JsonMappingUtils` 工具类，提供 `getText`、`getInt`、`formatDateTime` 等方法
- [x] 1.2 简化 `domain-ontology.yaml` 格式，移除 `source`、`flattenPath` 配置

## 2. 改造 YAML 驱动的 Gateway（移除 EntitySchemaMapper）

- [x] 2.1 改造 `BudgetBillGateway`：移除 EntitySchemaMapper，直接解析
- [x] 2.2 改造 `PersonalQuoteGateway`：移除 EntitySchemaMapper，直接解析
- [x] 2.3 改造 `SubOrderGateway`：移除 EntitySchemaMapper，直接解析
- [x] 2.4 改造 `PersonalSignableOrderInfoGateway`：移除 EntitySchemaMapper，直接解析
- [x] 2.5 改造 `FormalSignableOrderInfoGateway`：移除 EntitySchemaMapper，直接解析

## 3. 改造硬编码 Gateway（修正属性定义）

- [x] 3.1 补充 `Contract` 实体的 YAML 属性定义
- [x] 3.2 改造 `ContractGateway`：按 YAML 属性组装返回
- [x] 3.3 修正 `ContractNode` 的 YAML 属性定义（补充 contractCode，修正 nodeType 类型）
- [x] 3.4 改造 `ContractNodeGateway`：按 YAML 属性组装返回
- [x] 3.5 修正 `ContractQuotationRelation` 的 YAML 属性定义（修正 bindType/status 类型）
- [x] 3.6 改造 `ContractQuotationRelationGateway`：按 YAML 属性组装返回
- [x] 3.7 补充 `ContractConfig` 的 YAML 属性定义（补充 signChannelType）
- [x] 3.8 改造 `ContractConfigGateway`：按 YAML 属性组装返回
- [x] 3.9 补充 `ContractInstance` 的 YAML 属性定义（补充 message）
- [x] 3.10 改造 `ContractInstanceGateway`：按 YAML 属性组装返回
- [x] 3.11 修正 `ContractUser` 的 YAML 属性定义（修正类型）
- [x] 3.12 改造 `ContractUserGateway`：保留解密等逻辑，按 YAML 属性组装返回
- [x] 3.13 定义 `ContractField` 的 YAML 属性结构
- [x] 3.14 改造 `ContractFieldGateway`：按 YAML 属性组装返回

## 4. 清理工作

- [x] 4.1 删除 `EntitySchemaMapper.java`
- [x] 4.2 删除 `JsonPathResolver.java`
- [x] 4.3 清理 Gateway 中的 EntitySchemaMapper 依赖注入
- [x] 4.4 移除旧解析方法和一致性校验代码

## 5. 测试验证

- [x] 5.1 为 `JsonMappingUtils` 编写单元测试
- [x] 5.2 验证所有 Gateway 返回字段与 YAML 定义一致
- [x] 5.3 运行集成测试验证完整链路
