## 1. YAML 配置更新

- [x] 1.1 移除 `domain-ontology.yaml` 中 `Order → PersonalQuote` 的关系定义
- [x] 1.2 新增 `ContractQuotationRelation → PersonalQuote` 关系定义，via 配置为 `{source_field: billCode, target_field: projectOrderId}`
- [x] 1.3 更新 PersonalQuote 实体描述，说明参数来源为 ContractQuotationRelation

## 2. PersonalQuoteGateway 重构

- [x] 2.1 重写 `queryByFieldWithContext` 方法，从 parentRecord 提取 billCode 和 bindType
- [x] 2.2 实现 bindType 到参数名的映射逻辑（1→billCodeList, 2→subOrderNoList, 3→changeOrderId）
- [x] 2.3 添加 bindType 无效值的防御性处理（返回空列表 + warn 日志）
- [x] 2.4 保留 `queryWithExtraParams` 方法作为独立查询入口（向后兼容）

## 3. 提示词更新

- [x] 3.1 更新 `prompts/sre-agent.md` 中的场景表，移除 PersonalQuote 直接查询场景
- [x] 3.2 添加 PersonalQuote 三跳查询场景示例（订单 → 合同 → 签约单据 → 个性化报价）
- [x] 3.3 更新 queryScope 参数列表，说明 PersonalQuote 需通过 ContractQuotationRelation 访问

## 4. 集成测试

- [x] 4.1 新增 PersonalQuote 三跳查询集成测试用例
- [x] 4.2 新增 bindType 映射测试（1/2/3 分别对应正确参数）
- [x] 4.3 新增无效 bindType 处理测试
- [x] 4.4 运行 `mvn test` 确保全量测试通过
