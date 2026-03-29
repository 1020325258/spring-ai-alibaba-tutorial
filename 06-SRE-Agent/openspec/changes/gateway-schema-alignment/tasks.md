## 1. 前置修复：YAML 与 Gateway 现存不一致

- [ ] 1.1 `ContractDao.fetchContractsByOrderId()`：SQL SELECT 增加 `project_order_id`，结果 Map 加入 `projectOrderId`
- [ ] 1.2 `ContractDao.fetchNodes()`：结果 Map 加入 `contractCode`（入参中已有，直接回填）
- [ ] 1.3 `domain-ontology.yaml`：修正 `PersonalQuote.attributes`，改为实际出参字段（`billCode`、`personalContractPrice`、`organizationCode`、`organizationName`、`createTime`、`quoteFileUrl`、`quotePrevUrl`）
- [ ] 1.4 `domain-ontology.yaml`：`BudgetBill` 补充 `billType`、`billTypeDesc`、`statusDesc`、`originalBillCode`
- [ ] 1.5 `domain-ontology.yaml`：`ContractConfig` 补充 `signChannelType`
- [ ] 1.6 `domain-ontology.yaml`：`ContractField` 加 `dynamic: true` 标记

## 2. 模型扩展

- [ ] 2.1 `OntologyEntity.java`：增加 `private boolean dynamic` 字段（默认 false）

## 3. 新增 OntologySchemaEnforcer

- [ ] 3.1 新建 `domain/ontology/engine/OntologySchemaEnforcer.java`，实现 `enforceAll(String entityName, List<Map<String,Object>> records)` 方法
- [ ] 3.2 SchemaEnforcer 逻辑：`dynamic: true` 实体直接放行；空 attributes 实体直接放行；`_` 前缀字段始终保留；声明字段缺失补 null（WARN）；多余字段过滤（WARN）
- [ ] 3.3 为 `OntologySchemaEnforcer` 编写单元测试（覆盖：过滤多余字段、补 null 缺失字段、系统字段保留、dynamic 放行）

## 4. 接入 OntologyQueryEngine

- [ ] 4.1 `OntologyQueryEngine` 注入 `OntologySchemaEnforcer`
- [ ] 4.2 在首层 Gateway 调用后（`queryListOnly` 或等效方法）接入 `enforceAll`
- [ ] 4.3 在多跳展开（`attachLayer`/`attachMultiPathResults`）获取子实体结果后接入 `enforceAll`

## 5. 新增 GatewaySchemaValidator

- [ ] 5.1 新建 `domain/ontology/service/GatewaySchemaValidator.java`，实现 `ApplicationListener<ContextRefreshedEvent>`
- [ ] 5.2 启动校验逻辑：Gateway 实体名在 YAML 中不存在 → 抛异常；YAML 实体无 Gateway → WARN；relation `via.target_field` 未在目标实体 attributes 中 → WARN；INFO 输出注册摘要

## 6. 枚举与提示词补全

- [ ] 6.1 `QueryScope.java`：增加 `SIGNABLE_ORDER_INFO`、`FORMAL_SIGNABLE_ORDER_INFO`、`PERSONAL_QUOTE` 三个枚举值
- [ ] 6.2 `prompts/sre-agent.md`：同步更新 queryScope 可用值列表

## 7. 验证

- [ ] 7.1 运行单元测试：`./scripts/run-unit-tests.sh`，确认 `OntologySchemaEnforcer` 测试全部通过
- [ ] 7.2 运行集成测试：`./run-integration-tests.sh`，确认 `ContractOntologyIT` 全部通过，启动日志无 SchemaEnforcer WARN
