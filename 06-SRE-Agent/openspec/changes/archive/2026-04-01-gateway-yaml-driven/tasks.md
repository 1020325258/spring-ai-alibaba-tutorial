## 准备阶段

- [ ] 1.1 分析所有 Gateway 的数据源类型（HTTP/DB）和返回数据结构
- [ ] 1.2 确认各 Gateway 的查询参数

## 第一批：简单 Gateway（无嵌套数组）

- [ ] 2.1 OrderGateway YAML 配置
- [ ] 2.2 OrderGateway 代码改造 + 一致性校验
- [ ] 2.3 BudgetBillGateway YAML 配置
- [ ] 2.4 BudgetBillGateway 代码改造 + 一致性校验
- [ ] 2.5 ContractNodeGateway YAML 配置
- [ ] 2.6 ContractNodeGateway 代码改造 + 一致性校验
- [ ] 2.7 ContractConfigGateway YAML 配置
- [ ] 2.8 ContractConfigGateway 代码改造 + 一致性校验
- [ ] 2.9 ContractFieldGateway YAML 配置
- [ ] 2.10 ContractFieldGateway 代码改造 + 一致性校验

## 第二批：中等复杂度 Gateway

- [ ] 3.1 ContractGateway YAML 配置
- [ ] 3.2 ContractGateway 代码改造 + 一致性校验
- [ ] 3.3 ContractQuotationRelationGateway YAML 配置
- [ ] 3.4 ContractQuotationRelationGateway 代码改造 + 一致性校验
- [ ] 3.5 SubOrderGateway YAML 配置
- [ ] 3.6 SubOrderGateway 代码改造 + 一致性校验

## 第三批：复杂 Gateway

- [ ] 4.1 ContractInstanceGateway YAML 配置
- [ ] 4.2 ContractInstanceGateway 代码改造 + 一致性校验
- [ ] 4.3 PersonalQuoteGateway YAML 配置
- [ ] 4.4 PersonalQuoteGateway 代码改造 + 一致性校验

## 验证阶段

- [ ] 5.1 运行单元测试：JsonPathResolverTest
- [ ] 5.2 运行集成测试：QaPairEvaluationIT（每个 Gateway 改造后都运行）
- [ ] 5.3 全量验证通过后，删除各 Gateway 的旧解析方法
- [ ] 5.4 更新 domain-ontology.yaml 移除不再需要的配置（可选）

## 扩展 JsonPathResolver（如需要）

- [ ] 6.1 根据实际需要扩展新的解析模式
- [ ] 6.2 为新模式添加单元测试
