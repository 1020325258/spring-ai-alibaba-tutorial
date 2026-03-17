# SREmate 多数据源设计文档

## 1. 概述

本文档描述如何在 SREmate 项目中接入第二个 MySQL 数据源，实现多数据源支持。项目当前使用 Spring Boot 3.5.x + JdbcTemplate + HikariCP 连接池。

### 当前架构

```
spring.datasource.sre -> sreDataSource -> JdbcTemplate
```

### 目标架构

```
spring.datasource.sre  -> sreDataSource  -> sreJdbcTemplate
spring.datasource.biz  -> bizDataSource  -> bizJdbcTemplate
```

## 2. 配置方式

### 2.1 application.yml 配置

```yaml
spring:
  datasource:
    # 主数据源 - SRE 业务数据
    sre:
      jdbc-url: ${SRE_DB_URL:jdbc:mysql://localhost:3306/sre_db?useUnicode=true&characterEncoding=utf-8&allowMultiQueries=true&useAffectedRows=true}
      username: ${SRE_DB_USERNAME:root}
      password: ${SRE_DB_PASSWORD:}
      driver-class-name: com.mysql.cj.jdbc.Driver
      hikari:
        maximum-pool-size: 5
        minimum-idle: 2
        connection-timeout: 30000
        idle-timeout: 600000
        max-lifetime: 1800000

    # 第二数据源 - 业务数据
    biz:
      jdbc-url: ${BIZ_DB_URL:jdbc:mysql://localhost:3306/biz_db?useUnicode=true&characterEncoding=utf-8&allowMultiQueries=true&useAffectedRows=true}
      username: ${BIZ_DB_USERNAME:root}
      password: ${BIZ_DB_PASSWORD:}
      driver-class-name: com.mysql.cj.jdbc.Driver
      hikari:
        maximum-pool-size: 5
        minimum-idle: 2
        connection-timeout: 30000
        idle-timeout: 600000
        max-lifetime: 1800000
```

### 2.2 配置属性类

```java
package com.yycome.sremate.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "spring.datasource.sre")
public class SreDataSourceProperties {
    private String jdbcUrl;
    private String username;
    private String password;
    private String driverClassName;
    private Hikari hikari;

    @Data
    public static class Hikari {
        private int maximumPoolSize = 5;
        private int minimumIdle = 2;
        private long connectionTimeout = 30000;
        private long idleTimeout = 600000;
        private long maxLifetime = 1800000;
    }
}

@Data
@Component
@ConfigurationProperties(prefix = "spring.datasource.biz")
public class BizDataSourceProperties {
    // 同 SreDataSourceProperties 结构
}
```

## 3. 数据源配置类

### 3.1 多数据源配置实现

```java
package com.yycome.sremate.infrastructure.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfiguration {

    @Autowired
    private SreDataSourceProperties sreProperties;

    @Autowired
    private BizDataSourceProperties bizProperties;

    /**
     * 主数据源 - SRE 业务数据
     */
    @Bean
    @Primary
    public DataSource sreDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(sreProperties.getJdbcUrl());
        config.setUsername(sreProperties.getUsername());
        config.setPassword(sreProperties.getPassword());
        config.setDriverClassName(sreProperties.getDriverClassName());

        // Hikari 连接池配置
        Hikari hikari = sreProperties.getHikari();
        config.setMaximumPoolSize(hikari.getMaximumPoolSize());
        config.setMinimumIdle(hikari.getMinimumIdle());
        config.setConnectionTimeout(hikari.getConnectionTimeout());
        config.setIdleTimeout(hikari.getIdleTimeout());
        config.setMaxLifetime(hikari.getMaxLifetime());

        config.setPoolName("SreHikariPool");
        return new HikariDataSource(config);
    }

    /**
     * 第二数据源 - 业务数据
     */
    @Bean
    public DataSource bizDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(bizProperties.getJdbcUrl());
        config.setUsername(bizProperties.getUsername());
        config.setPassword(bizProperties.getPassword());
        config.setDriverClassName(bizProperties.getDriverClassName());

        Hikari hikari = bizProperties.getHikari();
        config.setMaximumPoolSize(hikari.getMaximumPoolSize());
        config.setMinimumIdle(hikari.getMinimumIdle());
        config.setConnectionTimeout(hikari.getConnectionTimeout());
        config.setIdleTimeout(hikari.getIdleTimeout());
        config.setMaxLifetime(hikari.getMaxLifetime());

        config.setPoolName("BizHikariPool");
        return new HikariDataSource(config);
    }

    /**
     * 主数据源 JdbcTemplate
     */
    @Bean
    @Primary
    public JdbcTemplate sreJdbcTemplate(DataSource sreDataSource) {
        return new JdbcTemplate(sreDataSource);
    }

    /**
     * 第二数据源 JdbcTemplate
     */
    @Bean
    public JdbcTemplate bizJdbcTemplate(DataSource bizDataSource) {
        return new JdbcTemplate(bizDataSource);
    }
}
```

## 4. 数据源切换策略

### 4.1 策略选择

| 策略 | 适用场景 | 优点 | 缺点 |
|------|---------|------|------|
| **多 JdbcTemplate 注入** | 数据源数量固定，明确知道每个操作使用哪个数据源 | 简单直观，性能好 | 不够灵活 |
| **AbstractRoutingDataSource** | 需要动态切换数据源，如读写分离 | 运行时动态切换 | 事务管理复杂 |
| **AOP + 注解** | 需要透明切换数据源 | 代码侵入性低 | 增加复杂度 |

### 4.2 推荐方案：多 JdbcTemplate 注入

对于 SREmate 项目，推荐使用**多 JdbcTemplate 注入**方案，原因：

1. 数据源数量固定（通常 2-3 个）
2. 每个业务场景明确知道使用哪个数据源
3. 实现简单，易于理解和维护
4. 事务边界清晰

## 5. 事务管理

### 5.1 声明式事务配置

```java
package com.yycome.sremate.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@Configuration
@EnableTransactionManagement
public class TransactionConfiguration {

    /**
     * 主数据源事务管理器
     */
    @Bean
    @Primary
    public PlatformTransactionManager sreTransactionManager(DataSource sreDataSource) {
        return new DataSourceTransactionManager(sreDataSource);
    }

    /**
     * 第二数据源事务管理器
     */
    @Bean
    public PlatformTransactionManager bizTransactionManager(DataSource bizDataSource) {
        return new DataSourceTransactionManager(bizDataSource);
    }
}
```

### 5.2 事务使用方式

#### 单数据源事务

```java
@Service
public class SreService {

    @Autowired
    @Qualifier("sreJdbcTemplate")
    private JdbcTemplate sreJdbcTemplate;

    @Transactional(transactionManager = "sreTransactionManager")
    public void updateSreData() {
        // 操作 sre 数据源
        sreJdbcTemplate.update("...");
    }
}

@Service
public class BizService {

    @Autowired
    @Qualifier("bizJdbcTemplate")
    private JdbcTemplate bizJdbcTemplate;

    @Transactional(transactionManager = "bizTransactionManager")
    public void updateBizData() {
        // 操作 biz 数据源
        bizJdbcTemplate.update("...");
    }
}
```

#### 跨数据源事务（分布式事务）

如果需要跨多个数据源的事务一致性，有以下方案：

**方案一：编程式事务**

```java
@Service
public class CrossDataSourceService {

    @Autowired
    @Qualifier("sreJdbcTemplate")
    private JdbcTemplate sreJdbcTemplate;

    @Autowired
    @Qualifier("bizJdbcTemplate")
    private JdbcTemplate bizJdbcTemplate;

    @Autowired
    private DataSourceTransactionManager sreTransactionManager;

    @Autowired
    private DataSourceTransactionManager bizTransactionManager;

    public void crossDataSourceOperation() {
        TransactionStatus sreStatus = sreTransactionManager.getTransaction(new DefaultTransactionDefinition());
        TransactionStatus bizStatus = bizTransactionManager.getTransaction(new DefaultTransactionDefinition());

        try {
            sreJdbcTemplate.update("...");
            bizJdbcTemplate.update("...");

            sreTransactionManager.commit(sreStatus);
            bizTransactionManager.commit(bizStatus);
        } catch (Exception e) {
            sreTransactionManager.rollback(sreStatus);
            bizTransactionManager.rollback(bizStatus);
            throw e;
        }
    }
}
```

**方案二：Seata 分布式事务框架**

对于复杂的跨数据源事务场景，建议引入 Seata：

```xml
<dependency>
    <groupId>io.seata</groupId>
    <artifactId>seata-spring-boot-starter</artifactId>
    <version>2.0.0</version>
</dependency>
```

## 6. 使用示例

### 6.1 Gateway 层使用多数据源

```java
package com.yycome.sremate.domain.ontology.gateway;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderGateway {

    @Autowired
    @Qualifier("sreJdbcTemplate")
    private JdbcTemplate sreJdbcTemplate;

    public Order findById(String orderId) {
        String sql = "SELECT * FROM orders WHERE order_id = ?";
        return sreJdbcTemplate.queryForObject(sql, new OrderRowMapper(), orderId);
    }
}

@Component
public class ContractGateway {

    @Autowired
    @Qualifier("bizJdbcTemplate")
    private JdbcTemplate bizJdbcTemplate;

    public Contract findByContractNo(String contractNo) {
        String sql = "SELECT * FROM contracts WHERE contract_no = ?";
        return bizJdbcTemplate.queryForObject(sql, new ContractRowMapper(), contractNo);
    }
}
```

### 6.2 单 Service 使用多数据源

```java
@Service
public class CompositeQueryService {

    @Autowired
    @Qualifier("sreJdbcTemplate")
    private JdbcTemplate sreJdbcTemplate;

    @Autowired
    @Qualifier("bizJdbcTemplate")
    private JdbcTemplate bizJdbcTemplate;

    /**
     * 从多个数据源查询数据并组装
     */
    public CompositeResult queryComposite(String orderId) {
        // 从 SRE 数据源查询订单信息
        Order order = sreJdbcTemplate.queryForObject(
            "SELECT * FROM orders WHERE order_id = ?",
            new OrderRowMapper(),
            orderId
        );

        // 从 BIZ 数据源查询合同信息
        Contract contract = bizJdbcTemplate.queryForObject(
            "SELECT * FROM contracts WHERE contract_no = ?",
            new ContractRowMapper(),
            order.getContractNo()
        );

        return new CompositeResult(order, contract);
    }
}
```

## 7. 与现有配置的兼容性

### 7.1 迁移步骤

1. **保留现有数据源配置**：`spring.datasource.sre` 配置保持不变
2. **新增第二数据源配置**：添加 `spring.datasource.biz` 配置
3. **修改 DataSourceConfiguration**：
   - 将 `jdbcTemplate` Bean 重命名为 `sreJdbcTemplate`
   - 添加 `@Primary` 注解到主数据源
   - 添加第二数据源配置
4. **修改依赖注入**：
   - 现有代码需要添加 `@Qualifier("sreJdbcTemplate")` 注解

### 7.2 向后兼容方案

如果希望保持向后兼容，可以保留原有的 `jdbcTemplate` Bean 名称：

```java
@Bean
@Primary
public JdbcTemplate jdbcTemplate(DataSource sreDataSource) {
    return new JdbcTemplate(sreDataSource);
}

@Bean
public JdbcTemplate bizJdbcTemplate(DataSource bizDataSource) {
    return new JdbcTemplate(bizDataSource);
}
```

这样现有代码无需修改，新代码按需注入 `bizJdbcTemplate`。

## 8. 最佳实践

### 8.1 命名规范

| 数据源 | 配置前缀 | Bean 名称 | 连接池名称 |
|--------|---------|-----------|-----------|
| SRE 数据 | spring.datasource.sre | sreDataSource, sreJdbcTemplate | SreHikariPool |
| 业务数据 | spring.datasource.biz | bizDataSource, bizJdbcTemplate | BizHikariPool |

### 8.2 注意事项

1. **不要在事务内跨数据源操作**：除非使用分布式事务框架
2. **明确指定事务管理器**：`@Transactional(transactionManager = "xxx")`
3. **使用 `@Qualifier` 注解**：明确注入哪个 JdbcTemplate
4. **连接池隔离**：每个数据源使用独立的连接池配置
5. **监控连接池状态**：通过 Actuator 监控各数据源连接池

### 8.3 性能优化

```yaml
spring:
  datasource:
    sre:
      hikari:
        maximum-pool-size: 5    # 根据业务压力调整
        minimum-idle: 2
        connection-timeout: 30000
        idle-timeout: 600000
        max-lifetime: 1800000
        leak-detection-threshold: 60000  # 连接泄露检测
```

## 9. 总结

本文档提供了 SREmate 项目多数据源接入的完整设计方案：

- **配置方式**：使用 Spring Boot 配置属性 + HikariConfig 显式配置
- **切换策略**：推荐多 JdbcTemplate 注入方式，简单可靠
- **事务管理**：每个数据源独立事务管理器，跨数据源使用编程式事务或 Seata
- **兼容性**：保持现有配置不变，增量添加新数据源

此方案满足项目当前需求，并具备良好的扩展性，后续可根据需要添加更多数据源。
