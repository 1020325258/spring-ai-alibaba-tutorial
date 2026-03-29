---
name: service-timeout
description: 排查服务调用超时、响应慢等问题
---

# 问题：服务超时

## 问题特征
- 错误信息：Timeout、Read timed out、SocketTimeoutException
- 常见场景：下游服务响应慢、网络延迟高、服务负载高
- 影响范围：用户请求失败，业务流程中断

## 排查步骤

### 1. 检查服务健康状态
调用服务健康检查接口：
```
GET /actuator/health
```

### 2. 检查服务指标
查看关键性能指标：
- 响应时间（p95、p99）
- 错误率
- QPS

### 3. 检查日志
查看服务日志中的异常和慢请求记录。

### 4. 检查资源使用
- CPU使用率
- 内存使用率
- GC情况
- 线程池状态

## 工具调用建议

使用HttpQueryTool调用监控接口：

**查询服务健康状态：**
```
tool: callHttpEndpoint
url: "http://service-host:port/actuator/health"
method: "GET"
description: "检查服务健康状态"
```

**查询服务指标：**
```
tool: callHttpEndpoint
url: "http://service-host:port/actuator/metrics"
method: "GET"
description: "获取服务性能指标"
```

## 解决方案

### 短期解决
1. **扩容实例**：增加服务实例数
2. **调整超时时间**：临时增加客户端超时时间
3. **限流降级**：对非核心功能进行降级

### 长期优化
1. **性能优化**：
   - 优化慢接口
   - 增加缓存
   - 异步化处理

2. **架构优化**：
   - 服务拆分
   - 引入消息队列
   - 实施熔断机制

3. **监控告警**：
   - 响应时间告警
   - 错误率告警
   - 自动扩容策略

## 相关指标
- response_time：响应时间
- error_rate：错误率
- throughput：吞吐量
- cpu_usage：CPU使用率
