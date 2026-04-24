## ADDED Requirements

### Requirement: ContractUser 实体定义
系统 SHALL 在本体模型中定义 ContractUser 实体，支持通过 contractCode 查询合同签约人信息。

#### Scenario: 实体属性完整性
- **WHEN** 定义 ContractUser 实体
- **THEN** 包含以下属性：contractCode（合同编号）、roleType（角色类型）、name（姓名）、phone（手机号）、certificateNo（证件号）、ctime（创建时间）、mtime（更新时间）

#### Scenario: 实体别名支持
- **WHEN** 用户使用"签约人"、"合同用户"、"业主"、"代理人"、"代办人"等别名
- **THEN** LLM 能正确识别为 ContractUser 实体

---

### Requirement: Contract 关联 ContractUser
系统 SHALL 建立 Contract 到 ContractUser 的关联关系，支持通过合同编号查询签约人。

#### Scenario: 单合同查询签约人
- **WHEN** 用户输入 "C1767150648920281 的签约人"
- **THEN** LLM 调用 ontologyQuery(entity="Contract", value="C1767150648920281", queryScope="ContractUser")

#### Scenario: 合同关联展开
- **WHEN** queryScope 包含 ContractUser
- **THEN** 合同记录下挂载 contractUsers 字段，包含该合同的所有签约人记录

---

### Requirement: 数据过滤
ContractUserGateway SHALL 过滤已删除的记录，仅返回 del_status=0 的有效数据。

#### Scenario: 排除已删除记录
- **WHEN** contract_user 表中存在 del_status=1 的记录
- **THEN** 查询结果不包含该记录

---

### Requirement: 角色类型枚举
系统 SHALL 支持 roleType 字段的枚举值解释。

#### Scenario: 角色类型映射
- **WHEN** 查询返回 roleType 值
- **THEN** 系统提供枚举解释：1-业主、2-代理人、3-公司代办人
