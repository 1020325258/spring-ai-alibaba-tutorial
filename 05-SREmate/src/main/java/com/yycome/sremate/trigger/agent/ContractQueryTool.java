package com.yycome.sremate.trigger.agent;

import com.yycome.sremate.domain.contract.service.ContractQueryService;
import com.yycome.sremate.infrastructure.annotation.DataQueryTool;
import com.yycome.sremate.infrastructure.service.ToolExecutionTemplate;
import com.yycome.sremate.infrastructure.service.ToolResult;
import com.yycome.sremate.types.enums.QueryDataType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 合同数据查询工具
 * 负责：合同基础数据、实例ID、版式、配置表的查询
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractQueryTool {

    private final ContractQueryService contractQueryService;
    private final ObjectMapper objectMapper;

    /**
     * 根据合同编号查询合同数据（支持4种查询类型）
     * @deprecated 推荐使用更精确的单表查询工具：queryContractBasic、queryContractNodes、queryContractSignedObjects、queryContractFields
     */
    @Deprecated
    @Tool(description = """
            【已废弃】请优先使用专用工具：
            - 合同基本信息 → queryContractBasic
            - 合同节点 → queryContractNodes
            - 签约单据 → queryContractSignedObjects
            - 合同字段 → queryContractFields
            - 签约人/参与人 → queryContractData(dataType=CONTRACT_USER)

            仅在需要查询签约人信息时使用本工具。
            参数：contractCode（C前缀）、dataType（仅CONTRACT_USER有效）""")
    @DataQueryTool
    public String queryContractData(String contractCode, String dataType) {
        return ToolExecutionTemplate.execute("queryContractData", () -> {
            // 防御性校验：纯数字说明是订单号，LLM 调错了工具
            if (contractCode != null && contractCode.matches("\\d+")) {
                return ToolResult.error("参数错误：" + contractCode + " 是订单号（纯数字），请使用 queryContractsByOrderId 工具查询订单下的合同");
            }
            QueryDataType type = QueryDataType.valueOf(dataType.toUpperCase());
            Map<String, Object> result = contractQueryService.queryByCode(contractCode, type);
            if (result == null) {
                return ToolResult.notFound("合同", contractCode);
            }
            return contractQueryService.toJson(result);
        });
    }

    /**
     * 根据项目订单号查询所有合同完整详情
     */
    @Tool(description = """
            【订单号查询】用户输入包含纯数字编号时使用。

            触发条件：编号为纯数字（如825123110000002753）

            参数：
            - projectOrderId：纯数字订单号（必填）

            示例：
            - "825123110000002753有哪些合同" → projectOrderId=825123110000002753
            - "订单825123110000002753的合同详情" → projectOrderId=825123110000002753

            注意：C前缀是合同号，请用queryContractData""")
    @DataQueryTool
    public String queryContractsByOrderId(String projectOrderId) {
        return ToolExecutionTemplate.execute("queryContractsByOrderId", () -> {
            List<Map<String, Object>> result = contractQueryService.queryByOrderId(projectOrderId);
            if (result == null) {
                return ToolResult.error("订单 " + projectOrderId + " 下未找到合同记录");
            }
            return contractQueryService.toJson(result);
        });
    }

    /**
     * 根据合同编号查询版式 form_id
     */
    @Tool(description = """
            【版式查询】仅当用户提到"版式"或"form_id"时使用。

            触发条件：包含关键词"版式"、"form_id"、"版式ID"

            参数：
            - contractCode：C前缀合同号（必填）

            示例："C1767173898135504的版式form_id"

            禁止：用户说"合同数据"时不能用此工具""")
    @DataQueryTool
    public String queryContractFormId(String contractCode) {
        return ToolExecutionTemplate.execute("queryContractFormId", () -> {
            String result = contractQueryService.queryFormId(contractCode);
            if (result == null) {
                return ToolResult.error("未找到合同编号为 " + contractCode + " 的合同记录，无法查询版式");
            }
            return result;
        });
    }

    /**
     * 查询合同配置表
     */
    @Tool(description = """
            【合同配置表查询】用户提到"配置表"或"合同配置"时使用。

            触发条件：包含关键词"配置表"、"合同配置"

            参数：
            - contractOrOrderId：合同号或订单号（自动识别格式）
            - contractType：合同类型（订单号查询时必填）

            支持的类型：认购(1)、设计(2)、正签(3)、套餐变更(4)、首期款(5)、
            整装首期(6)、图纸(7)、销售(8)、设计变更(11)、补充协议(29)、和解(30)

            示例：
            - "C1767173898135504的配置表" → contractOrOrderId=C1767173898135504, contractType可空
            - "825123110000002753的销售合同配置" → contractOrOrderId=825123110000002753, contractType=销售""")
    @DataQueryTool
    public String queryContractConfig(String contractOrOrderId, String contractType) {
        return ToolExecutionTemplate.execute("queryContractConfig", () -> {
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
                return ToolResult.error("未找到编号 " + contractOrOrderId + " 对应的合同记录");
            }
            return contractQueryService.toJson(result);
        });
    }

    // ── 本体论驱动的单表查询工具方法 ─────────────────────────────

    @Tool(description = "查询合同主表基础数据。触发词：合同基本信息、合同详情、合同状态。参数：contractCode（C前缀合同号，必填）")
    @DataQueryTool
    public String queryContractBasic(String contractCode) {
        return ToolExecutionTemplate.execute("queryContractBasic", () -> {
            Map<String, Object> result = contractQueryService.queryContractBasic(contractCode);
            if (result == null) return ToolResult.notFound("合同", contractCode);
            return contractQueryService.toJson(result);
        });
    }

    @Tool(description = "查询合同节点/流程节点数据。触发词：合同节点、合同流程、节点记录。参数：contractCode（C前缀合同号，必填）")
    @DataQueryTool
    public String queryContractNodes(String contractCode) {
        return ToolExecutionTemplate.execute("queryContractNodes", () -> {
            List<Map<String, Object>> result = contractQueryService.queryContractNodes(contractCode);
            return contractQueryService.toJson(result);
        });
    }

    @Tool(description = "查询合同签约的单据对象（contract_quotation_relation），包含报价单和S单的bill_code。触发词：签约单据、合同关联单据、合同签约对象。参数：contractCode（C前缀合同号，必填）")
    @DataQueryTool
    public String queryContractSignedObjects(String contractCode) {
        return ToolExecutionTemplate.execute("queryContractSignedObjects", () -> {
            List<Map<String, Object>> result = contractQueryService.queryContractSignedObjects(contractCode);
            return contractQueryService.toJson(result);
        });
    }

    @Tool(description = "查询合同扩展字段数据（contract_field_sharding分表）。触发词：合同字段、合同扩展字段。参数：contractCode（C前缀合同号，必填）")
    @DataQueryTool
    public String queryContractFields(String contractCode) {
        return ToolExecutionTemplate.execute("queryContractFields", () -> {
            Map<String, Object> result = contractQueryService.queryContractFields(contractCode);
            return contractQueryService.toJson(result);
        });
    }
}
