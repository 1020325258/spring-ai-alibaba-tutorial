package com.yycome.sremate.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private final ObjectMapper objectMapper;

    // 用于并行 DB 子查询的线程池，大小与连接池对齐
    private final ExecutorService dbQueryExecutor = Executors.newFixedThreadPool(5);

    /**
     * 执行MySQL查询
     *
     * @param sql         要执行的SQL语句（仅支持SELECT查询）
     * @param description 查询描述
     * @return JSON格式查询结果
     */
    @Tool(description = "执行MySQL查询，用于排查数据库相关问题。" +
            "仅支持SELECT查询，禁止执行INSERT、UPDATE、DELETE等修改操作。" +
            "sql参数是要执行的SELECT语句，description参数是对查询的描述。")
    public String executeQuery(String sql, String description) {
        log.info("调用MySQLQueryTool - 描述: {}, SQL: {}", description, sql);

        String trimmedSql = sql.trim().toUpperCase();
        if (!trimmedSql.startsWith("SELECT") && !trimmedSql.startsWith("SHOW")) {
            return toErrorJson("仅支持SELECT和SHOW查询，禁止执行修改操作");
        }

        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
            List<Map<String, Object>> limited = results.size() > 10 ? results.subList(0, 10) : results;

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("description", description);
            response.put("sql", sql);
            response.put("total", results.size());
            response.put("rows", limited);

            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("MySQL查询执行失败", e);
            return toErrorJson(e.getMessage());
        }
    }

    /**
     * 根据合同编号查询 platform_instance_id（原始值，内部复用）
     */
    private Long findPlatformInstanceId(String contractCode) {
        List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SELECT platform_instance_id FROM contract WHERE contract_code = ? LIMIT 1",
                contractCode);
        if (results.isEmpty()) return null;
        Object val = results.get(0).get("platform_instance_id");
        if (val == null) return null;
        return Long.parseLong(val.toString());
    }

    /**
     * 根据合同编号查询 platform_instance_id
     *
     * @param contractCode 合同编号（contract_code）
     * @return JSON格式结果
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
                return toErrorJson("未找到合同编号为 " + contractCode + " 的合同记录");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("contractCode", contractCode);
            result.put("platformInstanceId", instanceId);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("查询 platform_instance_id 失败", e);
            return toErrorJson(e.getMessage());
        }
    }

    /**
     * 根据项目订单号查询所有合同，聚合 contract_node、contract_user、contract_field_sharding 数据
     *
     * @param projectOrderId 项目订单号
     * @return JSON格式聚合合同数据
     */
    @Tool(description = "根据项目订单号（project_order_id）查询该订单下的所有合同，" +
            "并聚合每份合同的节点记录（contract_node）、参与人信息（contract_user）" +
            "以及扩展字段（contract_field_sharding，分库分表，按合同号数字部分取模10确定表后缀）。" +
            "当用户询问\"某订单有哪些合同\"、\"查询订单的合同列表\"、\"订单下的合同详情\"时使用此工具。" +
            "projectOrderId 参数为项目订单号字符串，如 826030619000001899。")
    public String queryContractsByOrderId(String projectOrderId) {
        log.info("queryContractsByOrderId - projectOrderId: {}", projectOrderId);
        try {
            List<Map<String, Object>> contracts = jdbcTemplate.queryForList(
                    "SELECT contract_code, type, status, platform_instance_id, amount, ctime " +
                    "FROM contract WHERE project_order_id = ? AND del_status = 0",
                    projectOrderId);

            if (contracts.isEmpty()) {
                return toErrorJson("订单 " + projectOrderId + " 下未找到合同记录");
            }

            List<Map<String, Object>> result = new ArrayList<>();

            for (Map<String, Object> contract : contracts) {
                String contractCode = String.valueOf(contract.get("contract_code"));

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("contractCode", contractCode);
                item.put("type", contract.get("type"));
                item.put("status", contract.get("status"));
                item.put("amount", contract.get("amount"));
                item.put("platformInstanceId", contract.get("platform_instance_id"));
                item.put("ctime", String.valueOf(contract.get("ctime")));

                // 四张关联表并行查询
                String shardTable = resolveFieldShardingTable(contractCode);

                CompletableFuture<List<Map<String, Object>>> nodesFuture = CompletableFuture.supplyAsync(
                        () -> jdbcTemplate.queryForList(
                                "SELECT node_type, fire_time FROM contract_node " +
                                "WHERE contract_code = ? AND del_status = 0 ORDER BY fire_time",
                                contractCode), dbQueryExecutor);

                CompletableFuture<List<Map<String, Object>>> usersFuture = CompletableFuture.supplyAsync(
                        () -> jdbcTemplate.queryForList(
                                "SELECT role_type, name, phone, is_sign, is_auth " +
                                "FROM contract_user WHERE contract_code = ? AND del_status = 0",
                                contractCode), dbQueryExecutor);

                CompletableFuture<List<Map<String, Object>>> fieldsFuture = CompletableFuture.supplyAsync(
                        () -> jdbcTemplate.queryForList(
                                "SELECT field_key, field_value FROM " + shardTable +
                                " WHERE contract_code = ? AND del_status = 0 LIMIT 20",
                                contractCode), dbQueryExecutor);

                CompletableFuture<List<Map<String, Object>>> quotationFuture = CompletableFuture.supplyAsync(
                        () -> jdbcTemplate.queryForList(
                                "SELECT * FROM contract_quotation_relation " +
                                "WHERE contract_code = ? AND del_status = 0",
                                contractCode), dbQueryExecutor);

                CompletableFuture.allOf(nodesFuture, usersFuture, fieldsFuture, quotationFuture).join();

                item.put("contract_node", nodesFuture.join());
                item.put("contract_user", usersFuture.join());

                Map<String, Object> fieldMap = new LinkedHashMap<>();
                fieldsFuture.join().forEach(f -> fieldMap.put(
                        String.valueOf(f.get("field_key")),
                        tryParseJson(f.get("field_value"))));
                item.put("contract_field_sharding", fieldMap);
                item.put("contract_field_sharding_table", shardTable);
                item.put("contract_quotation_relation", quotationFuture.join());

                result.add(item);
            }

            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("queryContractsByOrderId 失败", e);
            return toErrorJson(e.getMessage());
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
     * @return 版式接口原始响应
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
                return toErrorJson("未找到合同编号为 " + contractCode + " 的合同记录，无法查询版式");
            }
            log.info("queryContractFormId - contractCode: {}, instanceId: {}", contractCode, instanceId);
            Map<String, String> params = new HashMap<>();
            params.put("instanceId", instanceId.toString());
            return httpQueryTool.callPredefinedEndpoint("contract-form-data", params);
        } catch (Exception e) {
            log.error("查询合同版式失败", e);
            return toErrorJson(e.getMessage());
        }
    }

    /**
     * 若值是合法的 JSON 对象或数组字符串，解析后返回；否则原样返回。
     */
    private Object tryParseJson(Object value) {
        if (!(value instanceof String str)) return value;
        str = str.trim();
        if (!(str.startsWith("{") || str.startsWith("["))) return value;
        try {
            return objectMapper.readValue(str, Object.class);
        } catch (Exception e) {
            return value;
        }
    }

    private String toErrorJson(String message) {
        try {
            Map<String, String> err = new LinkedHashMap<>();
            err.put("error", message);
            return objectMapper.writeValueAsString(err);
        } catch (Exception ex) {
            return "{\"error\":\"" + message + "\"}";
        }
    }
}
