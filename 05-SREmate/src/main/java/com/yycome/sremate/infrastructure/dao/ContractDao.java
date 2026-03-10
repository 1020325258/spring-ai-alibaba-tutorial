package com.yycome.sremate.infrastructure.dao;

import com.yycome.sremate.infrastructure.util.DateTimeUtil;
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
        base.put("ctime", DateTimeUtil.format(row.get("ctime")));
        return base;
    }

    /**
     * 查询合同节点记录
     */
    public List<Map<String, Object>> fetchNodes(String contractCode) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT node_type, fire_time FROM contract_node " +
                "WHERE contract_code = ? AND del_status = 0 ORDER BY fire_time",
                contractCode);
        // 格式化时间字段
        return rows.stream().map(row -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("nodeType", row.get("node_type"));
            result.put("fireTime", DateTimeUtil.format(row.get("fire_time")));
            return result;
        }).toList();
    }

    /**
     * 查询合同操作日志
     */
    public List<Map<String, Object>> fetchLogs(String contractCode) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT type, content, remark, ctime, create_user_name, create_user_id FROM contract_log " +
                "WHERE contract_code = ? AND del_status = 0 ORDER BY ctime DESC LIMIT 50",
                contractCode);
        // 格式化时间字段
        return rows.stream().map(row -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", row.get("type"));
            result.put("content", row.get("content"));
            result.put("remark", row.get("remark"));
            result.put("ctime", DateTimeUtil.format(row.get("ctime")));
            result.put("createUserName", row.get("create_user_name"));
            result.put("createUserId", row.get("create_user_id"));
            return result;
        }).toList();
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
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT contract_code, bill_code, company_code, bind_type, ctime, mtime " +
                "FROM contract_quotation_relation " +
                "WHERE contract_code = ? AND del_status = 0",
                contractCode);
        // 格式化时间字段
        return rows.stream().map(row -> {
            Map<String, Object> result = new LinkedHashMap<>(row);
            result.put("ctime", DateTimeUtil.format(row.get("ctime")));
            result.put("mtime", DateTimeUtil.format(row.get("mtime")));
            return result;
        }).toList();
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
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT contract_code, type, status, platform_instance_id, amount, ctime " +
                "FROM contract WHERE project_order_id = ? AND del_status = 0",
                projectOrderId);
        // 格式化时间字段
        return rows.stream().map(row -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("contract_code", row.get("contract_code"));
            result.put("type", row.get("type"));
            result.put("status", row.get("status"));
            result.put("platform_instance_id", row.get("platform_instance_id"));
            result.put("amount", row.get("amount"));
            result.put("ctime", DateTimeUtil.format(row.get("ctime")));
            return result;
        }).toList();
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

    /**
     * 根据合同编号查询配置相关字段
     * 返回 project_order_id、business_type、gb_code、company_code、type
     */
    public Map<String, Object> fetchContractConfigFields(String contractCode) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT project_order_id, business_type, gb_code, company_code, type " +
                "FROM contract WHERE contract_code = ? AND del_status = 0 LIMIT 1",
                contractCode);
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.get(0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectOrderId", row.get("project_order_id"));
        result.put("businessType", row.get("business_type"));
        result.put("gbCode", row.get("gb_code"));
        result.put("companyCode", row.get("company_code"));
        result.put("type", row.get("type"));
        return result;
    }

    /**
     * 根据订单号和合同类型查询配置相关字段
     * 返回 business_type、gb_code、company_code
     */
    public Map<String, Object> fetchContractConfigFieldsByOrderIdAndType(String projectOrderId, String contractType) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT business_type, gb_code, company_code " +
                "FROM contract WHERE project_order_id = ? AND type = ? AND del_status = 0 LIMIT 1",
                projectOrderId, contractType);
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.get(0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("businessType", row.get("business_type"));
        result.put("gbCode", row.get("gb_code"));
        result.put("companyCode", row.get("company_code"));
        return result;
    }

    /**
     * 根据订单号查询所有合同类型
     */
    public List<String> fetchContractTypesByOrderId(String projectOrderId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT DISTINCT type FROM contract WHERE project_order_id = ? AND del_status = 0",
                projectOrderId);
        return rows.stream()
                .map(row -> String.valueOf(row.get("type")))
                .toList();
    }

    /**
     * 根据订单号查询 project_config_snap 表的 contract_config_id
     */
    public String fetchContractConfigId(String projectOrderId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT contract_config_id FROM project_config_snap " +
                "WHERE project_order_id = ? AND del_status = 0 LIMIT 1",
                projectOrderId);
        if (rows.isEmpty()) return null;
        Object val = rows.get(0).get("contract_config_id");
        return val != null ? val.toString() : null;
    }

    /**
     * 查询 contract_city_company_info 表数据
     * 根据 business_type、gb_code、company_code、version、type、sign_channel_type 查询
     * 过滤不需要的字段：show_platform、seal_code、fields_map、merge_launch_type
     */
    public List<Map<String, Object>> fetchCityCompanyInfo(String businessType, String gbCode, String companyCode, int version, String type) {
        // 过滤不需要输出的字段
        String selectFields = """
                    id, business_type, gb_code, company_code, version, contract_type, audit_type,
                    user_sign_type, form_id, form_key, second_form_id, second_form_key, third_form_id, third_form_key,
                    ctime, mtime
                    """;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT " + selectFields + " FROM contract_city_company_info " +
                "WHERE business_type = ? AND gb_code = ? AND company_code = ? AND version = ? AND contract_type = ? AND sign_channel_type = 1 AND del_status = 0",
                businessType, gbCode, companyCode, version, type);

        // 格式化时间字段
        return rows.stream().map(row -> {
            Map<String, Object> result = new LinkedHashMap<>(row);
            result.put("ctime", DateTimeUtil.format(row.get("ctime")));
            result.put("mtime", DateTimeUtil.format(row.get("mtime")));
            return result;
        }).toList();
    }
}
