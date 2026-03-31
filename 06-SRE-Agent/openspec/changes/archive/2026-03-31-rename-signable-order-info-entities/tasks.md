## 1. 更新 domain-ontology.yaml

- [x] 1.1 将 `SignableOrderInfo` 实体的 `name` 改为 `PersonalSignableOrderInfo`，`displayName` 改为 "销售合同弹窗可签约S单"，`aliases` 补充 "销售合同弹窗S单"、"个人合同弹窗S单"
- [x] 1.2 将关系配置 `to: SignableOrderInfo` 改为 `to: PersonalSignableOrderInfo`
- [x] 1.3 将 `FormalSignableOrderInfo` 实体的 `displayName` 改为 "正签合同弹窗可签约S单"，`aliases` 补充 "正签合同弹窗S单"

## 2. 重命名 Java Gateway 类

- [x] 2.1 将 `SignableOrderInfoGateway.java` 文件重命名为 `PersonalSignableOrderInfoGateway.java`，类名同步修改
- [x] 2.2 `PersonalSignableOrderInfoGateway.getEntityName()` 返回值改为 `"PersonalSignableOrderInfo"`
- [x] 2.3 更新类中所有日志前缀，将 `[SignableOrderInfoGateway]` 改为 `[PersonalSignableOrderInfoGateway]`
- [x] 2.4 更新类 Javadoc 注释，将 "SignableOrderInfo（弹窗可签约S单）" 改为 "PersonalSignableOrderInfo（销售合同弹窗可签约S单）"

## 3. 更新 Prompt 和 SKILL.md

- [x] 3.1 `src/main/resources/prompts/sre-agent.md`：将 `SignableOrderInfo` 替换为 `PersonalSignableOrderInfo`
- [x] 3.2 `src/main/resources/skills/sales-contract-sign-dialog-diagnosis/SKILL.md`：将所有 `SignableOrderInfo` 替换为 `PersonalSignableOrderInfo`，将"弹窗可签约S单"替换为"销售合同弹窗可签约S单"

## 4. 测试验证

- [x] 4.1 运行集成测试 `./run-integration-tests.sh`，确保排查场景（`InvestigateAgentIT`）和查询场景（`ContractOntologyIT`）全部通过
