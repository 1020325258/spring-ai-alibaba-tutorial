## 1. 本体论查询引擎

- [x] 1.1 实现 `OntologyQueryEngine`，提供 `query(entityName, value, queryScope)` 单入口
- [x] 1.2 实现 LookupStrategy 正则匹配，自动决定 Gateway 查询字段
- [x] 1.3 实现基于 YAML 关系图的 BFS 路径查找（`EntityRegistry.findRelationPath`）
- [x] 1.4 实现 CompletableFuture 并行执行同层关系查询
- [x] 1.5 实现 resultKey 自动推导（实体名首字母小写 + 复数 `s`）
- [x] 1.6 实现多目标 queryScope（逗号分隔，`attachMultiPathResults`）
- [x] 1.7 实现 `QueryScope` 枚举类，支持类型安全版本调用

## 2. 实体 Gateway 层

- [x] 2.1 定义 `EntityDataGateway` 接口（queryByField / queryByFieldWithContext / queryWithExtraParams）
- [x] 2.2 实现 `EntityGatewayRegistry` 自注册机制
- [x] 2.3 实现 `ContractGateway`（DB 查询）
- [x] 2.4 实现 `ContractNodeGateway`（DB 查询）
- [x] 2.5 实现 `ContractQuotationRelationGateway`（DB 查询）
- [x] 2.6 实现 `ContractFieldGateway`（分库分表，按 contractCode 取模 10 张表）
- [x] 2.7 实现 `ContractFormGateway`（HTTP 接口，via `platformInstanceId`）
- [x] 2.8 实现 `ContractConfigGateway`（HTTP 接口）
- [x] 2.9 实现 `BudgetBillGateway`（HTTP 接口）
- [x] 2.10 实现 `SubOrderGateway`（HTTP 接口，双参数：billCode + homeOrderNo）
- [x] 2.11 实现 `OrderGateway`（暂无属性，仅作路径起点）
- [x] 2.12 实现 `PersonalQuoteGateway`（HTTP 接口，含 extraParams）

## 3. 直接输出机制

- [x] 3.1 定义 `@DataQueryTool` 注解
- [x] 3.2 实现 `ObservabilityAspect` AOP 切面，捕获注解工具结果并写入 `DirectOutputHolder`
- [x] 3.3 实现 `DirectOutputHolder` 线程安全持有输出结果
- [x] 3.4 在 `OntologyQueryTool.ontologyQuery` 上标记 `@DataQueryTool`
- [x] 3.5 在 `AgentConfiguration` 的流式响应处理中接入 DirectOutput 逻辑

## 4. 基础设施

- [x] 4.1 实现 `HttpEndpointClient`，支持 GET/POST 接口调用和响应字段过滤
- [x] 4.2 实现 `EndpointTemplateService`，从 YAML 加载接口模板
- [x] 4.3 实现 `ToolExecutionTemplate`，统一计时、日志、异常处理
- [x] 4.4 实现 `ToolResult`，提供 success/error/notFound 统一结果格式
- [x] 4.5 实现 `EnvironmentConfig`，支持 `/env` 命令切换测试环境
- [x] 4.6 配置 HikariCP 连接池参数（idle-timeout/max-lifetime/keepalive-time）

## 5. 知识库查询

- [x] 5.1 实现 `KnowledgeLoader`，启动时向量化 `skills/` 目录下的 Markdown 文件
- [x] 5.2 实现 `KnowledgeService`，封装向量检索逻辑
- [x] 5.3 实现 `KnowledgeQueryTool`，作为 LLM 可调用的知识库查询工具

## 6. 集成测试体系

- [x] 6.1 实现 `BaseSREIT`，提供 ask/assertToolCalled/assertToolNotCalled/assertAllToolsSuccess
- [x] 6.2 实现 `ContractOntologyIT`，覆盖合同域全部实体的意图识别场景
- [x] 6.3 实现 `PersonalQuoteToolIT`，覆盖个性化报价查询场景
- [x] 6.4 实现 `IntentRecognitionIT`，覆盖订单号 vs 合同号格式识别
- [x] 6.5 实现 `StartupIT`，验证应用正常启动

## 7. 本体配置与提示词

- [x] 7.1 编写 `domain-ontology.yaml`，定义全部 10 个实体和 9 条关系
- [x] 7.2 编写 `sre-agent.md` 提示词，包含意图识别决策表和 queryScope 参数说明
- [x] 7.3 配置 `endpoints/` 接口模板 YAML（contract、quote 等分类）
