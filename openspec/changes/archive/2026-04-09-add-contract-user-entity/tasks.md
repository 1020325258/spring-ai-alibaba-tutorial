## 1. 本体配置

- [x] 1.1 在 `domain-ontology.yaml` 中添加 ContractUser 实体定义
- [x] 1.2 在 `domain-ontology.yaml` 中添加 Contract → ContractUser 关联关系

## 2. 数据访问层

- [x] 2.1 创建 `ContractUserGateway` 类，实现 `EntityDataGateway` 接口
- [x] 2.2 实现 `queryByField` 方法，通过 JdbcTemplate 查询 contract_user 表
- [x] 2.3 添加 del_status=0 过滤条件

## 3. 枚举扩展

- [x] 3.1 在 `QueryScope` 枚举中添加 `CONTRACT_USER` 选项

## 4. 提示词更新

- [x] 4.1 在 `query-agent.md` 的实体列表中添加 ContractUser 说明
- [x] 4.2 在 `query-agent.md` 的 queryScope 参数说明中添加 ContractUser 选项

## 5. 集成测试

- [x] 5.1 在 `ContractOntologyIT` 中添加 ContractUser 查询测试用例
- [x] 5.2 运行集成测试验证功能正确性

## 6. 后处理增强（手机号解密 + ucid 查询）

- [x] 6.1 添加 `sre-decrypt` 接口配置（解密手机号）
- [x] 6.2 添加 `user-phone-query` 接口配置（根据手机号查询 ucid）
- [x] 6.3 更新 `ContractUserGateway`，查询后调用解密和 ucid 查询接口
- [x] 6.4 更新本体模型，添加 `phonePlain` 和 `ucid` 属性
