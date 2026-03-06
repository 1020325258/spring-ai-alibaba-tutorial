package com.yycome.sremate.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * MySQL查询工具
 * 用于执行MySQL查询，排查数据库相关问题
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MySQLQueryTool {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 执行MySQL查询
     *
     * @param sql 要执行的SQL语句（仅支持SELECT查询）
     * @param description 查询描述
     * @return 查询结果
     */
    @Tool(description = "执行MySQL查询，用于排查数据库相关问题。" +
            "仅支持SELECT查询，禁止执行INSERT、UPDATE、DELETE等修改操作。" +
            "sql参数是要执行的SELECT语句，description参数是对查询的描述。")
    public String executeQuery(String sql, String description) {
        log.info("调用MySQLQueryTool - 描述: {}, SQL: {}", description, sql);

        // 安全检查：只允许SELECT查询
        String trimmedSql = sql.trim().toUpperCase();
        if (!trimmedSql.startsWith("SELECT") && !trimmedSql.startsWith("SHOW")) {
            return "错误：仅支持SELECT和SHOW查询，禁止执行修改操作";
        }

        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

            if (results.isEmpty()) {
                return "查询结果为空";
            }

            // 格式化输出
            StringBuilder result = new StringBuilder();
            result.append(String.format("查询: %s\n", description));
            result.append(String.format("SQL: %s\n", sql));
            result.append(String.format("返回 %d 条记录:\n\n", results.size()));

            for (int i = 0; i < results.size(); i++) {
                Map<String, Object> row = results.get(i);
                result.append(String.format("记录 %d:\n", i + 1));
                row.forEach((key, value) ->
                    result.append(String.format("  %s: %s\n", key, value))
                );
                result.append("\n");

                // 限制输出数量，避免结果过大
                if (i >= 9) {
                    result.append("...(仅显示前10条记录)\n");
                    break;
                }
            }

            return result.toString();

        } catch (Exception e) {
            log.error("MySQL查询执行失败", e);
            return "查询执行失败: " + e.getMessage();
        }
    }
}
