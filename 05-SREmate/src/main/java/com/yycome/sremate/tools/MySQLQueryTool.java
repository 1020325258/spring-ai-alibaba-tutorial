package com.yycome.sremate.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
     * 根据项目订单号查询所有合同，并聚合 contract_node、contract_user、contract_field_sharding 数据
     *
     * @param projectOrderId 项目订单号
     * @return 聚合后的合同数据
     */
    @Tool(description = "根据项目订单号（project_order_id）查询该订单下的所有合同，" +
            "并聚合每份合同的节点记录（contract_node）、参与人信息（contract_user）" +
            "以及扩展字段（contract_field_sharding，分库分表，按合同号数字部分取模10确定表后缀）。" +
            "当用户询问\"某订单有哪些合同\"、\"查询订单的合同列表\"、\"订单下的合同详情\"时使用此工具。" +
            "projectOrderId 参数为项目订单号字符串，如 826030619000001899。")
    public String queryContractsByOrderId(String projectOrderId) {
        log.info("queryContractsByOrderId - projectOrderId: {}", projectOrderId);
        try {
            // 1. 查询该订单下所有合同
            List<Map<String, Object>> contracts = jdbcTemplate.queryForList(
                    "SELECT contract_code, type, status, platform_instance_id, amount, ctime " +
                    "FROM contract WHERE project_order_id = ? AND del_status = 0",
                    projectOrderId);

            if (contracts.isEmpty()) {
                return "订单 " + projectOrderId + " 下未找到合同记录";
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("订单 %s 共有 %d 份合同:\n\n", projectOrderId, contracts.size()));

            for (int i = 0; i < contracts.size(); i++) {
                Map<String, Object> contract = contracts.get(i);
                String contractCode = String.valueOf(contract.get("contract_code"));

                sb.append(String.format("【合同 %d】%s\n", i + 1, contractCode));
                sb.append(String.format("  类型: %s  状态: %s  金额: %s  创建时间: %s\n",
                        contract.get("type"), contract.get("status"),
                        contract.get("amount"), contract.get("ctime")));
                if (contract.get("platform_instance_id") != null) {
                    sb.append(String.format("  platform_instance_id: %s\n", contract.get("platform_instance_id")));
                }

                // 2. 查询节点记录
                List<Map<String, Object>> nodes = jdbcTemplate.queryForList(
                        "SELECT node_type, fire_time FROM contract_node " +
                        "WHERE contract_code = ? AND del_status = 0 ORDER BY fire_time",
                        contractCode);
                if (!nodes.isEmpty()) {
                    sb.append("  节点记录:\n");
                    nodes.forEach(n -> sb.append(String.format(
                            "    node_type=%s  fire_time=%s\n", n.get("node_type"), n.get("fire_time"))));
                }

                // 3. 查询参与人
                List<Map<String, Object>> users = jdbcTemplate.queryForList(
                        "SELECT role_type, name, phone, is_sign, is_auth " +
                        "FROM contract_user WHERE contract_code = ? AND del_status = 0",
                        contractCode);
                if (!users.isEmpty()) {
                    sb.append("  参与人:\n");
                    users.forEach(u -> sb.append(String.format(
                            "    role_type=%s  name=%s  phone=%s  is_sign=%s  is_auth=%s\n",
                            u.get("role_type"), u.get("name"), u.get("phone"),
                            u.get("is_sign"), u.get("is_auth"))));
                }

                // 4. 查询扩展字段（分库分表：合同号数字部分 % 10）
                String shardTable = resolveFieldShardingTable(contractCode);
                List<Map<String, Object>> fields = jdbcTemplate.queryForList(
                        "SELECT field_key, field_value FROM " + shardTable +
                        " WHERE contract_code = ? AND del_status = 0 LIMIT 20",
                        contractCode);
                if (!fields.isEmpty()) {
                    sb.append(String.format("  扩展字段（%s）:\n", shardTable));
                    fields.forEach(f -> sb.append(String.format(
                            "    %s = %s\n", f.get("field_key"), f.get("field_value"))));
                }

                sb.append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("queryContractsByOrderId 失败", e);
            return "查询合同列表失败: " + e.getMessage();
        }
    }

    /**
     * 根据合同号计算分片表名
     * 规则：去除合同号中的非数字字符，取数字部分 % 10
     */
    private String resolveFieldShardingTable(String contractCode) {
        String digits = contractCode.replaceAll("[^0-9]", "");
        int shard = (int) (Long.parseLong(digits) % 10);
        return "contract_field_sharding_" + shard;
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
