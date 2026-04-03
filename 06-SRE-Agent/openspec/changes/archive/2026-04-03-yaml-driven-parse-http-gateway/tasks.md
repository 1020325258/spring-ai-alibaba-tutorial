## 1. YAML 配置更新

- [x] 1.1 为 SubOrder 实体添加 sourceType、endpoint、flattenPath 配置
- [x] 1.2 为 SubOrder 实体的 attributes 添加 source 字段
- [x] 1.3 为 PersonalQuote 实体添加 sourceType、endpoint、flattenPath 配置
- [x] 1.4 为 PersonalQuote 实体的 attributes 添加 source 字段（含嵌套字段 quoteInfo.fileUrl）

## 2. EntitySchemaMapper 验证

- [x] 2.1 验证 flattenPath: "data[]" 对扁平数组的支持
- [x] 2.2 验证 flattenPath: "data.personalContractDataList[]" 对嵌套路径的支持
- [x] 2.3 扩展 JsonPathResolver.flattenWithInheritance() 支持单层数组和对象路径数组

## 3. Gateway 改造

- [x] 3.1 SubOrderGateway 注入 EntitySchemaMapper 和 EntityRegistry
- [x] 3.2 SubOrderGateway 实现 parseSubOrdersNew() 调用 schemaMapper.map()
- [x] 3.3 SubOrderGateway 保留 parseSubOrders() 为 parseSubOrdersOld() 用于一致性校验
- [x] 3.4 SubOrderGateway 添加 equals() 方法比较新旧输出
- [x] 3.5 PersonalQuoteGateway 注入 EntitySchemaMapper 和 EntityRegistry
- [x] 3.6 PersonalQuoteGateway 实现 parsePersonalQuoteNew() 调用 schemaMapper.map()
- [x] 3.7 PersonalQuoteGateway 保留 parsePersonalQuote() 为 parsePersonalQuoteOld() 用于一致性校验
- [x] 3.8 PersonalQuoteGateway 添加 equals() 方法比较新旧输出

## 4. 测试验证

- [x] 4.1 运行单元测试验证 EntitySchemaMapper 改造正确
- [x] 4.2 运行集成测试验证 SubOrder/PersonalQuote 查询功能正常
- [x] 4.3 检查日志无"新旧方法输出一致性校验失败"错误
