# 故障排查

## 数据库连接超时怎么排查？

### 问题现象
应用日志中出现数据库连接超时错误，如：
- Connection timed out
- Communications link failure
- Could not get JDBC Connection

### 排查步骤
1. 检查数据库服务是否正常运行
2. 检查网络连通性：ping/telnet 数据库地址
3. 检查连接池配置是否合理
4. 检查是否有慢查询占用连接
5. 检查数据库最大连接数是否达到上限

### 解决方案
1. 调整连接池配置：maximum-pool-size、minimum-idle
2. 优化慢查询，减少连接占用时间
3. 增加数据库最大连接数
4. 检查并修复连接泄漏问题

### 相关命令
```bash
# 查看数据库当前连接数
SHOW STATUS LIKE 'Threads_connected';
# 查看最大连接数
SHOW VARIABLES LIKE 'max_connections';
# 查看当前连接详情
SHOW PROCESSLIST;
```

---

## Redis连接超时怎么排查？

### 问题现象
应用日志中出现 Redis 连接超时错误，如：
- Redis connection timeout
- Unable to connect to Redis
- Connection refused

### 排查步骤
1. 检查 Redis 服务是否正常运行
2. 检查网络连通性
3. 检查 Redis 配置：timeout、maxclients
4. 检查是否有慢命令阻塞

### 解决方案
1. 增加 Redis 客户端超时配置
2. 优化慢命令
3. 检查 Redis 内存是否满

### 相关命令
```bash
# 检查 Redis 状态
redis-cli ping
# 查看当前连接数
redis-cli INFO clients
# 查看慢日志
redis-cli SLOWLOG GET 10
```
