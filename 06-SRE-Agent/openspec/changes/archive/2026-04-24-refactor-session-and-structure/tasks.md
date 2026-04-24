## 1. SessionId 一致性确认

- [x] 1.1 确认前端是否正确传递 `X-Session-Id` header
- [x] 1.2 如需修改，更新 `ChatController.generateSessionId()` 逻辑或前端代码

## 2. 统一 config 目录

- [x] 2.1 创建 `config/infra/` 目录
- [x] 2.2 移动 `infrastructure/config/DataSourceConfiguration.java` → `config/infra/DataSourceConfiguration.java`
- [x] 2.3 移动 `infrastructure/config/EnvironmentConfig.java` → `config/infra/EnvironmentConfig.java`
- [x] 2.4 移动 `infrastructure/config/OntologyWebConfig.java` → `config/infra/OntologyWebConfig.java`
- [x] 2.5 移动 `infrastructure/config/SessionProperties.java` → `config/infra/SessionProperties.java`
- [x] 2.6 更新所有 import 路径：`com.yycome.sreagent.infrastructure.config` → `com.yycome.sreagent.config.infra`
- [x] 2.7 删除空的 `infrastructure/config/` 目录

## 3. 重命名提示词文件

- [x] 3.1 重命名 `prompts/sre-agent.md` → `prompts/query-agent.md`
- [x] 3.2 更新 `AgentConfiguration.java` 中的 `@Value("classpath:prompts/sre-agent.md")` → `@Value("classpath:prompts/query-agent.md")`
- [x] 3.3 更新 CLAUDE.md 中所有 `sre-agent.md` 引用

## 4. 规范 spec 文件名

- [x] 4.1 重命名 `openspec/specs/session-context-memory/SPEC.md` → `openspec/specs/session-context-memory/spec.md`

## 5. 验证

- [x] 5.1 运行编译验证无错误
- [x] 5.2 运行测试验证功能正常（注：有 1 个 LLM-as-Judge 评估失败，与本次重构无关）
