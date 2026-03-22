## ADDED Requirements

### Requirement: ContractQuotationRelation 到 PersonalQuote 的关联
系统 SHALL 支持从 ContractQuotationRelation（签约单据）查询 PersonalQuote（个性化报价）数据，通过 billCode 和 bindType 字段自动映射查询参数。

#### Scenario: bindType=1 映射到 billCodeList
- **WHEN** ContractQuotationRelation 的 bindType=1（报价单）
- **THEN** billCode 映射到 PersonalQuote 查询的 `billCodeList` 参数

#### Scenario: bindType=2 映射到 subOrderNoList
- **WHEN** ContractQuotationRelation 的 bindType=2（S单号）
- **THEN** billCode 映射到 PersonalQuote 查询的 `subOrderNoList` 参数

#### Scenario: bindType=3 映射到 changeOrderId
- **WHEN** ContractQuotationRelation 的 bindType=3（变更单号）
- **THEN** billCode 映射到 PersonalQuote 查询的 `changeOrderId` 参数

---

### Requirement: 三跳路径查询
系统 SHALL 支持从 Order（订单）通过三跳路径查询 PersonalQuote：Order → Contract → ContractQuotationRelation → PersonalQuote。

#### Scenario: 订单查询个性化报价
- **WHEN** 用户查询"订单 826031018000004758 的个性化报价数据"
- **THEN** 系统先查询订单下的所有合同，再查询每个合同的签约单据，最后用签约单据的 billCode 查询个性化报价

#### Scenario: 多合同聚合
- **WHEN** 订单下有多个合同，每个合同有多个签约单据
- **THEN** 系统并行查询所有签约单据的个性化报价，结果聚合在对应合同下

---

### Requirement: 无效 bindType 处理
系统 SHALL 对 bindType 不在 1/2/3 范围的签约单据返回空列表并记录警告日志。

#### Scenario: bindType 值异常
- **WHEN** ContractQuotationRelation 的 bindType 为 0 或其他非预期值
- **THEN** 返回空列表，并打印 warn 级别日志，包含 contractCode 和 bindType 值
