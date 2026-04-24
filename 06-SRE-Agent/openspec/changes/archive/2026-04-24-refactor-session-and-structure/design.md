## Context

当前项目结构存在以下问题：
1. **config 目录分散**：`config/` 和 `infrastructure/config/` 两个配置目录，增加认知负担
2. **提示词命名不一致**：`sre-agent.md` 实际用于 QueryAgent，但文件名暗示是通用 SRE Agent 提示词
3. **spec 文件名大小写不规范**：`SPEC.md` 应该是小写 `spec.md`

当前目录结构：
```
src/main/java/com/yycome/sreagent/
├── config/                    # Agent 相关配置
│   ├── AgentConfiguration.java
│   ├── SkillConfiguration.java
│   ├── SREAgentGraphConfiguration.java
│   └── node/                  # 节点实现
├── infrastructure/
│   ├── config/                # 基础设施配置（分散点）
│   │   ├── DataSourceConfiguration.java
│   │   ├── EnvironmentConfig.java
│   │   ├── OntologyWebConfig.java
│   │   └── SessionProperties.java
│   └── ...
```

## Goals / Non-Goals

**Goals:**
- 统一 config 目录结构，减少认知负担
- 规范文件命名，使其与实际用途一致
- 保持代码功能不变

**Non-Goals:**
- 不改变任何业务逻辑
- 不改变配置项的语义
- 不新增功能

## Decisions

### 1. config 目录统一方案

**决定**: 将 `infrastructure/config/` 移动到 `config/infra/`

**备选方案**:
- A. 全部移到 `config/` 根目录（扁平化）
- B. 保持现状，仅文档说明
- C. 移动到 `config/infra/` 子目录（选中）

**理由**:
- 方案 A 会导致 `config/` 目录文件过多，且混合了业务配置和基础设施配置
- 方案 B 不解决问题
- 方案 C 既统一了目录位置，又保持了分类清晰

### 2. 提示词文件重命名

**决定**: `sre-agent.md` → `query-agent.md`

**理由**:
- 该提示词实际用于 `QueryAgentNode`，标题也是"数据查询 Agent"
- 与 `investigate-agent.md` 命名风格一致
- 需要更新 `AgentConfiguration.java` 中的 `@Value` 引用

### 3. spec 文件名规范化

**决定**: `SPEC.md` → `spec.md`

**理由**:
- 遵循 kebab-case 命名规范
- 与其他 spec 文件命名一致

## Risks / Trade-offs

| 风险 | 缓解措施 |
|-----|---------|
| import 路径变更导致编译错误 | 使用 IDE 的 refactor 功能，全量更新 import |
| 遗漏文档引用 | 全局搜索旧文件名，确保所有引用已更新 |
| Git 历史追踪困难 | 使用 `git mv` 保留文件历史 |

## Migration Plan

1. 使用 IDE 重命名/移动功能，确保 import 自动更新
2. 全局搜索旧文件名，更新文档引用
3. 运行测试验证功能正常
4. 提交变更

## Open Questions

无。
