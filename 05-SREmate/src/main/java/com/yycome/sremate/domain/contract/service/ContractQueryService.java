package com.yycome.sremate.domain.contract.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yycome.sremate.infrastructure.dao.ContractDao;
import com.yycome.sremate.trigger.agent.HttpEndpointTool;
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
}
