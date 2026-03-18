## ADDED Requirements

### Requirement: 环境配置与切换
系统 SHALL 支持多测试环境切换，通过 `/env` 控制台命令查看和切换当前环境，无需重启应用。

#### Scenario: 查看当前环境
- **WHEN** 用户在控制台输入 `/env`
- **THEN** 输出当前激活的环境名称及可用环境列表

#### Scenario: 切换环境
- **WHEN** 用户输入 `/env offline-beta`
- **THEN** 系统切换到 `offline-beta` 环境，后续 HTTP 接口调用使用对应域名

#### Scenario: 接口模板 URL 占位符替换
- **WHEN** 接口模板中 URL 含 `${env}` 占位符（如 `http://服务名.${env}.ttb.test.ke.com/api/...`）
- **THEN** 调用时自动替换为当前激活的环境标识

---

### Requirement: 敏感配置隔离
真实的数据库地址、账号密码 SHALL 只存储在 `application-local.yml` 中，该文件被 `.gitignore` 忽略，不得提交 git。`application.yml` 只存占位符默认值。

#### Scenario: 敏感配置不进 git
- **WHEN** 开发者修改了 `application-local.yml` 中的数据库连接信息
- **THEN** git status 不显示该文件，不会被意外提交

---

### Requirement: HTTP 接口模板 YAML 格式
新增 HTTP 接口 SHALL 只需在 `src/main/resources/endpoints/` 对应分类的 YAML 文件中追加配置，无需修改 Java 代码。

#### Scenario: 新增 GET 接口后可调用
- **WHEN** 在 endpoints YAML 中添加 GET 接口配置（含 id/urlTemplate/parameters/headers）
- **THEN** `HttpEndpointClient.callPredefinedEndpoint(id, params)` 可直接调用该接口，`${paramName}` 自动替换为实际值

#### Scenario: 新增 POST 接口后可调用
- **WHEN** 在 endpoints YAML 中添加 POST 接口配置（含 requestBodyTemplate）
- **THEN** `HttpEndpointClient` 用实际参数值替换 `requestBodyTemplate` 中的占位符，构建 JSON body 发送请求

#### Scenario: responseFields 过滤响应字段
- **WHEN** 接口配置中设置了 `responseFields`（指定需要保留的字段列表）
- **THEN** 返回值只包含配置中列出的字段，其余字段被过滤

---

### Requirement: HikariCP 连接池参数约束
数据库连接池配置 SHALL 满足以下约束，防止连接超时告警：
- `idle-timeout` < `max-lifetime`
- `max-lifetime` < MySQL `wait_timeout` - 30s
- 建议开启 `keepalive-time` 心跳防止空闲连接被 MySQL 提前关闭

#### Scenario: 连接池参数符合约束
- **WHEN** 配置 `idle-timeout=180000`（3min）、`max-lifetime=300000`（5min）、`keepalive-time=60000`（1min）
- **THEN** 不出现 `Failed to validate connection ... No operations allowed after connection closed` 告警

#### Scenario: max-lifetime 超过 MySQL wait_timeout
- **WHEN** `max-lifetime` ≥ MySQL `wait_timeout`
- **THEN** 连接池持有连接超过 MySQL 主动关闭时间，触发验证失败告警

---

### Requirement: 工具类职责划分
系统 SHALL 按以下职责划分工具类，`@DataQueryTool` 注解只标注在用户直调的最外层工具上。

| 组件 | 位置 | 职责 | @DataQueryTool |
|------|------|------|----------------|
| `OntologyQueryTool.ontologyQuery` | trigger/agent | 本体论统一查询入口 | ✅ 有 |
| `OntologyQueryTool.queryPersonalQuote` | trigger/agent | 个性化报价查询（合并在 OntologyQueryTool 中） | ✅ 有 |
| `HttpEndpointClient` | infrastructure/client | HTTP 接口调用基础设施 | ❌ 无 |

> **注意**：`PersonalQuoteTool` 已在重构时合并进 `OntologyQueryTool`，不再是独立类。

#### Scenario: HttpEndpointClient 无注解不触发直接输出
- **WHEN** Gateway 内部调用 `HttpEndpointClient` 方法
- **THEN** 不触发 DirectOutput，最终由外层 `OntologyQueryTool` 的结果触发

#### Scenario: 旧工具 ContractQueryTool 逐步废弃
- **WHEN** 用户提问触发合同查询场景
- **THEN** LLM 应优先调用 `ontologyQuery`，不应调用旧的 `queryContractsByOrderId` 等方法（集成测试通过 `assertToolNotCalled` 验证）

---

### Requirement: 工具日志规范
工具调用 SHALL 在 AOP 层和工具层各输出一条日志，格式统一。

#### Scenario: AOP 层日志
- **WHEN** 带 `@Tool` 注解的方法被调用
- **THEN** 日志格式：`[TOOL] ontologyQuery(entity=Order, value=xxx)`

#### Scenario: 工具层结果日志
- **WHEN** 工具方法执行完成
- **THEN** 日志格式：`[TOOL] ontologyQuery → 133ms, ok`（SQL 查询显示行数，HTTP 请求显示状态码）
