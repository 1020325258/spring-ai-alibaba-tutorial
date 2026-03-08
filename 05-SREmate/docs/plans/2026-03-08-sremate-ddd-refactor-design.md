# SREmate DDD 重构设计方案

## 背景

当前 SREmate 包结构混乱（`domain/` 存放监控模型而非领域模型，`tools/` 混合了工具定义与实现细节），需要按 DDD 思想重新划分，并提取重复代码、删除不需要的工具。

## 目标

1. 按 DDD 重新划分包结构（单模块，不拆 Maven 模块）
2. 工具类只暴露 `@Tool` 方法，实现细节下沉到 service/dao 层
3. 删除 `executeQuery` 工具

---

## 目标包结构

```
com.yycome.sremate/
├── SREmateApplication.java
│
├── types/                          # 公共类型（无外部依赖）
│   └── enums/
│       └── QueryDataType.java
│
├── domain/                         # 领域层（业务逻辑）
│   └── contract/
│       └── service/
│           └── ContractQueryService.java   # 合同聚合逻辑
│
├── infrastructure/                 # 基础设施层（技术实现）
│   ├── config/
│   │   ├── AgentConfiguration.java
│   │   └── DataSourceConfiguration.java
│   ├── dao/
│   │   └── ContractDao.java               # 合同 SQL 访问
│   ├── gateway/
│   │   ├── model/
│   │   │   ├── EndpointParameter.java
│   │   │   └── EndpointTemplate.java
│   │   └── EndpointTemplateService.java
│   └── service/
│       ├── model/
│       │   ├── AggregatedResult.java
│       │   ├── PerformanceReport.java
│       │   ├── ToolExecutionRequest.java
│       │   ├── ToolMetrics.java
│       │   ├── TraceSession.java
│       │   └── TracingContext.java
│       ├── CacheService.java
│       ├── MetricsCollector.java
│       ├── ParallelExecutor.java
│       ├── ResultAggregator.java
│       ├── SkillService.java
│       └── TracingService.java
│
├── trigger/                        # 触发层
│   ├── console/
│   │   └── SREConsole.java
│   └── agent/                      # LLM Function Calling 工具（只含 @Tool 方法）
│       ├── ContractTool.java
│       ├── HttpEndpointTool.java
│       └── SkillQueryTool.java
│
└── aspect/
    └── ObservabilityAspect.java
```

---

## 重构细节

### 职责分离（ContractTool 调用链）

```
ContractTool          → 工具声明（@Tool + 参数校验 + 调服务）
  └── ContractQueryService  → 业务组装（并行调 DAO、组装 Map、序列化 JSON）
        └── ContractDao          → SQL 访问（fetchXxx 方法、分表路由）
```

### ContractTool（trigger/agent/ContractTool.java）

```java
@Tool public String queryContractData(String contractCode, String dataType)
@Tool public String queryContractsByOrderId(String projectOrderId)
@Tool public String queryContractInstanceId(String contractCode)
@Tool public String queryContractFormId(String contractCode)
```

每个方法职责：解析参数 → 调 ContractQueryService → 返回 JSON（异常统一在此处理）

### ContractQueryService（domain/contract/service/）

- `queryByCode(contractCode, dataType)` → 并行调 ContractDao，组装结果
- `queryByOrderId(projectOrderId)` → 查主表 + 并行查关联表，组装列表
- `queryInstanceId(contractCode)` → 单字段查询
- `queryFormId(contractCode)` → 查 instanceId + 调 HTTP 网关

### ContractDao（infrastructure/dao/）

从 MySQLQueryTool 提取的 6 个 fetch 方法 + 工具方法：
- `fetchContractBase(code)`
- `fetchNodes(code)` / `fetchLogs(code)` / `fetchUsers(code)`
- `fetchFields(code)` / `fetchQuotations(code)`
- `findPlatformInstanceId(code)` / `resolveFieldShardingTable(code)`

### 删除内容

- `MySQLQueryTool.java`（整个文件删除，由 ContractTool + ContractQueryService + ContractDao 替代）
- `tools/QueryDataType.java`（迁移到 `types/enums/`）
- `sre-agent.md` 中 `executeQuery` 工具的说明

### 迁移内容

| 原路径 | 新路径 |
|---|---|
| `tools/HttpQueryTool.java` | `trigger/agent/HttpEndpointTool.java` |
| `tools/SkillQueryTool.java` | `trigger/agent/SkillQueryTool.java` |
| `tools/QueryDataType.java` | `types/enums/QueryDataType.java` |
| `config/AgentConfiguration.java` | `infrastructure/config/AgentConfiguration.java` |
| `config/DataSourceConfiguration.java` | `infrastructure/config/DataSourceConfiguration.java` |
| `service/EndpointTemplateService.java` | `infrastructure/gateway/EndpointTemplateService.java` |
| `domain/EndpointParameter.java` | `infrastructure/gateway/model/EndpointParameter.java` |
| `domain/EndpointTemplate.java` | `infrastructure/gateway/model/EndpointTemplate.java` |
| `domain/AggregatedResult.java` 等监控模型 | `infrastructure/service/model/` |
| `service/CacheService.java` 等基础服务 | `infrastructure/service/` |
| `console/SREConsole.java` | `trigger/console/SREConsole.java` |
| `aspect/ObservabilityAspect.java` | `aspect/ObservabilityAspect.java`（保持） |

---

## 测试方案

重构完成后运行已有集成测试验证：
- `ContractDataQueryIT`（4 个测试，覆盖全部 dataType）
- `ContractListQueryIT`（订单查询）
- `ContractFormQueryIT`（版式查询）
