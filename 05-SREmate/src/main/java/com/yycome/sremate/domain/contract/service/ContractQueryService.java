package com.yycome.sremate.domain.contract.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yycome.sremate.infrastructure.dao.ContractDao;
import com.yycome.sremate.trigger.agent.HttpEndpointTool;
import com.yycome.sremate.types.enums.ContractTypeEnum;
import com.yycome.sremate.types.enums.QueryDataType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * 合同聚合查询服务（领域层）
 * 负责并行调 DAO、组装结果 Map、序列化 JSON
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractQueryService {

    private final ContractDao contractDao;
    private final HttpEndpointTool httpEndpointTool;
    private final ObjectMapper objectMapper;

    // 用于并行 DB 子查询的线程池，大小与连接池对齐
    private final ExecutorService dbQueryExecutor = Executors.newFixedThreadPool(5);

    /**
     * 根据合同编号和查询类型聚合合同数据
     */
    public Map<String, Object> queryByCode(String contractCode, QueryDataType type) {
        CompletableFuture<Map<String, Object>> baseFuture = CompletableFuture.supplyAsync(
                () -> contractDao.fetchContractBase(contractCode), dbQueryExecutor);

        Map<String, CompletableFuture<?>> futures = new LinkedHashMap<>();
        switch (type) {
            case ALL -> {
                futures.put("contract_node", CompletableFuture.supplyAsync(() -> contractDao.fetchNodes(contractCode), dbQueryExecutor));
                futures.put("contract_user", CompletableFuture.supplyAsync(() -> contractDao.fetchUsers(contractCode), dbQueryExecutor));
                futures.put("contract_field_sharding", CompletableFuture.supplyAsync(() -> contractDao.fetchFields(contractCode), dbQueryExecutor));
                futures.put("contract_quotation_relation", CompletableFuture.supplyAsync(() -> contractDao.fetchQuotations(contractCode), dbQueryExecutor));
            }
            case CONTRACT_NODE -> {
                futures.put("contract_node", CompletableFuture.supplyAsync(() -> contractDao.fetchNodes(contractCode), dbQueryExecutor));
                futures.put("contract_log", CompletableFuture.supplyAsync(() -> contractDao.fetchLogs(contractCode), dbQueryExecutor));
            }
            case CONTRACT_FIELD ->
                futures.put("contract_field_sharding", CompletableFuture.supplyAsync(() -> contractDao.fetchFields(contractCode), dbQueryExecutor));
            case CONTRACT_USER ->
                futures.put("contract_user", CompletableFuture.supplyAsync(() -> contractDao.fetchUsers(contractCode), dbQueryExecutor));
        }

        CompletableFuture.allOf(
                Stream.concat(Stream.of(baseFuture), futures.values().stream())
                        .toArray(CompletableFuture[]::new)
        ).join();

        Map<String, Object> base = baseFuture.join();
        if (base == null) return null;

        Map<String, Object> result = new LinkedHashMap<>(base);
        futures.forEach((key, future) -> result.put(key, future.join()));
        return result;
    }

    /**
     * 根据项目订单号查询所有合同，聚合关联数据
     */
    public List<Map<String, Object>> queryByOrderId(String projectOrderId) {
        List<Map<String, Object>> contracts = contractDao.fetchContractsByOrderId(projectOrderId);
        if (contracts.isEmpty()) return null;

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

            String shardTable = contractDao.resolveFieldShardingTable(contractCode);

            CompletableFuture<List<Map<String, Object>>> nodesFuture = CompletableFuture.supplyAsync(
                    () -> contractDao.fetchNodes(contractCode), dbQueryExecutor);
            CompletableFuture<List<Map<String, Object>>> usersFuture = CompletableFuture.supplyAsync(
                    () -> contractDao.fetchUsers(contractCode), dbQueryExecutor);
            CompletableFuture<Map<String, Object>> fieldsFuture = CompletableFuture.supplyAsync(
                    () -> contractDao.fetchFields(contractCode), dbQueryExecutor);
            CompletableFuture<List<Map<String, Object>>> quotationFuture = CompletableFuture.supplyAsync(
                    () -> contractDao.fetchQuotations(contractCode), dbQueryExecutor);

            CompletableFuture.allOf(nodesFuture, usersFuture, fieldsFuture, quotationFuture).join();

            item.put("contract_node", nodesFuture.join());
            item.put("contract_user", usersFuture.join());
            item.put("contract_field_sharding", fieldsFuture.join());
            item.put("contract_field_sharding_table", shardTable);
            item.put("contract_quotation_relation", quotationFuture.join());

            result.add(item);
        }
        return result;
    }

    /**
     * 查询合同的 platform_instance_id
     */
    public Long queryInstanceId(String contractCode) {
        return contractDao.findPlatformInstanceId(contractCode);
    }

    /**
     * 查询合同版式 form_id（查库获取 instanceId + 调用 HTTP 网关）
     */
    public String queryFormId(String contractCode) {
        Long instanceId = contractDao.findPlatformInstanceId(contractCode);
        if (instanceId == null) return null;
        Map<String, String> params = new HashMap<>();
        params.put("instanceId", instanceId.toString());
        return httpEndpointTool.callPredefinedEndpoint("contract-form-data", params);
    }

    /**
     * 将 Map 序列化为 JSON 字符串
     */
    public String toJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    /**
     * 查询合同配置表（contract_city_company_info）
     * 支持两种入参：
     * - contractCode: 合同编号（C前缀+数字），自动从 contract 表获取 type
     * - projectOrderId: 项目订单号（纯数字），需要指定 contractType
     *
     * 查询流程：
     * 1. 根据入参获取 project_order_id 和 type
     * 2. 查询 project_config_snap 获取 contract_config_id
     * 3. 取 contract_config_id 第一个版本号
     * 4. 获取 business_type、gb_code、company_code
     * 5. 查询 contract_city_company_info（增加 type 和 sign_channel_type=1 条件）
     *
     * @return 如果需要询问合同类型，返回包含 needAskType=true 和可选类型列表的 Map
     */
    public Map<String, Object> queryContractConfig(String contractCode, String projectOrderId, String contractType) {
        String actualOrderId = projectOrderId;
        Map<String, Object> contractFields;
        String type;

        // 根据入参类型决定查询路径
        if (contractCode != null && !contractCode.isBlank()) {
            // 入参是合同号，自动从 contract 表获取 type
            contractFields = contractDao.fetchContractConfigFields(contractCode);
            if (contractFields == null) {
                return null;
            }
            actualOrderId = String.valueOf(contractFields.get("projectOrderId"));
            type = String.valueOf(contractFields.get("type"));
        } else if (projectOrderId != null && !projectOrderId.isBlank()) {
            // 入参是订单号，需要指定合同类型
            // 先查询该订单实际有哪些合同类型
            List<String> actualTypeCodes = contractDao.fetchContractTypesByOrderId(projectOrderId);
            if (actualTypeCodes.isEmpty()) {
                return null;
            }

            // 将实际类型代码转换为用户友好的名称
            List<String> availableTypeNames = actualTypeCodes.stream()
                    .map(code -> {
                        String name = ContractTypeEnum.getNameByCode(Byte.parseByte(code));
                        return name != null ? name + "(" + code + ")" : code;
                    })
                    .toList();

            // 用户未指定合同类型，返回需要询问的信息
            if (contractType == null || contractType.isBlank()) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("needAskType", true);
                result.put("message", "该订单存在多种合同类型，请指定要查询的合同类型");
                result.put("availableTypes", availableTypeNames);
                result.put("projectOrderId", projectOrderId);
                return result;
            }

            // 尝试将用户输入转换为类型代码
            Byte typeCode = ContractTypeEnum.getCodeByInput(contractType);
            String typeCodeStr = (typeCode != null) ? String.valueOf(typeCode) : contractType;

            // 校验转换后的类型是否在该订单实际存在的类型列表中
            if (!actualTypeCodes.contains(typeCodeStr)) {
                // 类型不匹配，返回可用类型列表供用户选择
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("needAskType", true);
                result.put("message", "合同类型 \"" + contractType + "\" 不存在或该订单无此类型合同");
                result.put("availableTypes", availableTypeNames);
                result.put("projectOrderId", projectOrderId);
                result.put("inputType", contractType);
                return result;
            }

            // 使用指定的合同类型查询
            contractFields = contractDao.fetchContractConfigFieldsByOrderIdAndType(projectOrderId, typeCodeStr);
            if (contractFields == null) {
                return null;
            }
            type = typeCodeStr;
        } else {
            return null;
        }

        // 查询 contract_config_id
        String contractConfigId = contractDao.fetchContractConfigId(actualOrderId);
        if (contractConfigId == null || contractConfigId.isBlank()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("error", "未找到 project_config_snap 记录，project_order_id: " + actualOrderId);
            return result;
        }

        // 取第一个版本号
        String[] versions = contractConfigId.split("_");
        if (versions.length < 1) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("error", "contract_config_id 格式无效: " + contractConfigId);
            return result;
        }
        int version = Integer.parseInt(versions[0]);

        // 获取查询字段
        String businessType = String.valueOf(contractFields.get("businessType"));
        String gbCode = String.valueOf(contractFields.get("gbCode"));
        String companyCode = String.valueOf(contractFields.get("companyCode"));

        // 查询 contract_city_company_info（增加 type 和 sign_channel_type=1 条件）
        List<Map<String, Object>> cityCompanyInfo = contractDao.fetchCityCompanyInfo(
                businessType, gbCode, companyCode, version, type);

        // 组装结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("input", contractCode != null ? contractCode : projectOrderId);
        result.put("projectOrderId", actualOrderId);
        result.put("contractConfigId", contractConfigId);
        result.put("version", version);
        result.put("businessType", businessType);
        result.put("gbCode", gbCode);
        result.put("companyCode", companyCode);
        result.put("type", type);
        result.put("signChannelType", 1);
        result.put("contract_city_company_info", cityCompanyInfo);

        return result;
    }
}
