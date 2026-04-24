## ADDED Requirements

### Requirement: 查询合同基本信息
系统 SHALL 通过 HTTP 接口查询合同基本信息，直接返回 Contract 实体。

#### Scenario: 根据合同编号查询
- **WHEN** 调用 GET /sre/contract?contractCode=C1767150648920281&app=sreAgent
- **THEN** 返回 Contract 实体（包含 contractCode、type、status、amount、platformInstanceId、projectOrderId、ctime 等字段）

#### Scenario: 合同不存在
- **WHEN** 调用 GET /sre/contract?contractCode=NOT_EXIST&app=sreAgent
- **THEN** 返回 null

### Requirement: 查询合同节点
系统 SHALL 通过 HTTP 接口查询合同节点列表，直接返回 ContractNode 实体列表。

#### Scenario: 根据合同编号查询节点
- **WHEN** 调用 GET /sre/contract/node?contractCode=C1767150648920281&app=sreAgent
- **THEN** 返回 ContractNode 实体列表

### Requirement: 查询签约人
系统 SHALL 通过 HTTP 接口查询签约人列表，直接返回 ContractUser 实体列表。

#### Scenario: 根据合同编号查询签约人
- **WHEN** 调用 GET /sre/contract/user?contractCode=C1767150648920281&app=sreAgent
- **THEN** 返回 ContractUser 实体列表（phone 字段为密文）

### Requirement: 查询合同扩展字段
系统 SHALL 通过 HTTP 接口查询合同扩展字段，返回 Map<String, Object>。

#### Scenario: 根据合同编号查询扩展字段
- **WHEN** 调用 GET /sre/contract/field?contractCode=C1767150648920281&app=sreAgent
- **THEN** 返回 Map（fieldKey -> fieldValue）

#### Scenario: 分表路由正确
- **WHEN** contractCode 的数字部分取模后指向特定分表
- **THEN** 服务端正确路由到 contract_field_sharding_{shard} 表查询

### Requirement: 查询签约单据
系统 SHALL 通过 HTTP 接口查询签约单据列表，直接返回 ContractQuotationRelation 实体列表。

#### Scenario: 根据合同编号查询签约单据
- **WHEN** 调用 GET /sre/contract/quotation-relation?contractCode=C1767150648920281&app=sreAgent
- **THEN** 返回 ContractQuotationRelation 实体列表

### Requirement: 根据订单号查询合同列表
系统 SHALL 通过 HTTP 接口根据订单号查询合同列表，返回 Contract 实体列表。

#### Scenario: 根据订单号查询
- **WHEN** 调用 GET /sre/contract/list-by-order?projectOrderId=826022518000001562&app=sreAgent
- **THEN** 返回 Contract 实体列表

### Requirement: 查询合同配置字段
系统 SHALL 通过 HTTP 接口查询合同配置相关字段，返回 Contract 实体（部分字段）。

#### Scenario: 根据合同编号查询配置字段
- **WHEN** 调用 GET /sre/contract/config-field?contractCode=C1767150648920281&app=sreAgent
- **THEN** 返回 Contract 实体（包含 projectOrderId、businessType、gbCode、companyCode、type 字段）

### Requirement: 查询配置ID
系统 SHALL 通过 HTTP 接口查询配置ID，直接返回字符串。

#### Scenario: 根据订单号查询配置ID
- **WHEN** 调用 GET /sre/project-config-snap/config-id?projectOrderId=826022518000001562&app=sreAgent
- **THEN** 返回 contractConfigId 字符串

### Requirement: 查询城市公司配置
系统 SHALL 通过 HTTP 接口查询城市公司配置列表，直接返回 ContractCityCompanyInfo 实体列表。

#### Scenario: 根据条件查询城市公司配置
- **WHEN** 调用 GET /sre/contract-city-company-info?businessType=1&gbCode=110000&companyCode=C001&version=1&type=1&app=sreAgent
- **THEN** 返回 ContractCityCompanyInfo 实体列表

### Requirement: 接口鉴权
系统 SHALL 验证 app 参数为 sreAgent 才返回数据。

#### Scenario: 鉴权成功
- **WHEN** 调用接口时 app=sreAgent
- **THEN** 返回正常数据

#### Scenario: 鉴权失败
- **WHEN** 调用接口时 app=invalid
- **THEN** 返回 null

### Requirement: 轻量化接口实现
接口 SHALL 直接返回数据库实体或 Map，无需创建额外的 DTO 类。

#### Scenario: 返回实体
- **WHEN** 接口查询单表数据
- **THEN** 直接返回对应的实体类（如 Contract、ContractNode）

#### Scenario: 返回 Map
- **WHEN** 接口查询分表或需要聚合数据
- **THEN** 返回 Map<String, Object>
