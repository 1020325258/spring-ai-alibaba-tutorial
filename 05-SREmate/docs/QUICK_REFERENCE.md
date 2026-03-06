# SREmate Agent 快速参考指南

## 一、项目快速启动

### 环境检查
```bash
# 检查Java版本（需要17+）
java -version

# 设置API Key
export AI_DASHSCOPE_API_KEY=your_api_key_here
```

### 快速运行
```bash
# 编译
mvn clean package -DskipTests

# 运行
java -jar target/05-SREmate-1.0-SNAPSHOT.jar
```

## 二、核心架构图

```
用户输入
    ↓
SREConsole (命令行交互 + 流式输出)
    ↓
ChatClient (Agent核心 + System Prompt)
    ↓
工具调用 (自动选择)
    ├─ SkillQueryTool (知识库查询)
    ├─ MySQLQueryTool (数据库查询)
    └─ HttpQueryTool (HTTP接口调用)
    ↓
基础设施支持
    ├─ TracingService (调用追踪)
    ├─ CacheService (结果缓存)
    └─ MetricsCollector (性能指标)
    ↓
返回结果给用户
```

## 三、关键代码位置

### Domain模型
```
src/main/java/com/yycome/sremate/domain/
├── TracingContext.java       # 追踪上下文
├── TraceSession.java          # 会话管理
├── ToolMetrics.java           # 性能指标
├── PerformanceReport.java     # 性能报告
├── ToolExecutionRequest.java  # 执行请求
└── AggregatedResult.java      # 聚合结果
```

### 核心服务
```
src/main/java/com/yycome/sremate/service/
├── TracingService.java        # 追踪服务
├── CacheService.java          # 缓存服务
├── MetricsCollector.java      # 指标收集
├── ParallelExecutor.java      # 并行执行
├── ResultAggregator.java      # 结果聚合
└── SkillService.java          # Skills查询
```

### 工具层
```
src/main/java/com/yycome/sremate/tools/
├── SkillQueryTool.java        # 知识库查询
├── MySQLQueryTool.java        # 数据库查询
└── HttpQueryTool.java         # HTTP调用
```

## 四、配置文件说明

### application.yml关键配置
```yaml
spring:
  ai:
    dashscope:
      api-key: ${AI_DASHSCOPE_API_KEY}  # 通义千问API
      chat:
        options:
          model: qwen3-max              # 模型选择

  datasource:
    sre:                                 # 数据库配置
      jdbc-url: ${DB_URL}
      username: ${DB_USERNAME}
      password: ${DB_PASSWORD}
```

## 五、Skills知识库使用

### 添加新的排查经验
1. 创建Markdown文件
2. 放入对应目录：`src/main/resources/skills/`
   - `diagnosis/` - 问题诊断
   - `operations/` - 运维咨询
   - `knowledge/` - 通用知识

### 文档模板
```markdown
# 问题：数据库连接超时

## 问题特征
- 错误信息：Connection timeout
- 常见场景：高并发、连接池配置不当
- 影响范围：应用无法访问数据库

## 排查步骤
### 1. 检查数据库连接数
使用MySQL查询工具：
```sql
SHOW PROCESSLIST;
```

## 解决方案
### 短期解决
1. 重启应用释放连接
2. 临时调大连接池配置

### 长期优化
1. 优化连接池配置
2. 排查连接泄漏
```

## 六、常用命令

### 命令行交互
```
# 开始咨询
你: 数据库连接超时了

# 查看性能统计
你: stats

# 查看追踪链路
你: trace

# 退出
你: quit
```

## 七、扩展开发

### 添加新工具
```java
@Component
public class NewTool {

    @Tool(description = "工具描述")
    public String newMethod(String param) {
        // 实现逻辑
        return "result";
    }
}
```

### 注册工具
```java
@Bean
public ToolCallbackProvider sreTools(NewTool newTool) {
    return MethodToolCallbackProvider.builder()
            .toolObjects(newTool)
            .build();
}
```

## 八、性能优化要点

### 1. 缓存策略
- Skills文档缓存30分钟
- 使用Caffeine高性能缓存
- 自动过期清理

### 2. 并行执行
- 独立工具可并行调用
- CompletableFuture异步
- 线程池大小：10

### 3. 流式输出
- 实时响应提升体验
- 减少首字节延迟

## 九、问题排查

### 编译问题
```bash
# Java版本不对
错误: 类文件具有错误的版本 61.0
解决: 使用Java 17+

# 依赖冲突
mvn dependency:tree
```

### 运行问题
```bash
# API Key未设置
检查: echo $AI_DASHSCOPE_API_KEY

# 数据库连接失败
检查: application.yml中的数据库配置

# 工具调用失败
查看: 日志中的[TOOL_CALL]标记
```

## 十、测试验证

### 运行测试
```bash
mvn test
```

### 手动测试
```bash
# 启动应用
java -jar target/05-SREmate-1.0-SNAPSHOT.jar

# 测试对话
你: 数据库连接超时了，怎么办？

# 查看性能
你: stats

# 查看追踪
你: trace
```

## 十一、关键依赖版本

```xml
<properties>
    <java.version>17</java.version>
    <spring-boot.version>3.x</spring-boot.version>
    <spring-ai-alibaba.version>最新版</spring-ai-alibaba.version>
    <caffeine.version>最新版</caffeine.version>
</properties>
```

## 十二、日志查看

### 关键日志标记
```
[TRACE]    - 追踪日志
[TOOL_CALL] - 工具调用日志
[PARALLEL] - 并行执行日志
[AGGREGATOR] - 结果聚合日志
[CACHE]    - 缓存日志
[METRICS]  - 指标日志
```

### 日志级别配置
```yaml
logging:
  level:
    com.yycome.sremate: DEBUG
    org.springframework.ai: DEBUG
```

## 十三、性能监控

### 查看性能统计
```
你: stats

输出示例:
=== 性能统计报告 ===
querySkills:
  调用次数: 15
  平均耗时: 850ms
  成功率: 93.3%
```

### 查看追踪链路
```
你: trace

输出示例:
用户问题: 数据库连接超时
  ↓
[✓] querySkills (耗时: 150ms)
  ↓
[✓] executeQuery (耗时: 200ms)
```

## 十四、常见错误处理

### 1. API调用失败
- 检查API Key是否正确
- 检查网络连接
- 查看API配额

### 2. 数据库连接失败
- 检查数据库配置
- 检查网络连通性
- 检查数据库用户权限

### 3. Skills查询无结果
- 检查关键词是否准确
- 检查Skills文档位置
- 检查文件格式是否正确

## 十五、快速参考卡片

| 功能 | 实现类 | 关键方法 |
|------|--------|----------|
| 追踪 | TracingService | startToolCall(), endToolCall() |
| 缓存 | CacheService | getOrCompute() |
| 指标 | MetricsCollector | recordToolCall() |
| 并行 | ParallelExecutor | executeParallel() |
| 聚合 | ResultAggregator | aggregate() |

---

**提示**: 详细信息请查看 `PROJECT_EXPERIENCE.md`
