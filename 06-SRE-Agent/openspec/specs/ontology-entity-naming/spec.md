## MODIFIED Requirements

### Requirement: SignableOrderInfo 实体可通过 PersonalSignableOrderInfo 名称寻址
系统 SHALL 将可签约S单实体名从 `SignableOrderInfo` 更名为 `PersonalSignableOrderInfo`，
任何传入 `queryScope=PersonalSignableOrderInfo` 的 `ontologyQuery` 调用 SHALL 成功返回销售合同弹窗可签约S单数据。

#### Scenario: 使用新实体名查询销售合同弹窗S单
- **WHEN** 调用 `ontologyQuery(entity=Order, value={订单号}, queryScope=PersonalSignableOrderInfo)`
- **THEN** 系统返回该订单下销售合同的弹窗可签约S单列表

#### Scenario: 使用旧实体名 SignableOrderInfo 时查询失败
- **WHEN** 调用 `ontologyQuery(entity=Order, value={订单号}, queryScope=SignableOrderInfo)`
- **THEN** 系统返回空结果或实体未找到错误（旧名称已不存在）

#### Scenario: 排查场景 SKILL.md 步骤调用正确实体名
- **WHEN** investigateAgent 执行 `sales-contract-sign-dialog-diagnosis` Skill 的第一步查询
- **THEN** 调用参数为 `queryScope=PersonalSignableOrderInfo`

---

### Requirement: PersonalSignableOrderInfo displayName 和 aliases 准确描述业务
`PersonalSignableOrderInfo` 实体 SHALL 具有 displayName "销售合同弹窗可签约S单"，
aliases SHALL 包含"销售合同弹窗S单"，以便 LLM 通过自然语言描述定位该实体。

#### Scenario: LLM 通过 displayName 识别实体
- **WHEN** 用户问及"销售合同弹窗可签约S单"相关数据
- **THEN** `ontologyQuery` 参数中 entity 或 queryScope 为 `PersonalSignableOrderInfo`

---

### Requirement: FormalSignableOrderInfo displayName 更新为正签合同弹窗可签约S单
`FormalSignableOrderInfo` 实体 SHALL 具有 displayName "正签合同弹窗可签约S单"，
aliases SHALL 包含"正签合同弹窗S单"。实体名（`FormalSignableOrderInfo`）保持不变。

#### Scenario: FormalSignableOrderInfo 仍可通过原实体名查询
- **WHEN** 调用 `ontologyQuery(entity=Order, value={订单号}, queryScope=FormalSignableOrderInfo)`
- **THEN** 系统正常返回正签合同弹窗可签约S单数据
