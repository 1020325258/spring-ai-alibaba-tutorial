## Why

OntologyQueryEngine 存在多处代码坏味道，影响可读性和可维护性：重复的起始查询逻辑、复杂的条件判断嵌套、魔法字符串散落各处、结果组装逻辑重复。这些问题增加了理解成本和出错风险，需要通过重构提升代码质量。

## What Changes

- 简化对外接口：删除冗余的枚举版本重载方法，保留两个字符串版本入口
- 提取重复逻辑：将起始实体查询、结果组装等重复代码提取为独立方法
- 引入常量：将魔法字符串（"default"、"list"、"queryEntity" 等）定义为类常量
- 简化条件判断：提取 `shouldExpandRelations` 和 `buildRelationPaths` 方法，降低嵌套层级
- 优化日志拼接：使用 Stream API 简化 `logPathPlan` 方法
- 清理未使用代码：删除从未调用的 `attachPathResults` 方法
- 统一命名风格：将 `queryListOnly` / `queryWithScopeString` 重命名为 `queryWithoutExpansion` / `queryWithExpansion`

## Capabilities

### New Capabilities
- `simplified-query-interface`: 简化查询引擎对外接口，删除冗余重载方法
- `extracted-common-logic`: 提取重复的起始查询、结果组装、路径构建逻辑为独立方法
- `magic-string-constants`: 引入常量替代魔法字符串，提升可维护性

### Modified Capabilities
<!-- 无现有能力的需求变更，仅重构内部实现 -->

## Impact

- **代码文件**：`OntologyQueryEngine.java` 内部重构，对外接口保持向后兼容（仅删除未使用的枚举版本方法）
- **调用方**：现有调用字符串版本 `query()` 的代码无需修改
- **测试**：`OntologyQueryEngineTest` 需更新测试用例，删除枚举版本方法的测试
- **依赖**：无外部依赖变更
