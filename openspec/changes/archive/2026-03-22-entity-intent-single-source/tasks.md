## 1. 实体重命名（ContractForm → ContractInstance）

- [x] 1.1 修改 `domain-ontology.yaml`：ContractForm 重命名为 ContractInstance，扩展 aliases 为 `["实例", "实例信息", "版式", "版式数据", "form_id", "版式ID", "instanceId"]`
- [x] 1.2 重命名 `ContractFormGateway.java` → `ContractInstanceGateway.java`
- [x] 1.3 更新 `ContractInstanceGateway` 中的 `getEntityName()` 返回 "ContractInstance"
- [x] 1.4 更新所有引用 ContractForm 的关系定义（domain-ontology.yaml）

## 2. EntityRegistry 增强

- [x] 2.1 在 `EntityRegistry` 中新增 `getEntitySummaryForPrompt()` 方法
- [x] 2.2 方法输出格式：实体名称 + displayName + 别名 + 查询入口
- [x] 2.3 添加单元测试 `EntityRegistryTest.testGetEntitySummaryForPrompt()`

## 3. 提示词注入机制

- [x] 3.1 在 `AgentConfiguration.sreAgent()` 中读取 EntityRegistry
- [x] 3.2 将 `{{entity_summary}}` 替换为 `getEntitySummaryForPrompt()` 返回内容
- [x] 3.3 更新 `sre-agent.md`：移除硬编码决策表，添加 `{{entity_summary}}` 占位符

## 4. 提示词精简

- [x] 4.1 移除 `sre-agent.md` 中的"编号格式 → entity"硬编码表格
- [x] 4.2 保留 3-5 个典型样例供 LLM 理解模式
- [x] 4.3 更新决策规则说明：意图优先 + 上下文推断值类型

## 5. OntologyQueryTool 改造

- [x] 5.1 移除 `inferEntityFromValue()` 中的格式推断逻辑
- [x] 5.2 保留 entity 合法性校验（检查是否在 EntityRegistry 中存在）
- [x] 5.3 更新单元测试 `OntologyQueryToolTest`

## 6. 集成测试更新

- [x] 6.1 新增测试：用户输入 instanceId 查询 ContractInstance
- [x] 6.2 新增测试：用户输入"825123110000002753的合同"识别为 Order → Contract
- [x] 6.3 新增测试：用户输入"101835395的实例信息"识别为 ContractInstance
- [x] 6.4 更新现有测试：ContractForm 相关测试改为 ContractInstance

## 7. 运行全量测试

- [x] 7.1 运行 `mvn test` 确保所有测试通过
- [x] 7.2 运行 `ContractOntologyIT` 集成测试验证 LLM 行为
