package com.yycome.sremate.trigger.agent;

import com.yycome.sremate.domain.contract.service.ContractQueryService;
import com.yycome.sremate.types.enums.QueryDataType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 合同查询工具（触发层，只含 @Tool 方法）
 * 负责参数解析 + 调用 ContractQueryService + 返回 JSON
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractTool {

    private final ContractQueryService contractQueryService;

    /**
     * 根据合同编号查询合同数据（支持4种查询类型）
     *
     * @param contractCode 合同编号（contract_code），C前缀+数字，如 C1772925352128725
     * @param dataType     查询类型：ALL / CONTRACT_NODE / CONTRACT_FIELD / CONTRACT_USER
     * @return JSON格式聚合数据
     */
    @Tool(description = """
            根据合同编号（contract_code）查询合同数据。
            contractCode 参数为合同编号字符串，格式为C前缀+数字，如 C1772925352128725。
            dataType 参数控制查询范围，根据用户意图填写：
            - 用户说"合同数据"、"合同详情"、"合同信息" → 填 ALL（返回 contract + contract_node + contract_user + contract_quotation_relation + contract_field_sharding）
            - 用户说"合同节点"、"节点数据"、"操作日志"、"合同日志" → 填 CONTRACT_NODE（返回 contract + contract_node + contract_log）
            - 用户说"合同字段"、"字段数据" → 填 CONTRACT_FIELD（返回 contract + contract_field_sharding）
            - 用户说"签约人"、"合同用户"、"参与人" → 填 CONTRACT_USER（返回 contract + contract_user）
            注意：若用户提供的编号为纯数字（无C前缀），说明是订单号，应使用 queryContractsByOrderId 工具。""")
    public String queryContractData(String contractCode, String dataType) {
        log.info("queryContractData - contractCode: {}, dataType: {}", contractCode, dataType);
        try {
            QueryDataType type = QueryDataType.valueOf(dataType.toUpperCase());
            Map<String, Object> result = contractQueryService.queryByCode(contractCode, type);
            if (result == null) {
                return toErrorJson("未找到合同编号为 " + contractCode + " 的合同记录");
            }
            return contractQueryService.toJson(result);
        } catch (IllegalArgumentException e) {
            return toErrorJson("无效的 dataType 参数: " + dataType + "，可选值: ALL / CONTRACT_NODE / CONTRACT_FIELD / CONTRACT_USER");
        } catch (Exception e) {
            log.error("queryContractData 失败", e);
            return toErrorJson(e.getMessage());
        }
    }

    /**
     * 根据项目订单号查询所有合同，聚合关联数据
     *
     * @param projectOrderId 项目订单号
     * @return JSON格式聚合合同数据
     */
    @Tool(description = """
            根据项目订单号（project_order_id）查询该订单下的所有合同，
            并聚合每份合同的节点记录（contract_node）、参与人信息（contract_user）
            以及扩展字段（contract_field_sharding，分库分表，按合同号数字部分取模10确定表后缀）。
            当用户询问"某订单有哪些合同"、"查询订单的合同列表"、"订单下的合同详情"时使用此工具。
            projectOrderId 参数为项目订单号字符串，格式为纯数字，如 826030619000001899。
            注意：若用户提供的编号以字母C开头（如 C1772925352128725），则该编号是合同编号而非订单号，
            此时不得调用本工具，应使用 queryContractData 工具。""")
    public String queryContractsByOrderId(String projectOrderId) {
        log.info("queryContractsByOrderId - projectOrderId: {}", projectOrderId);
        try {
            List<Map<String, Object>> result = contractQueryService.queryByOrderId(projectOrderId);
            if (result == null) {
                return toErrorJson("订单 " + projectOrderId + " 下未找到合同记录");
            }
            return contractQueryService.toJson(result);
        } catch (Exception e) {
            log.error("queryContractsByOrderId 失败", e);
            return toErrorJson(e.getMessage());
        }
    }

    /**
     * 根据合同编号查询 platform_instance_id
     *
     * @param contractCode 合同编号（contract_code）
     * @return JSON格式结果
     */
    @Tool(description = """
            根据合同编号（contract_code）查询合同的 platform_instance_id。
            若只需要 instanceId 而不需要版式数据时使用此工具。
            如需同时获取 form_id，请直接使用 queryContractFormId。
            contractCode 参数为合同编号字符串。""")
    public String queryContractInstanceId(String contractCode) {
        log.info("queryContractInstanceId - contractCode: {}", contractCode);
        try {
            Long instanceId = contractQueryService.queryInstanceId(contractCode);
            if (instanceId == null) {
                return toErrorJson("未找到合同编号为 " + contractCode + " 的合同记录");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("contractCode", contractCode);
            result.put("platformInstanceId", instanceId);
            return contractQueryService.toJson(result);
        } catch (Exception e) {
            log.error("查询 platform_instance_id 失败", e);
            return toErrorJson(e.getMessage());
        }
    }

    /**
     * 根据合同编号查询版式 form_id
     *
     * @param contractCode 合同编号（contract_code），如 C1772854666284956
     * @return 版式接口原始响应
     */
    @Tool(description = """
            根据合同编号（contract_code）查询合同对应的版式 form_id。
            该工具自动完成两步操作：1) 从数据库查询合同的 platform_instance_id；
            2) 以 platform_instance_id 作为 instanceId 调用版式查询接口获取 form_id。
            当用户询问"查询某合同的版式"、"查合同的form_id"、"合同编号XXX的版式是什么"时使用此工具。
            contractCode 参数为合同编号字符串，如 C1772854666284956。""")
    public String queryContractFormId(String contractCode) {
        log.info("queryContractFormId - contractCode: {}", contractCode);
        try {
            String result = contractQueryService.queryFormId(contractCode);
            if (result == null) {
                return toErrorJson("未找到合同编号为 " + contractCode + " 的合同记录，无法查询版式");
            }
            return result;
        } catch (Exception e) {
            log.error("查询合同版式失败", e);
            return toErrorJson(e.getMessage());
        }
    }

    private String toErrorJson(String message) {
        return "{\"error\":\"" + message.replace("\"", "\\\"") + "\"}";
    }
}
