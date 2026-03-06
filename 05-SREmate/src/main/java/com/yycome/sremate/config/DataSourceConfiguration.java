package com.yycome.sremate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 数据源配置类
 */
@Configuration
public class DataSourceConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.sre")
    public DataSource sreDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource sreDataSource) {
        return new JdbcTemplate(sreDataSource);
    }
}
