package com.yycome.sremate.infrastructure.dao;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 合同数据访问层（SQL 访问 + 分表路由）
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ContractDao {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 查询合同主表基本信息
     */
    public Map<String, Object> fetchContractBase(String contractCode) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT contract_code, type, status, platform_instance_id, amount, project_order_id, ctime " +
                "FROM contract WHERE contract_code = ? AND del_status = 0 LIMIT 1",
                contractCode);
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.get(0);
        Map<String, Object> base = new LinkedHashMap<>();
        base.put("contractCode", row.get("contract_code"));
        base.put("type", row.get("type"));
        base.put("status", row.get("status"));
        base.put("amount", row.get("amount"));
        base.put("platformInstanceId", row.get("platform_instance_id"));
        base.put("projectOrderId", row.get("project_order_id"));
        base.put("ctime", String.valueOf(row.get("ctime")));
        return base;
    }

    /**
     * 查询合同节点记录
     */
    public List<Map<String, Object>> fetchNodes(String contractCode) {
        return jdbcTemplate.queryForList(
                "SELECT node_type, fire_time FROM contract_node " +
                "WHERE contract_code = ? AND del_status = 0 ORDER BY fire_time",
                contractCode);
    }

    /**
     * 查询合同操作日志
     */
    public List<Map<String, Object>> fetchLogs(String contractCode) {
        return jdbcTemplate.queryForList(
                "SELECT type, content, remark, ctime, create_user_name, create_user_id FROM contract_log " +
                "WHERE contract_code = ? AND del_status = 0 ORDER BY ctime DESC LIMIT 50",
                contractCode);
    }

    /**
     * 查询合同参与人（签约人）
     */
    public List<Map<String, Object>> fetchUsers(String contractCode) {
        return jdbcTemplate.queryForList(
                "SELECT role_type, name, phone, is_sign, is_auth " +
                "FROM contract_user WHERE contract_code = ? AND del_status = 0",
                contractCode);
    }

    /**
     * 查询合同扩展字段（分表）
     */
    public Map<String, Object> fetchFields(String contractCode) {
        String shardTable = resolveFieldShardingTable(contractCode);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT field_key, field_value FROM " + shardTable +
                " WHERE contract_code = ? AND del_status = 0 LIMIT 20",
                contractCode);
        Map<String, Object> fieldMap = new LinkedHashMap<>();
        rows.forEach(f -> fieldMap.put(
                String.valueOf(f.get("field_key")),
                f.get("field_value")));
        fieldMap.put("_shardTable", shardTable);
        return fieldMap;
    }

    /**
     * 查询合同报价关联记录
     */
    public List<Map<String, Object>> fetchQuotations(String contractCode) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM contract_quotation_relation " +
                "WHERE contract_code = ? AND del_status = 0",
                contractCode);
    }

    /**
     * 根据合同编号查询 platform_instance_id
     */
    public Long findPlatformInstanceId(String contractCode) {
        List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SELECT platform_instance_id FROM contract WHERE contract_code = ? LIMIT 1",
                contractCode);
        if (results.isEmpty()) return null;
        Object val = results.get(0).get("platform_instance_id");
        if (val == null) return null;
        return Long.parseLong(val.toString());
    }

    /**
     * 根据项目订单号查询合同基本列表
     */
    public List<Map<String, Object>> fetchContractsByOrderId(String projectOrderId) {
        return jdbcTemplate.queryForList(
                "SELECT contract_code, type, status, platform_instance_id, amount, ctime " +
                "FROM contract WHERE project_order_id = ? AND del_status = 0",
                projectOrderId);
    }

    /**
     * 根据合同号计算分片表名
     * 规则：去除合同号中的非数字字符，取数字部分 % 10
     */
    public String resolveFieldShardingTable(String contractCode) {
        String digits = contractCode.replaceAll("[^0-9]", "");
        int shard = (int) (Long.parseLong(digits) % 10);
        return "contract_field_sharding_" + shard;
    }
}
