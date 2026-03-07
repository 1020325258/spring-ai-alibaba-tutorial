package com.yycome.sremate.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
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
    private final HttpQueryTool httpQueryTool;

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

    /**
     * 根据合同编号查询 platform_instance_id（原始值）
     */
    private Long findPlatformInstanceId(String contractCode) {
        List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SELECT platform_instance_id FROM contract WHERE contract_code = ? LIMIT 1",
                contractCode);
        if (results.isEmpty()) {
            return null;
        }
        Object val = results.get(0).get("platform_instance_id");
        if (val == null) {
            return null;
        }
        return Long.parseLong(val.toString());
    }

    /**
     * 根据合同编号查询 platform_instance_id（供 LLM 单步使用）
     *
     * @param contractCode 合同编号（contract_code）
     * @return platform_instance_id 文本描述
     */
    @Tool(description = "根据合同编号（contract_code）查询合同的 platform_instance_id。" +
            "若只需要 instanceId 而不需要版式数据时使用此工具。" +
            "如需同时获取 form_id，请直接使用 queryContractFormId。" +
            "contractCode 参数为合同编号字符串。")
    public String queryContractInstanceId(String contractCode) {
        log.info("queryContractInstanceId - contractCode: {}", contractCode);
        try {
            Long instanceId = findPlatformInstanceId(contractCode);
            if (instanceId == null) {
                return "未找到合同编号为 " + contractCode + " 的合同记录";
            }
            return "contract_code=" + contractCode + " 对应的 platform_instance_id 为: " + instanceId;
        } catch (Exception e) {
            log.error("查询 platform_instance_id 失败", e);
            return "查询失败: " + e.getMessage();
        }
    }

    /**
     * 根据合同编号查询版式 form_id（自动串联数据库查询与 HTTP 接口调用）
     *
     * @param contractCode 合同编号（contract_code），如 C1772854666284956
     * @return 版式接口响应（含 form_id）
     */
    @Tool(description = "根据合同编号（contract_code）查询合同对应的版式 form_id。" +
            "该工具自动完成两步操作：1) 从数据库查询合同的 platform_instance_id；" +
            "2) 以 platform_instance_id 作为 instanceId 调用版式查询接口获取 form_id。" +
            "当用户询问\"查询某合同的版式\"、\"查合同的form_id\"、\"合同编号XXX的版式是什么\"时使用此工具。" +
            "contractCode 参数为合同编号字符串，如 C1772854666284956。")
    public String queryContractFormId(String contractCode) {
        log.info("queryContractFormId - contractCode: {}", contractCode);
        try {
            Long instanceId = findPlatformInstanceId(contractCode);
            if (instanceId == null) {
                return "未找到合同编号为 " + contractCode + " 的合同记录，无法查询版式";
            }
            log.info("queryContractFormId - contractCode: {}, instanceId: {}", contractCode, instanceId);
            Map<String, String> params = new HashMap<>();
            params.put("instanceId", instanceId.toString());
            return httpQueryTool.callPredefinedEndpoint("contract-form-data", params);
        } catch (Exception e) {
            log.error("查询合同版式失败", e);
            return "查询合同版式失败: " + e.getMessage();
        }
    }
}
