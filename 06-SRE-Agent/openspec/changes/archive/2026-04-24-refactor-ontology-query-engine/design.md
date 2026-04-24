## Context

OntologyQueryEngine 是本体论查询引擎的核心类，负责根据起始实体和目标范围执行多跳关联查询。当前实现存在以下问题：

1. **接口冗余**：4 个重载方法（字符串版本 + 枚举版本），但枚举版本内部仍转换为字符串处理
2. **代码重复**：起始实体查询逻辑在 `queryListOnly` 和 `queryWithScopeString` 中重复
3. **魔法字符串**：`"default"`, `"list"`, `"queryEntity"` 等字符串散落在代码中
4. **复杂嵌套**：`queryWithScopeString` 中的条件判断和路径构建逻辑嵌套 3 层
5. **命名不一致**：`queryListOnly` vs `queryWithScopeString` 命名风格不统一

约束：
- 必须保持向后兼容，现有调用字符串版本 `query()` 的代码不能受影响
- 不能改变查询引擎的核心逻辑（策略匹配、路径规划、多跳查询）
- 重构后必须通过 `OntologyQueryEngineTest` 所有单元测试

## Goals / Non-Goals

**Goals:**
- 简化对外接口：删除冗余的枚举版本方法，保留两个字符串版本入口
- 消除代码重复：提取公共逻辑为独立方法（起始查询、结果组装、路径构建）
- 提升可读性：引入常量、简化条件判断、统一命名风格
- 清理死代码：删除未使用的 `attachPathResults` 方法

**Non-Goals:**
- 不改变查询引擎的核心算法（策略匹配、路径查找、并行查询）
- 不修改 Gateway 接口或本体论模型
- 不优化查询性能（本次仅关注代码质量）
- 不增加新功能

## Decisions

### Decision 1: 删除枚举版本方法，统一使用字符串接口

**选择**：删除 `query(String, String, QueryScope)` 和 `query(String, String, QueryScope, Map)` 两个方法

**理由**：
- 字符串版本已通过 `QueryScope.fromString()` 处理枚举转换
- 枚举版本内部仍调用字符串版本，属于冗余封装
- 支持逗号分隔的多目标查询（如 `"ContractNode,PersonalQuote"`）无法用枚举表示

**替代方案考虑**：
- 保留枚举版本作为类型安全的入口 → 拒绝，因为多目标查询无法用枚举表示，且增加维护成本

### Decision 2: 提取四个私有方法消除重复

**选择**：提取以下方法
- `queryStartEntity(String entityName, String value)` - 起始实体查询
- `buildQueryResult(String entityName, String value, List<Map<String, Object>> records)` - 结果组装
- `buildRelationPaths(String entityName, String queryScope)` - 路径构建
- `shouldExpandRelations(String queryScope)` - 展开判断

**理由**：
- 起始查询逻辑在两处重复（106-119 行 vs 132-145 行）
- 结果组装逻辑在两处重复（115-119 行 vs 167-171 行）
- 路径构建逻辑嵌套在 `queryWithScopeString` 中，难以理解
- 展开判断条件复杂（`queryScope != null && !"default".equals(queryScope) && !"list".equals(queryScope)`）

**替代方案考虑**：
- 使用模板方法模式 → 拒绝，过度设计，简单提取方法即可满足需求

### Decision 3: 引入常量替代魔法字符串

**选择**：定义以下常量
```java
private static final String SCOPE_DEFAULT = "default";
private static final String SCOPE_LIST = "list";
private static final String KEY_QUERY_ENTITY = "queryEntity";
private static final String KEY_QUERY_VALUE = "queryValue";
private static final String KEY_RECORDS = "records";
```

**理由**：
- 防止拼写错误（编译期检查）
- 便于全局修改（单点变更）
- 提升代码可读性（语义化命名）

**替代方案考虑**：
- 使用枚举 → 拒绝，简单字符串常量即可，无需引入枚举复杂度

### Decision 4: 重命名方法统一命名风格

**选择**：
- `queryListOnly` → `queryWithoutExpansion`
- `queryWithScopeString` → `queryWithExpansion`

**理由**：
- 对比更清晰：`WithoutExpansion` vs `WithExpansion`
- 语义更准确：强调是否展开关联，而非参数类型

**替代方案考虑**：
- 保持原命名 → 拒绝，命名不一致影响可读性

### Decision 5: 使用 Stream API 简化日志拼接

**选择**：将 `logPathPlan` 中的 StringBuilder 改为 Stream API

**理由**：
- 消除冗余的 `if (i == 0)` 分支（两个分支逻辑完全相同）
- Stream API 更简洁，符合现代 Java 风格

**替代方案考虑**：
- 保持 StringBuilder → 拒绝，代码冗余且可读性差

## Risks / Trade-offs

### Risk 1: 删除枚举版本方法可能影响现有调用方

**风险**：如果有代码直接调用 `query(entityName, value, QueryScope.CONTRACT)`，重构后会编译失败

**缓解措施**：
1. 在项目中全局搜索 `QueryScope.` 的使用，确认无直接调用
2. 如果发现调用，修改为字符串版本 `query(entityName, value, "Contract")`
3. 更新 `OntologyQueryEngineTest`，删除枚举版本的测试用例

### Risk 2: 提取方法可能引入新 bug

**风险**：提取逻辑时可能遗漏边界条件或改变执行顺序

**缓解措施**：
1. 提取方法时保持逻辑完全一致，不做任何行为变更
2. 运行 `OntologyQueryEngineTest` 确保所有测试通过
3. 运行 `ContractOntologyIT` 集成测试验证端到端行为

### Risk 3: 常量命名可能与未来需求冲突

**风险**：如果未来需要支持更多 scope 类型，常量命名可能不够通用

**缓解措施**：
- 当前常量仅用于内部判断，未来扩展时可直接修改常量定义
- 如需支持更多类型，可考虑引入枚举或配置化

## Trade-offs

### Trade-off 1: 方法数量增加 vs 代码重复

**选择**：增加 4 个私有方法，消除重复逻辑

**代价**：类的方法数量从 10 个增加到 13 个（删除 3 个公有方法 + 删除 1 个未使用方法 + 增加 4 个私有方法）

**收益**：消除重复代码，降低维护成本，提升可读性

### Trade-off 2: 向后兼容 vs 接口简洁

**选择**：保留字符串版本方法，删除枚举版本方法

**代价**：失去枚举的类型安全（调用方可能传入无效字符串）

**收益**：接口更简洁，支持多目标查询，减少维护成本

**判断**：字符串版本已通过 `QueryScope.fromString()` 和 `entityRegistry.findRelationPath()` 进行校验，运行时会抛出清晰的异常，类型安全问题可接受
