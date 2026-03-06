# SREmate Agent - SRE值班客服智能助手

## 项目概述
SREmate是一个基于Spring AI Alibaba构建的智能SRE值班客服Agent，旨在帮助研发人员快速排查和解决运维问题。

## 核心功能

### 1. 意图理解
- 理解用户问题描述，识别问题类型
- 自动选择合适的工具和排查路径

### 2. 知识库查询
- 查询Skills文档获取排查经验
- 支持关键词匹配和分类查询
- 内置缓存机制提升性能

### 3. 多工具调用
- **SkillQueryTool**: 查询SRE运维知识库
- **MySQLQueryTool**: 执行数据库查询
- **HttpQueryTool**: 调用HTTP接口

### 4. 流式输出
- 支持实时流式响应
- 提升用户体验

## 技术架构

### 分层架构
```
表现层 (SREConsole)
    ↓
Agent层 (ChatClient + System Prompt)
    ↓
编排层 (ParallelExecutor, ResultAggregator)
    ↓
工具层 (SkillQueryTool, MySQLQueryTool, HttpQueryTool)
    ↓
基础设施层 (TracingService, CacheService, MetricsCollector)
```

### 核心组件

#### 1. 基础设施层
- **TracingService**: 工具调用追踪，会话管理
- **CacheService**: 基于Caffeine的高性能缓存
- **MetricsCollector**: 性能指标收集和统计

#### 2. 编排层
- **ParallelExecutor**: 并行执行多个工具调用
- **ResultAggregator**: 结果聚合和关键信息提取

#### 3. 可观测性
- 工具调用追踪
- 性能指标监控
- 排查链路可视化
- 特殊命令支持（stats, trace）

## 环境要求

### 必需
- **Java 17+** (Spring Boot 3.x要求)
- **Maven 3.6+**
- **通义千问API Key**

### 可选
- MySQL数据库（用于数据库查询工具）
- HTTP监控接口（用于HTTP查询工具）

## 快速开始

### 1. 配置API Key
```bash
export AI_DASHSCOPE_API_KEY=your_api_key_here
```

### 2. 编译项目
```bash
mvn clean package
```

### 3. 运行
```bash
java -jar target/05-SREmate-1.0-SNAPSHOT.jar
```

## 使用指南

### 命令行交互
```
╔════════════════════════════════════════════╗
║      SRE值班客服Agent v2.0                  ║
║      支持流式输出、追踪、缓存               ║
╚════════════════════════════════════════════╝

输入问题开始咨询，输入 'quit' 或 'exit' 退出
特殊命令:
  stats - 查看性能统计
  trace - 查看当前会话追踪链路
```

### 示例对话

**用户：** 数据库连接超时了，怎么办？

**助手：**
我来帮你排查数据库连接超时的问题。首先查询相关的排查经验。

[调用querySkills工具，查询类型：diagnosis，关键词：数据库 连接 超时]

根据排查经验，我需要检查数据库连接数。让我执行查询：

[调用executeQuery工具，SQL: SHOW PROCESSLIST]

分析查询结果，我发现当前连接数已经达到上限。建议：

### 短期解决
1. 重启应用释放空闲连接
2. 临时调大连接池配置

### 长期优化
1. 优化连接池配置
2. 排查是否有连接泄漏
3. 优化慢查询

## Skills知识库

### 目录结构
```
src/main/resources/skills/
├── diagnosis/          # 问题诊断
│   ├── database-connection.md
│   └── service-timeout.md
├── operations/         # 运维咨询
└── knowledge/          # 通用知识
```

### 添加新的排查经验
在相应目录下创建Markdown文件，包含：
- 问题特征
- 排查步骤
- 工具调用建议
- 解决方案
- 典型案例

## 性能优化

### 1. 缓存机制
- Skills文档缓存（30分钟TTL）
- Caffeine高性能缓存
- 自动过期清理

### 2. 并行执行
- 支持多个工具并行调用
- CompletableFuture异步执行
- 线程池管理

### 3. 流式输出
- 实时响应提升用户体验
- 减少首字节延迟

## 可观测性

### 1. 工具调用追踪
```
[TRACE] 开始工具调用: tool=querySkills, traceId=xxx
[TRACE] 工具调用成功: tool=querySkills, duration=150ms
```

### 2. 性能统计
```
=== 性能统计报告 ===
querySkills:
  调用次数: 15
  平均耗时: 850ms
  成功率: 93.3%
```

### 3. 排查链路可视化
使用`trace`命令查看当前会话的完整排查链路。

## 扩展开发

### 添加新工具
1. 创建工具类并添加`@Component`注解
2. 添加`@Tool`注解的方法
3. 在`AgentConfiguration`中注册

### 自定义编排策略
实现`ParallelExecutor`的扩展接口。

## 故障排查

### 常见问题

**Q: 编译失败，提示Java版本错误**
A: 确保使用Java 17或更高版本

**Q: API调用失败**
A: 检查环境变量AI_DASHSCOPE_API_KEY是否正确设置

**Q: 数据库连接失败**
A: 检查application.yml中的数据库配置

## 技术栈

- **语言**: Java 17+
- **框架**: Spring Boot 3.x, Spring AI Alibaba
- **AI模型**: 通义千问 (qwen3-max)
- **缓存**: Caffeine
- **监控**: Micrometer, Spring Actuator
- **数据库**: MySQL
- **日志**: SLF4J, Logback

## 项目结构
```
05-SREmate/
├── src/main/java/com/yycome/sremate/
│   ├── domain/              # 领域模型
│   │   ├── TracingContext.java
│   │   ├── TraceSession.java
│   │   ├── ToolMetrics.java
│   │   └── ...
│   ├── service/             # 业务服务
│   │   ├── TracingService.java
│   │   ├── CacheService.java
│   │   ├── MetricsCollector.java
│   │   ├── ParallelExecutor.java
│   │   ├── ResultAggregator.java
│   │   └── SkillService.java
│   ├── tools/               # 工具层
│   │   ├── SkillQueryTool.java
│   │   ├── MySQLQueryTool.java
│   │   └── HttpQueryTool.java
│   ├── aspect/              # 切面
│   │   └── ObservabilityAspect.java
│   ├── config/              # 配置
│   │   ├── AgentConfiguration.java
│   │   └── DataSourceConfiguration.java
│   └── console/             # 控制台
│       └── SREConsole.java
├── src/main/resources/
│   ├── prompts/
│   │   └── sre-agent.md     # Agent提示词
│   ├── skills/              # 知识库
│   │   ├── diagnosis/
│   │   ├── operations/
│   │   └── knowledge/
│   └── application.yml      # 配置文件
└── pom.xml
```

## 许可证
MIT License

## 联系方式
如有问题，请提交Issue或Pull Request。
