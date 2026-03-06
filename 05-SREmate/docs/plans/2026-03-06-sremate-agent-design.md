# SREmate Agent 设计文档

## 1. 概述

### 1.1 项目目标
构建一个智能SRE值班客服Agent，帮助研发人员快速排查和解决应用层问题，提升问题排查效率。

### 1.2 核心功能
1. **意图理解**：理解用户问题描述，识别问题类型和排查方向
2. **知识库查询**：查询Skills文档，获取相关的排查经验和解决方案
3. **多工具调用**：调用多种工具（数据库、HTTP接口、日志）获取诊断数据
4. **智能分析**：基于获取的数据，提供清晰的排查建议和解决方案

### 1.3 非功能性需求
- **响应速度**：支持并行工具调用、流式输出、结果缓存
- **流程可观测**：工具调用追踪、性能指标监控、排查链路可视化
- **数据来源**：所有数据通过接口获取，不依赖本地文件

## 2. 架构设计

### 2.1 整体架构

采用**模块化分层架构**，分为五个核心层：

```
┌─────────────────────────────────────────────────┐
│           表现层 (Presentation Layer)            │
│  - SREConsole (命令行交互)                       │
│  - SREController (可选的REST API)               │
└─────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────┐
│           Agent层 (Agent Layer)                  │
│  - SREAgent (主Agent，理解意图和编排工具)         │
│  - System Prompt (sre-agent.md)                 │
└─────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────┐
│           编排层 (Orchestration Layer)           │
│  - ToolOrchestrator (工具编排器)                 │
│  - ParallelExecutor (并行执行器)                 │
│  - ResultAggregator (结果聚合器)                 │
└─────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────┐
│           工具层 (Tool Layer)                    │
│  - SkillQueryTool (知识库查询)                   │
│  - MySQLQueryTool (数据库查询)                   │
│  - HttpQueryTool (HTTP接口调用)                  │
│  - ElkLogQueryTool (ELK日志查询) [新增]          │
└─────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────┐
│           基础设施层 (Infrastructure Layer)      │
│  - TracingService (调用追踪)                     │
│  - CacheService (结果缓存)                       │
│  - MetricsCollector (性能指标收集)               │
└─────────────────────────────────────────────────┘
```

### 2.2 核心设计原则
- **单一职责**：每层只负责自己的核心功能
- **依赖倒置**：上层依赖下层的抽象接口
- **开闭原则**：新增工具只需实现统一接口，无需修改现有代码

## 3. 详细设计

### 3.1 工具层设计

#### 3.1.1 统一工具接口

所有工具实现统一的 `SRETool` 接口：

```java
public interface SRETool {
    /**
     * 获取工具元数据
     */
    ToolMetadata getMetadata();

    /**
     * 执行工具调用
     */
    ToolResult execute(ToolRequest request, TracingContext tracing);

    /**
     * 是否支持并行调用
     */
    default boolean supportsParallel() {
        return true;
    }
}
```

#### 3.1.2 工具元数据

```java
@Data
public class ToolMetadata {
    private String name;           // 工具名称
    private String description;    // 工具描述
    private List<ParameterSpec> parameters;  // 参数说明
    private String category;       // 工具分类
}
```

#### 3.1.3 新增工具：ELK日志查询工具

```java
@Slf4j
@Component
public class ElkLogQueryTool implements SRETool {

    private final RestClient elasticsearchClient;

    @Override
    public ToolMetadata getMetadata() {
        return ToolMetadata.builder()
            .name("queryLogs")
            .description("查询ELK日志，用于排查应用日志相关问题")
            .parameters(Arrays.asList(
                new ParameterSpec("serviceName", "服务名称", true),
                new ParameterSpec("level", "日志级别（INFO/WARN/ERROR）", false),
                new ParameterSpec("keywords", "关键词，多个用空格分隔", false),
                new ParameterSpec("startTime", "开始时间（格式：yyyy-MM-dd HH:mm:ss）", false),
                new ParameterSpec("endTime", "结束时间（格式：yyyy-MM-dd HH:mm:ss）", false),
                new ParameterSpec("limit", "返回条数限制，默认100", false)
            ))
            .category("log")
            .build();
    }

    @Override
    public ToolResult execute(ToolRequest request, TracingContext tracing) {
        // 实现日志查询逻辑
    }
}
```

### 3.2 编排层设计

#### 3.2.1 工具编排器

```java
@Service
@Slf4j
public class ToolOrchestrator {

    private final Map<String, SRETool> toolRegistry;
    private final ParallelExecutor parallelExecutor;
    private final TracingService tracingService;

    /**
     * 执行单个工具调用
     */
    public ToolResult executeTool(String toolName, Map<String, Object> params) {
        SRETool tool = toolRegistry.get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("Tool not found: " + toolName);
        }

        TracingContext tracing = tracingService.startToolCall(toolName, params);
        try {
            ToolResult result = tool.execute(new ToolRequest(params), tracing);
            tracingService.endToolCall(tracing, result);
            return result;
        } catch (Exception e) {
            tracingService.failToolCall(tracing, e);
            throw e;
        }
    }

    /**
     * 并行执行多个工具调用
     */
    public List<ToolResult> executeParallel(List<ToolExecutionRequest> requests) {
        return parallelExecutor.executeParallel(requests);
    }
}
```

#### 3.2.2 并行执行器

```java
@Service
public class ParallelExecutor {

    private final ToolOrchestrator orchestrator;

    /**
     * 并行执行多个工具调用
     */
    public List<ToolResult> executeParallel(List<ToolExecutionRequest> requests) {
        List<CompletableFuture<ToolResult>> futures = requests.stream()
            .map(req -> CompletableFuture.supplyAsync(() ->
                orchestrator.executeTool(req.getToolName(), req.getParams())
            ))
            .toList();

        return futures.stream()
            .map(CompletableFuture::join)
            .toList();
    }
}
```

#### 3.2.3 结果聚合器

```java
@Service
public class ResultAggregator {

    /**
     * 聚合多个工具结果
     */
    public AggregatedResult aggregate(List<ToolResult> results) {
        AggregatedResult aggregated = new AggregatedResult();

        for (ToolResult result : results) {
            if (result.isSuccess()) {
                aggregated.addSuccessResult(result);
            } else {
                aggregated.addFailedResult(result);
            }
        }

        // 提取关键信息
        aggregated.setKeyInsights(extractKeyInsights(results));

        return aggregated;
    }

    /**
     * 提取关键信息
     */
    private List<String> extractKeyInsights(List<ToolResult> results) {
        // 分析结果，提取异常、错误、关键指标等
    }
}
```

### 3.3 基础设施层设计

#### 3.3.1 追踪服务

```java
@Service
@Slf4j
public class TracingService {

    private final Map<String, TraceSession> sessions = new ConcurrentHashMap<>();

    /**
     * 开始追踪会话
     */
    public TraceSession startSession(String userId, String query) {
        String sessionId = UUID.randomUUID().toString();
        TraceSession session = new TraceSession(sessionId, userId, query);
        sessions.put(sessionId, session);
        return session;
    }

    /**
     * 开始工具调用追踪
     */
    public TracingContext startToolCall(String toolName, Map<String, Object> params) {
        TracingContext context = new TracingContext();
        context.setToolName(toolName);
        context.setParams(params);
        context.setStartTime(System.currentTimeMillis());
        return context;
    }

    /**
     * 结束工具调用追踪
     */
    public void endToolCall(TracingContext context, ToolResult result) {
        context.setEndTime(System.currentTimeMillis());
        context.setSuccess(result.isSuccess());
        context.setResult(result);

        // 记录到当前会话
        TraceSession session = sessions.get(context.getSessionId());
        if (session != null) {
            session.addToolCall(context);
        }
    }

    /**
     * 获取追踪链路
     */
    public TraceChain getTraceChain(String sessionId) {
        TraceSession session = sessions.get(sessionId);
        if (session == null) {
            return null;
        }
        return TraceChain.from(session);
    }
}
```

#### 3.3.2 缓存服务

```java
@Service
public class CacheService {

    private final Cache<String, CacheEntry> cache = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(Duration.ofMinutes(10))
        .recordStats()
        .build();

    /**
     * 获取或计算缓存值
     */
    public <T> T getOrCompute(String key, Supplier<T> supplier, Duration ttl) {
        CacheEntry entry = cache.get(key, k -> {
            T value = supplier.get();
            return new CacheEntry(value, ttl);
        });
        return (T) entry.getValue();
    }

    /**
     * 获取缓存统计信息
     */
    public CacheStats getStats() {
        return cache.stats();
    }
}
```

#### 3.3.3 性能指标收集器

```java
@Service
public class MetricsCollector {

    private final MeterRegistry meterRegistry;
    private final Map<String, ToolMetrics> toolMetricsMap = new ConcurrentHashMap<>();

    /**
     * 记录工具调用指标
     */
    public void recordToolCall(String toolName, long duration, boolean success) {
        ToolMetrics metrics = toolMetricsMap.computeIfAbsent(
            toolName,
            k -> new ToolMetrics(toolName)
        );

        metrics.recordCall(duration, success);

        // 记录到Micrometer
        Timer.builder("sre.tool.duration")
            .tag("tool", toolName)
            .tag("success", String.valueOf(success))
            .register(meterRegistry)
            .record(Duration.ofMillis(duration));
    }

    /**
     * 获取性能报告
     */
    public PerformanceReport getReport() {
        PerformanceReport report = new PerformanceReport();
        toolMetricsMap.forEach((name, metrics) -> {
            report.addToolMetrics(name, metrics);
        });
        return report;
    }
}
```

### 3.4 性能优化策略

#### 3.4.1 并行调用

```java
// Agent可以一次性请求多个工具并行执行
List<ToolExecutionRequest> requests = Arrays.asList(
    new ToolExecutionRequest("executeQuery", Map.of("sql", "SHOW PROCESSLIST")),
    new ToolExecutionRequest("queryLogs", Map.of("serviceName", "user-service", "level", "ERROR"))
);

List<ToolResult> results = orchestrator.executeParallel(requests);
```

#### 3.4.2 流式输出

在SREConsole中支持流式输出：

```java
Flux<String> responseStream = sreAgent.prompt()
    .messages(conversationHistory)
    .stream()
    .content();

responseStream.subscribe(chunk -> {
    System.out.print(chunk);
});
```

#### 3.4.3 智能缓存

对于常用数据使用缓存：

```java
@Cacheable(value = "skill-docs", key = "#queryType + '-' + #keywords")
public String querySkills(String queryType, String keywords) {
    // 查询逻辑
}
```

### 3.5 可观测性实现

#### 3.5.1 工具调用追踪

在控制台实时显示工具调用状态：

```
[10:30:15] 正在调用: executeQuery
[10:30:16] ✓ executeQuery 完成 (耗时: 1.2s)
[10:30:16] 正在调用: queryLogs
[10:30:18] ✓ queryLogs 完成 (耗时: 2.1s)
```

#### 3.5.2 性能指标监控

提供性能统计报告：

```
=== 性能统计 ===
executeQuery:
  调用次数: 15
  平均耗时: 850ms
  成功率: 93.3%

queryLogs:
  调用次数: 8
  平均耗时: 1.2s
  成功率: 100%
```

#### 3.5.3 排查链路可视化

生成排查路径图：

```
用户问题: 数据库连接超时
  ↓
[Skills查询] 获取排查经验
  ↓
[并行执行]
  ├─ [数据库查询] SHOW PROCESSLIST
  └─ [日志查询] 查询ERROR日志
  ↓
[结果分析]
  - 连接数: 150/200
  - 错误日志: "Connection timeout"
  ↓
[建议方案]
  1. 重启应用释放连接
  2. 优化连接池配置
```

## 4. 实施计划

### 4.1 阶段一：基础设施层
1. 实现TracingService
2. 实现CacheService
3. 实现MetricsCollector
4. 编写单元测试

### 4.2 阶段二：工具层增强
1. 定义SRETool接口
2. 重构现有工具实现接口
3. 实现ElkLogQueryTool
4. 编写集成测试

### 4.3 阶段三：编排层
1. 实现ToolOrchestrator
2. 实现ParallelExecutor
3. 实现ResultAggregator
4. 编写集成测试

### 4.4 阶段四：性能优化
1. 实现并行调用
2. 实现流式输出
3. 实现缓存机制
4. 性能测试和调优

### 4.5 阶段五：可观测性
1. 实现工具调用追踪
2. 实现性能指标监控
3. 实现排查链路可视化
4. 编写使用文档

## 5. 技术栈

- **语言**: Java 21
- **框架**: Spring Boot 3.x, Spring AI Alibaba
- **缓存**: Caffeine
- **监控**: Micrometer, Spring Actuator
- **数据库**: MySQL (已有), Elasticsearch (日志查询)
- **日志**: SLF4J, Logback

## 6. 扩展性设计

### 6.1 新增工具

只需实现SRETool接口并注册为Spring Bean：

```java
@Component
public class NewTool implements SRETool {
    // 实现接口方法
}
```

### 6.2 自定义编排策略

可以实现不同的编排策略：

```java
public interface OrchestrationStrategy {
    List<ToolResult> execute(List<ToolExecutionRequest> requests);
}
```

### 6.3 插件化追踪

可以实现自定义的追踪器：

```java
public interface Tracer {
    void onStart(TracingContext context);
    void onEnd(TracingContext context);
    void onError(TracingContext context, Exception e);
}
```

## 7. 风险和挑战

### 7.1 技术风险
- **LLM推理能力**: Agent的推理能力依赖LLM，可能存在理解偏差
- **工具调用可靠性**: 外部接口调用可能失败，需要完善的错误处理
- **性能瓶颈**: 并行调用可能带来资源竞争

### 7.2 解决方案
- **Prompt优化**: 持续优化System Prompt，提升理解准确度
- **重试机制**: 对工具调用实现自动重试和降级方案
- **资源限制**: 对并行调用数量进行限制，避免资源耗尽

## 8. 成功指标

### 8.1 功能指标
- 支持至少4种工具调用（现有3种 + ELK日志）
- 支持并行调用至少3个工具
- 排查准确率 > 80%

### 8.2 性能指标
- 平均响应时间 < 5秒（单个工具调用）
- 并行调用时间节省 > 40%
- 缓存命中率 > 30%

### 8.3 可观测性指标
- 工具调用追踪覆盖率 100%
- 性能指标收集覆盖率 100%
- 排查链路可视化覆盖率 100%
