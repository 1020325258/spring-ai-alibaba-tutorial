## 1. 准备工作

- [x] 1.1 全局搜索 `QueryScope.` 确认无枚举版本方法的直接调用
- [x] 1.2 备份当前 `OntologyQueryEngine.java` 文件

## 2. 定义常量

- [x] 2.1 在类顶部定义 scope 常量：`SCOPE_DEFAULT`, `SCOPE_LIST`
- [x] 2.2 在类顶部定义结果 key 常量：`KEY_QUERY_ENTITY`, `KEY_QUERY_VALUE`, `KEY_RECORDS`

## 3. 提取公共方法

- [x] 3.1 提取 `queryStartEntity(String entityName, String value)` 方法，包含策略匹配、Gateway 查询和日志
- [x] 3.2 提取 `buildQueryResult(String entityName, String value, List<Map<String, Object>> records)` 方法
- [x] 3.3 提取 `shouldExpandRelations(String queryScope)` 方法，使用常量判断
- [x] 3.4 提取 `buildRelationPaths(String entityName, String queryScope)` 方法，处理逗号分隔和路径查找

## 4. 重命名现有方法

- [x] 4.1 将 `queryListOnly` 重命名为 `queryWithoutExpansion`
- [x] 4.2 将 `queryWithScopeString` 重命名为 `queryWithExpansion`

## 5. 重构方法实现

- [x] 5.1 重构 `queryWithoutExpansion`：调用 `queryStartEntity` 和 `buildQueryResult`
- [x] 5.2 重构 `queryWithExpansion`：调用 `queryStartEntity`、`shouldExpandRelations`、`buildRelationPaths` 和 `buildQueryResult`
- [x] 5.3 更新 `query(String, String, String)` 方法中对重命名方法的调用
- [x] 5.4 更新 `query(String, String, String, Map)` 方法中对重命名方法的调用

## 6. 删除冗余代码

- [x] 6.1 删除 `query(String, String, QueryScope)` 方法（第 87-89 行）
- [x] 6.2 删除 `query(String, String, QueryScope, Map)` 方法（第 94-101 行）
- [x] 6.3 删除未使用的 `attachPathResults` 方法（第 200-220 行）

## 7. 优化日志方法

- [x] 7.1 重构 `logPathPlan` 方法，使用 Stream API 替代 StringBuilder
- [x] 7.2 删除冗余的 `if (i == 0)` 分支

## 8. 更新测试

- [x] 8.1 更新 `OntologyQueryEngineTest`，删除枚举版本方法的测试用例
- [x] 8.2 运行 `OntologyQueryEngineTest` 确保所有单元测试通过
- [x] 8.3 运行 `ContractOntologyIT` 集成测试验证端到端行为

## 9. 验证和清理

- [x] 9.1 检查代码中所有魔法字符串是否已替换为常量
- [x] 9.2 确认所有方法调用已更新为新方法名
- [x] 9.3 运行完整测试套件：`./run-integration-tests.sh`
- [x] 9.4 代码审查：检查是否有遗漏的重复逻辑或命名不一致
