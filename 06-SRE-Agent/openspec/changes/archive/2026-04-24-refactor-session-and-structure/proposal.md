## Why

当前项目存在4个结构和命名不一致的问题：sessionId 在每次请求时都可能重新生成、config 配置分散在两个目录增加认知负担、提示词文件命名与实际用途不匹配、spec 文件名大小写不规范。这些问题影响代码可维护性和开发效率。

## What Changes

1. **SessionId 保持一致性**
   - 当前端传入 `X-Session-Id` header 时，保持该值不变，确保同一会话窗口内的请求使用相同的 sessionId
   - 当前逻辑已支持此行为，需确认前端正确传递 `X-Session-Id`

2. **统一 config 目录到根目录**
   - 将 `infrastructure/config/` 下的配置类移动到 `config/` 目录
   - 在 `config/` 下创建子目录进行分类：
     - `config/agent/` - Agent 相关配置
     - `config/infra/` - 基础设施配置（DataSource、Environment、Web 等）
   - **BREAKING**: 所有 `infrastructure.config` 包的 import 路径需要更新

3. **重命名提示词文件**
   - `sre-agent.md` → `query-agent.md`（与 QueryAgentNode 命名一致）
   - 更新所有引用该文件的代码和文档

4. **规范 spec 文件名**
   - `session-context-memory/SPEC.md` → `session-context-memory/spec.md`

## Capabilities

### New Capabilities

无新增能力。

### Modified Capabilities

无需求变更，仅重构代码结构和命名。

## Impact

- **代码结构**：`infrastructure/config/` 目录移动到 `config/infra/`
- **Import 路径**：涉及 `infrastructure.config` 包的 import 需要更新
- **资源引用**：`prompts/sre-agent.md` → `prompts/query-agent.md`
- **文档更新**：CLAUDE.md 中相关引用需要同步更新
