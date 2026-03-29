---
name: database-connection
description: 排查数据库连接超时、连接池耗尽等问题
---

# 问题：数据库连接超时

## 问题特征
- 错误信息：Connection timeout、Communications link failure
- 常见场景：高并发、网络抖动、连接池配置不当、数据库负载过高
- 影响范围：应用无法访问数据库，业务功能中断

## 排查步骤

### 1. 检查数据库连接数
使用MySQL查询工具查看当前连接数：
```sql
SHOW PROCESSLIST;
SHOW STATUS LIKE 'Threads_connected';
SHOW STATUS LIKE 'Max_used_connections';
```

关键指标：
- Threads_connected：当前活跃连接数
- Max_used_connections：历史最大连接数
- max_connections：最大允许连接数（通过 SHOW VARIABLES LIKE 'max_connections' 查看）

### 2. 检查连接池配置
查看应用连接池配置是否合理：
- maximum-pool-size：最大连接数
- minimum-idle：最小空闲连接数
- connection-timeout：连接超时时间
- idle-timeout：空闲超时时间

### 3. 检查网络连通性
测试应用与数据库之间的网络：
- ping测试
- telnet测试数据库端口
- 检查防火墙规则

### 4. 检查数据库负载
查看数据库负载情况：
```sql
SHOW ENGINE INNODB STATUS\G
```
关注：
- 慢查询
- 锁等待
- buffer pool命中率

## 工具调用建议

使用MySQLQueryTool执行以下查询：

**查询当前连接数：**
```
tool: executeQuery
sql: "SHOW PROCESSLIST"
description: "查看当前数据库连接数和状态"
```

**查询连接统计：**
```
tool: executeQuery
sql: "SHOW STATUS LIKE 'Threads_%'"
description: "查看连接统计信息"
```

## 解决方案

### 短期解决
1. **增加连接池大小**：临时调大连接池maximum-pool-size
2. **释放空闲连接**：重启应用释放长期占用的连接
3. **优化慢查询**：识别并优化执行时间长的SQL

### 长期优化
1. **优化连接池配置**：
   - maximum-pool-size: 根据业务并发量设置（公式：(core_count * 2) + effective_spindle_count）
   - minimum-idle: 设置为与maximum-pool-size相同，避免连接创建开销
   - connection-timeout: 30000ms（30秒）

2. **数据库优化**：
   - 增加max_connections参数
   - 优化慢查询和索引
   - 考虑读写分离

3. **监控告警**：
   - 监控连接池使用率
   - 监控数据库连接数
   - 设置告警阈值

## 相关指标
- connection_count：当前连接数
- wait_timeout：等待超时时间
- max_connections：最大连接数
- connection_pool_usage：连接池使用率

## 典型案例

**案例1：连接池耗尽**
- 现象：应用报错"Connection is not available, request timed out after 30000ms"
- 原因：maximum-pool-size设置为10，但业务并发达到50
- 解决：将maximum-pool-size调整为50，并增加数据库max_connections

**案例2：网络抖动**
- 现象：间歇性出现连接超时
- 原因：跨机房访问数据库，网络延迟高且不稳定
- 解决：将应用迁移到与数据库同机房，或使用专线

**案例3：慢查询导致连接占用**
- 现象：连接数持续增长，最终耗尽
- 原因：某SQL缺少索引，执行时间长达30秒
- 解决：添加索引，SQL执行时间降到10ms
