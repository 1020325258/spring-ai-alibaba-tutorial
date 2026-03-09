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
     * 根据项目订单号查询合同列表（精简版，不查询 contract_field_sharding）
     *
     * @param projectOrderId 项目订单号
     * @return JSON格式合同列表
     */
    @Tool(description = """
            根据项目订单号（project_order_id）查询该订单下的合同列表（精简版）。
            只返回合同基本信息、节点记录（contract_node）和参与人信息（contract_user），
            不查询扩展字段（contract_field_sharding）和报价关联（contract_quotation_relation）。
            当用户询问"某订单有哪些合同"、"查询订单的合同列表"、"订单下有什么合同"时使用此工具。
            projectOrderId 参数为项目订单号字符串，格式为纯数字，如 826030619000001899。
            注意：若用户提供的编号以字母C开头（如 C1772925352128725），则该编号是合同编号而非订单号，
            此时不得调用本工具，应使用 queryContractData 工具。""")
    public String queryContractListByOrderId(String projectOrderId) {
        log.info("queryContractListByOrderId - projectOrderId: {}", projectOrderId);
        try {
            List<Map<String, Object>> result = contractQueryService.queryListByOrderId(projectOrderId);
            if (result == null) {
                return toErrorJson("订单 " + projectOrderId + " 下未找到合同记录");
            }
            return contractQueryService.toJson(result);
        } catch (Exception e) {
            log.error("queryContractListByOrderId 失败", e);
            return toErrorJson(e.getMessage());
        }
    }

    /**
     * 根据项目订单号查询所有合同完整详情（包含扩展字段和报价关联）
     *
     * @param projectOrderId 项目订单号
     * @return JSON格式聚合合同数据
     */
    @Tool(description = """
            根据项目订单号（project_order_id）查询该订单下所有合同的完整详情。
            返回每份合同的节点记录（contract_node）、参与人信息（contract_user）、
            扩展字段（contract_field_sharding，分库分表，按合同号数字部分取模10确定表后缀）
            以及报价关联（contract_quotation_relation）。
            当用户询问"订单的合同详情"、"订单下合同的完整信息"、"合同的扩展字段"时使用此工具。
            如果用户只是想知道订单有哪些合同，请使用 queryContractListByOrderId 工具（响应更快）。
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

    /**
     * 查询合同配置表（contract_city_company_info）
     *
     * @param contractOrOrderId 合同编号或订单号，自动识别格式：
     *                          - 合同编号：C前缀+数字，如 C1772925348216431
     *                          - 订单号：纯数字，如 826030619000001899
     * @param contractType 合同类型，如 "正签"、"草签"、"补充协议"。
     *                     - 使用合同编号查询时可不填（系统自动获取）
     *                     - 使用订单号查询时必须填写
     * @return JSON格式配置数据
     */
    @Tool(description = """
            查询合同配置表（contract_city_company_info）数据。
            当用户询问"合同配置表"、"配置表数据"、"合同配置"时使用此工具。

            参数说明：
            - contractOrOrderId：合同编号或订单号，直接填入用户提供的编号即可，系统会自动识别格式
              - 如果用户说"C1772925348216431的合同配置"，填入 C1772925348216431
              - 如果用户说"826030619000001899的合同配置"，填入 826030619000001899

            - contractType：合同类型名称，支持的类型包括：
              - 认购合同/认购/定金（类型1）
              - 设计合同/设计（类型2）
              - 正签合同/正签/正式合同（类型3）
              - 套餐变更合同/变更合同/套餐变更/变更（类型4）
              - 首期款合同/首期款协议/首期合同（类型5）
              - 整装首期款合同/整装首期（类型6）
              - 图纸（类型7）
              - 销售合同/销售/个性化（类型8）
              - 设计变更协议/设计变更（类型11）
              - 补充协议/补充（类型29）
              - 和解协议/和解（类型30）
              - 使用合同编号查询时：可以不填，系统自动从合同表获取
              - 使用订单号查询时：必须填写用户指定的类型名称
              - 如果用户没有指定类型，请询问用户需要查询哪种合同类型

            示例场景：
            - "C1772925348216431的合同配置表数据" → contractOrOrderId=C1772925348216431, contractType可空
            - "826030619000001899的正签合同配置" → contractOrOrderId=826030619000001899, contractType=正签
            - "826030619000001899的合同配置" → contractOrOrderId=826030619000001899, 但需要询问contractType""")
    public String queryContractConfig(String contractOrOrderId, String contractType) {
        log.info("queryContractConfig - contractOrOrderId: {}, contractType: {}", contractOrOrderId, contractType);
        try {
            // 自动识别编号类型
            String contractCode = null;
            String projectOrderId = null;

            if (contractOrOrderId != null && !contractOrOrderId.isBlank()) {
                if (contractOrOrderId.toUpperCase().startsWith("C")) {
                    contractCode = contractOrOrderId;
                } else {
                    projectOrderId = contractOrOrderId;
                }
            }

            Map<String, Object> result = contractQueryService.queryContractConfig(contractCode, projectOrderId, contractType);
            if (result == null) {
                return toErrorJson("未找到编号 " + contractOrOrderId + " 对应的合同记录");
            }
            return contractQueryService.toJson(result);
        } catch (Exception e) {
            log.error("queryContractConfig 失败", e);
            return toErrorJson(e.getMessage());
        }
    }

    private String toErrorJson(String message) {
        return "{\"error\":\"" + message.replace("\"", "\\\"") + "\"}";
    }
}
