## Why

当前本体模型中缺少合同签约人实体，导致用户无法通过自然语言查询合同的签约人信息（如"查合同的签约人"、"合同有哪些代理人"等）。需要在本体模型中添加 `ContractUser` 实体，与 `Contract` 建立关联，支持基于合同维度的签约人信息查询。

## What Changes

- 新增 `ContractUser` 实体定义，包含签约人基本信息（姓名、手机号、证件号、角色类型等）
- 在本体配置中建立 `Contract` → `ContractUser` 的关联关系（基于 `contractCode`）
- 新增 `ContractUserRepository` 数据访问层，从 `contract_user` 表查询数据
- 更新本体查询引擎支持新实体的展开查询

## Capabilities

### New Capabilities

- `contract-user-query`: 合同签约人实体查询能力，支持通过合同编号查询关联的签约人信息，包含角色类型（业主/代理人/代办人）、姓名、手机号、证件号等字段

### Modified Capabilities

- `ontology-query-engine`: 扩展本体查询引擎，支持 ContractUser 实体的展开查询

## Impact

- **本体配置**: `src/main/resources/ontology/domain-ontology.yaml` 新增 ContractUser 实体定义
- **数据访问层**: 新增 `ContractUserRepository` 类
- **查询引擎**: `OntologyQueryEngine` 支持新实体类型
- **Agent Prompt**: 可能需要更新提示词以告知 LLM 新实体可用
