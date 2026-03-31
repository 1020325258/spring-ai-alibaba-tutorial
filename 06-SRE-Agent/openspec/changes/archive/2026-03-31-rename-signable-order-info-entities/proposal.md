## Why

`SignableOrderInfo` 命名不能区分销售合同和正签合同两种场景的弹窗S单，`FormalSignableOrderInfo` 的 displayName "正签可签约S单"也未体现"弹窗"语义。统一规范为"销售合同弹窗可签约S单（PersonalSignableOrderInfo）"和"正签合同弹窗可签约S单"，让命名精确反映业务含义。

## What Changes

- **BREAKING** `SignableOrderInfo` 实体名重命名为 `PersonalSignableOrderInfo`（影响 `queryScope` 参数值）
- `SignableOrderInfo` 的 displayName 从"弹窗可签约S单"更新为"销售合同弹窗可签约S单"
- `FormalSignableOrderInfo` 的 displayName 从"正签可签约S单"更新为"正签合同弹窗可签约S单"
- Java 类 `SignableOrderInfoGateway` 重命名为 `PersonalSignableOrderInfoGateway`（含文件名）
- `domain-ontology.yaml` 关系配置中 `to: SignableOrderInfo` 同步更新为 `to: PersonalSignableOrderInfo`
- `sre-agent.md` Prompt 中的 `SignableOrderInfo` 引用更新
- `sales-contract-sign-dialog-diagnosis/SKILL.md` 中的 `SignableOrderInfo` 引用更新

## Capabilities

### New Capabilities

### Modified Capabilities

- `ontology-entity-naming`: `SignableOrderInfo` 重命名为 `PersonalSignableOrderInfo`，影响本体模型实体可寻址名称（queryScope 参数值）

## Impact

- `src/main/resources/ontology/domain-ontology.yaml`：实体名、displayName、关系配置
- `src/main/java/.../gateway/SignableOrderInfoGateway.java` → 重命名为 `PersonalSignableOrderInfoGateway.java`
- `src/main/resources/prompts/sre-agent.md`：queryScope 示例
- `src/main/resources/skills/sales-contract-sign-dialog-diagnosis/SKILL.md`：queryScope 调用示例
- **外部调用影响**：任何传入 `queryScope=SignableOrderInfo` 的调用需更新为 `queryScope=PersonalSignableOrderInfo`
