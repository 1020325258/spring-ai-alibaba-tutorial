# SREmate Agent 项目经验总结

## 项目背景

### 目标
构建一个智能SRE值班客服Agent，帮助研发人员快速排查和解决应用层问题，提升问题排查效率。

### 核心需求
1. **意图理解**: 理解用户问题描述，识别问题类型和排查方向
2. **知识库查询**: 查询Skills文档，获取相关的排查经验
3. **多工具调用**: 调用多种工具（数据库、HTTP接口）获取诊断数据
4. **智能分析**: 基于获取的数据，提供清晰的排查建议

### 非功能性需求
- 响应速度足够快
- 流程可观测
- 所有数据通过接口获取

## 技术架构

### 分层设计

```
表现层 (SREConsole)
    ↓ 命令行交互、流式输出
Agent层 (ChatClient + System Prompt)
    ↓ 意图理解、工具编排
编排层 (ParallelExecutor, ResultAggregator)
    ↓ 并行执行、结果聚合
工具层 (SkillQueryTool, MySQLQueryTool, HttpQueryTool)
    ↓ 数据获取
基础设施层 (TracingService, CacheService, MetricsCollector)
    ↓ 追踪、缓存、指标
```

### 核心组件

#### 1. Domain模型层
位置: `src/main/java/com/yycome/sremate/domain/`

关键类:
- **TracingContext**: 追踪上下文，记录单次工具调用信息
- **TraceSession**: 追踪会话，记录完整排查会话
- **ToolMetrics**: 工具性能指标，统计调用次数、耗时、成功率
- **PerformanceReport**: 性能报告，汇总所有工具指标
- **ToolExecutionRequest**: 工具执行请求
- **AggregatedResult**: 聚合结果

#### 2. 基础设施层
位置: `src/main/java/com/yycome/sremate/service/`

##### TracingService
```java
// 功能
- 会话管理 (startSession, endSession)
- 工具调用追踪 (startToolCall, endToolCall, failToolCall)
- 追踪链路可视化 (visualizeTraceChain)

// 使用场景
- 记录每个工具调用的详细信息
- 提供排查链路的完整视图
- 支持问题回溯和分析
```

##### CacheService
```java
// 功能
- 基于Caffeine的高性能缓存
- getOrCompute: 获取或计算缓存值
- 支持TTL配置
- 提供缓存统计

// 配置
- 最大容量: 1000
- 默认过期时间: 10分钟
- Skills文档缓存: 30分钟

// 使用场景
- Skills文档查询结果缓存
- 频繁查询的数据缓存
- 提升响应速度
```

##### MetricsCollector
```java
// 功能
- 收集工具调用性能指标
- 集成Micrometer
- 生成性能报告

// 指标类型
- 调用次数
- 成功/失败次数
- 平均耗时
- 最大/最小耗时
- 成功率
```

#### 3. 编排层

##### ParallelExecutor
```java
// 功能
- 并行执行多个工具调用
- 使用CompletableFuture实现异步
- 线程池管理 (固定10个线程)

// 关键代码
List<CompletableFuture<Object>> futures = requests.stream()
    .map(request -> CompletableFuture.supplyAsync(() ->
        executeSingleTool(request), executorService
    ))
    .collect(Collectors.toList());
```

##### ResultAggregator
```java
// 功能
- 聚合多个工具的执行结果
- 提取关键信息
- 区分成功/失败结果

// 信息提取
- 错误信息识别 (error, exception, fail, timeout)
- 连接相关问题识别
- 数字指标提取
```

#### 4. 工具层

##### SkillQueryTool
```java
// 功能
- 查询SRE运维知识库
- 支持按类型过滤 (diagnosis/operations/knowledge)
- 关键词匹配
- 集成缓存

// 使用示例
querySkills("diagnosis", "数据库 连接 超时")
```

##### MySQLQueryTool
```java
// 功能
- 执行MySQL查询
- 仅支持SELECT和SHOW语句
- 安全检查防止修改操作
- 结果格式化输出

// 使用示例
executeQuery("SHOW PROCESSLIST", "查看当前数据库连接数")
```

##### HttpQueryTool
```java
// 功能
- 调用HTTP接口
- 支持GET/POST方法
- 超时控制 (30秒)

// 使用示例
callHttpEndpoint("http://localhost:8080/actuator/health", "GET", null)
```

#### 5. 表现层

##### SREConsole
```java
// 功能
- 命令行交互界面
- 流式输出支持
- 特殊命令: stats (性能统计), trace (追踪链路)
- 会话管理

// 特性
- 彩色输出 (JAnsi)
- 历史命令支持 (JLine)
- Ctrl+C/D处理
```

### 关键实现细节

#### 1. 可观测性切面
```java
@Around("@annotation(org.springframework.ai.tool.annotation.Tool)")
public Object logToolCall(ProceedingJoinPoint joinPoint) {
    // 1. 开始追踪
    TracingContext tracing = tracingService.startToolCall(toolName, params);

    // 2. 执行工具
    Object result = joinPoint.proceed();

    // 3. 结束追踪
    tracingService.endToolCall(tracing, result);

    // 4. 记录指标
    metricsCollector.recordToolCall(toolName, duration, success);
}
```

#### 2. 流式输出
```java
sreAgent.prompt()
    .messages(conversationHistory)
    .stream()
    .content()
    .doOnNext(chunk -> {
        System.out.print(chunk);  // 实时输出
        responseBuilder.append(chunk);
    })
    .blockLast();
```

#### 3. 缓存集成
```java
public String querySkills(String queryType, String keywords) {
    String cacheKey = buildCacheKey(queryType, keywords);

    return cacheService.getOrCompute(cacheKey, () -> {
        return doQuerySkills(queryType, keywords);
    }, Duration.ofMinutes(30));
}
```

## 遇到的问题和解决方案

### 1. Java版本兼容性
**问题**: Spring AI和Spring Boot 3.x需要Java 17+
```
错误: 类文件具有错误的版本 61.0, 应为 52.0
```

**解决方案**:
- 项目要求Java 17+运行环境
- 使用Java 9作为编译目标（部分兼容）
- 在README中明确说明环境要求

### 2. 工具调用集成
**问题**: ParallelExecutor直接依赖Spring AI的ToolCallback导致版本冲突

**解决方案**:
- 简化ParallelExecutor实现
- 实际工具调用由Spring AI框架处理
- ParallelExecutor专注于并行编排和追踪

### 3. 流式输出实现
**问题**: 如何实现实时响应提升用户体验

**解决方案**:
- 使用Spring AI的stream()方法
- doOnNext实时输出每个chunk
- StringBuilder收集完整响应用于历史记录

## Skills知识库结构

### 目录组织
```
src/main/resources/skills/
├── diagnosis/          # 问题诊断
│   ├── database-connection.md
│   └── service-timeout.md
├── operations/         # 运维咨询
└── knowledge/          # 通用知识
```

### 文档模板
```markdown
# 问题：[问题名称]

## 问题特征
- 错误信息
- 常见场景
- 影响范围

## 排查步骤
1. 步骤1
2. 步骤2

## 工具调用建议
```
tool: executeQuery
sql: "SHOW PROCESSLIST"
description: "查看当前数据库连接数"
```

## 解决方案
### 短期解决
### 长期优化

## 典型案例
```

## 配置说明

### application.yml关键配置
```yaml
spring:
  ai:
    dashscope:
      api-key: ${AI_DASHSCOPE_API_KEY}
      chat:
        options:
          model: qwen3-max

  datasource:
    sre:
      jdbc-url: ${DB_URL:jdbc:mysql://localhost:3306/sre_db}
      username: ${DB_USERNAME:root}
      password: ${DB_PASSWORD:root}

logging:
  level:
    com.yycome.sremate: DEBUG
    org.springframework.ai: DEBUG

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

### 依赖管理
```xml
<!-- Spring AI Alibaba -->
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-starter-dashscope</artifactId>
</dependency>

<!-- Caffeine Cache -->
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>

<!-- Micrometer for Metrics -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-core</artifactId>
</dependency>
```

## 性能优化建议

### 1. 响应速度
- 使用流式输出减少首字节延迟
- Skills文档缓存30分钟
- 并行执行独立的工具调用
- 使用异步非阻塞IO (WebClient)

### 2. 可观测性
- 所有的工具调用自动追踪
- 性能指标实时收集
- 提供stats和trace命令查看
- 日志级别合理配置

### 3. 扩展性
- 新增工具只需实现@Tool注解方法
- Skills知识库动态加载
- 编排策略可扩展

## 测试策略

### 单元测试
```java
@Test
void testTracingService() {
    TraceSession session = tracingService.startSession("user1", "test query");
    assertNotNull(session);
    assertEquals("user1", session.getUserId());
}

@Test
void testCacheService() {
    String value = cacheService.getOrCompute("key", () -> "computed");
    assertEquals("computed", value);
}

@Test
void testMetricsCollector() {
    metricsCollector.recordToolCall("tool1", 100, true);
    PerformanceReport report = metricsCollector.getReport();
    assertTrue(report.getToolMetrics().containsKey("tool1"));
}
```

## 部署和运行

### 环境准备
```bash
# 1. 设置API Key
export AI_DASHSCOPE_API_KEY=your_api_key_here

# 2. 配置数据库（可选）
export DB_URL=jdbc:mysql://localhost:3306/sre_db
export DB_USERNAME=root
export DB_PASSWORD=root
```

### 构建和运行
```bash
# 编译
mvn clean package

# 运行
java -jar target/05-SREmate-1.0-SNAPSHOT.jar
```

### Docker部署（建议）
```dockerfile
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY target/05-SREmate-1.0-SNAPSHOT.jar app.jar
ENV AI_DASHSCOPE_API_KEY=""
ENTRYPOINT ["java", "-jar", "app.jar"]
```

## 最佳实践

### 1. Skills文档编写
- 描述清晰的问题特征
- 提供具体的排查步骤
- 包含工具调用示例
- 给出多种解决方案
- 补充典型案例

### 2. 工具调用
- 优先使用Skills查询获取经验
- 并行调用独立的工具
- 注意工具调用的安全性
- 合理设置超时时间

### 3. 性能监控
- 定期查看性能统计
- 关注缓存命中率
- 监控平均响应时间
- 分析失败原因

### 4. 问题排查
- 使用trace命令查看排查链路
- 检查工具调用日志
- 验证Skills文档准确性
- 分析性能瓶颈

## 后续优化方向

### 短期优化
1. 添加更多Skills文档
2. 实现ELK日志查询工具
3. 增加监控指标对接
4. 优化错误处理和重试机制

### 中期优化
1. 实现Web界面
2. 支持多用户会话管理
3. 添加权限控制
4. 集成更多数据源

### 长期优化
1. 机器学习优化排查路径
2. 自动生成Skills文档
3. 知识图谱构建
4. 智能推荐解决方案

## 常见问题FAQ

### Q1: 如何添加新的排查场景？
**A**: 在`src/main/resources/skills/`相应目录下添加Markdown文档。

### Q2: 如何添加新的数据查询工具？
**A**:
1. 创建工具类并添加`@Component`
2. 添加`@Tool`注解的方法
3. 在`AgentConfiguration`中注册

### Q3: 如何查看性能瓶颈？
**A**: 使用`stats`命令查看各工具的平均耗时和成功率。

### Q4: 如何调试工具调用？
**A**:
1. 查看日志中的`[TOOL_CALL]`标记
2. 使用`trace`命令查看调用链路
3. 开启DEBUG日志级别

### Q5: 缓存不生效怎么办？
**A**:
1. 检查CacheService是否正确注入
2. 确认缓存键的构建逻辑
3. 查看缓存统计信息

## 相关文档链接

- [Spring AI文档](https://docs.spring.io/spring-ai/reference/)
- [Spring AI Alibaba文档](https://github.com/alibaba/spring-ai-alibaba)
- [Caffeine缓存文档](https://github.com/ben-manes/caffeine)
- [Micrometer文档](https://micrometer.io/docs)

## 项目维护建议

### 代码规范
- 遵循阿里巴巴Java开发规范
- 使用Lombok简化代码
- 合理使用设计模式
- 保持代码可测试性

### 文档维护
- 及时更新README
- 补充新的Skills文档
- 记录遇到的问题和解决方案
- 保持本文档的时效性

### 版本管理
- 使用语义化版本号
- 编写清晰的提交信息
- 重要功能创建feature分支
- 定期发布release版本

---

**最后更新**: 2026-03-06
**维护者**: SREmate开发团队
