package com.yycome.sremate.trigger.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yycome.sremate.domain.contract.service.ContractQueryService;
import com.yycome.sremate.types.enums.QueryDataType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 合同查询工具（触发层，只含 @Tool 方法）
 * 负责参数解析 + 调用 ContractQueryService + 返回 JSON
 */
@Slf4j
@Component
public class ContractTool {

    private final ContractQueryService contractQueryService;
    private final HttpEndpointTool httpEndpointTool;
    private final ObjectMapper objectMapper;

    public ContractTool(ContractQueryService contractQueryService,
                        HttpEndpointTool httpEndpointTool,
                        ObjectMapper objectMapper) {
        this.contractQueryService = contractQueryService;
        this.httpEndpointTool = httpEndpointTool;
        this.objectMapper = objectMapper;
    }

    /**
     * 根据合同编号查询合同数据（支持4种查询类型）
     *
     * @param contractCode 合同编号（contract_code），C前缀+数字，如 C1772925352128725
     * @param dataType     查询类型：ALL / CONTRACT_NODE / CONTRACT_FIELD / CONTRACT_USER
     * @return JSON格式聚合数据
     */
    @Tool(description = """
            【合同编号查询】用户输入包含C前缀编号时使用。

            触发条件：编号以C开头（如C1767173898135504）

            参数：
            - contractCode：C前缀+数字（必填）
            - dataType：查询范围
              | 用户说 | dataType值 |
              |--------|-----------|
              | 合同数据/详情/信息 | ALL |
              | 节点/日志 | CONTRACT_NODE |
              | 字段 | CONTRACT_FIELD |
              | 签约人/参与人 | CONTRACT_USER |

            示例：
            - "C1767173898135504合同数据" → contractCode=C1767173898135504, dataType=ALL
            - "C1767173898135504签约人" → dataType=CONTRACT_USER

            ⚠️ 注意：纯数字是订单号，请用queryContractsByOrderId""")
    public String queryContractData(String contractCode, String dataType) {
        long start = System.currentTimeMillis();
        // 防御性校验：纯数字说明是订单号，LLM 调错了工具
        if (contractCode != null && contractCode.matches("\\d+")) {
            return toErrorJson("参数错误：" + contractCode + " 是订单号（纯数字），请使用 queryContractsByOrderId 工具查询订单下的合同");
        }
        try {
            QueryDataType type = QueryDataType.valueOf(dataType.toUpperCase());
            Map<String, Object> result = contractQueryService.queryByCode(contractCode, type);
            if (result == null) {
                log.info("[TOOL] queryContractData → {}ms, 0 rows (not found)", System.currentTimeMillis() - start);
                return toErrorJson("未找到合同编号为 " + contractCode + " 的合同记录");
            }
            String json = contractQueryService.toJson(result);
            int rows = countDataRows(result);
            log.info("[TOOL] queryContractData → {}ms, {} rows", System.currentTimeMillis() - start, rows);
            return json;
        } catch (IllegalArgumentException e) {
            log.error("[TOOL] queryContractData → {}ms, error: {}", System.currentTimeMillis() - start, e.getMessage());
            return toErrorJson("无效的 dataType 参数: " + dataType + "，可选值: ALL / CONTRACT_NODE / CONTRACT_FIELD / CONTRACT_USER");
        } catch (Exception e) {
            log.error("[TOOL] queryContractData → {}ms, error: {}", System.currentTimeMillis() - start, e.getMessage());
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
            【订单号查询】用户输入包含纯数字编号时使用。

            触发条件：编号为纯数字（如825123110000002753）

            参数：
            - projectOrderId：纯数字订单号（必填）

            示例：
            - "825123110000002753有哪些合同" → projectOrderId=825123110000002753
            - "订单825123110000002753的合同详情" → projectOrderId=825123110000002753

            ⚠️ 注意：C前缀是合同号，请用queryContractData""")
    public String queryContractsByOrderId(String projectOrderId) {
        long start = System.currentTimeMillis();
        try {
            List<Map<String, Object>> result = contractQueryService.queryByOrderId(projectOrderId);
            if (result == null) {
                log.info("[TOOL] queryContractsByOrderId → {}ms, 0 rows (not found)", System.currentTimeMillis() - start);
                return toErrorJson("订单 " + projectOrderId + " 下未找到合同记录");
            }
            String json = contractQueryService.toJson(result);
            log.info("[TOOL] queryContractsByOrderId → {}ms, {} rows", System.currentTimeMillis() - start, result.size());
            return json;
        } catch (Exception e) {
            log.error("[TOOL] queryContractsByOrderId → {}ms, error: {}", System.currentTimeMillis() - start, e.getMessage());
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
            【查询实例ID】仅查询platform_instance_id时使用。

            触发条件：用户明确问"instance_id"或"实例ID"

            参数：
            - contractCode：C前缀合同号（必填）

            示例："C1767173898135504的instance_id"

            💡 提示：如需版式form_id，直接用queryContractFormId""")
    public String queryContractInstanceId(String contractCode) {
        long start = System.currentTimeMillis();
        try {
            Long instanceId = contractQueryService.queryInstanceId(contractCode);
            if (instanceId == null) {
                log.info("[TOOL] queryContractInstanceId → {}ms, not found", System.currentTimeMillis() - start);
                return toErrorJson("未找到合同编号为 " + contractCode + " 的合同记录");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("contractCode", contractCode);
            result.put("platformInstanceId", instanceId);
            String json = contractQueryService.toJson(result);
            log.info("[TOOL] queryContractInstanceId → {}ms, 1 row", System.currentTimeMillis() - start);
            return json;
        } catch (Exception e) {
            log.error("[TOOL] queryContractInstanceId → {}ms, error: {}", System.currentTimeMillis() - start, e.getMessage());
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
            【版式查询】仅当用户提到"版式"或"form_id"时使用。

            触发条件：包含关键词"版式"、"form_id"、"版式ID"

            参数：
            - contractCode：C前缀合同号（必填）

            示例："C1767173898135504的版式form_id"

            ⚠️ 禁止：用户说"合同数据"时不能用此工具""")
    public String queryContractFormId(String contractCode) {
        long start = System.currentTimeMillis();
        try {
            String result = contractQueryService.queryFormId(contractCode);
            if (result == null) {
                log.info("[TOOL] queryContractFormId → {}ms, not found", System.currentTimeMillis() - start);
                return toErrorJson("未找到合同编号为 " + contractCode + " 的合同记录，无法查询版式");
            }
            log.info("[TOOL] queryContractFormId → {}ms, ok", System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            log.error("[TOOL] queryContractFormId → {}ms, error: {}", System.currentTimeMillis() - start, e.getMessage());
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
    public String queryContractConfig(String contractOrOrderId, String contractType) {
        long start = System.currentTimeMillis();
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
                log.info("[TOOL] queryContractConfig → {}ms, not found", System.currentTimeMillis() - start);
                return toErrorJson("未找到编号 " + contractOrOrderId + " 对应的合同记录");
            }
            String json = contractQueryService.toJson(result);
            log.info("[TOOL] queryContractConfig → {}ms, 1 row", System.currentTimeMillis() - start);
            return json;
        } catch (Exception e) {
            log.error("[TOOL] queryContractConfig → {}ms, error: {}", System.currentTimeMillis() - start, e.getMessage());
            return toErrorJson(e.getMessage());
        }
    }

    private String toErrorJson(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("error", message));
        } catch (Exception e) {
            // 降级处理：若 ObjectMapper 失败，返回简单错误
            return "{\"error\":\"" + message.replace("\"", "'") + "\"}";
        }
    }

    /**
     * 根据项目订单号查询报价单列表
     *
     * @param projectOrderId 项目订单号，纯数字格式
     * @return 过滤后的报价单列表 JSON（仅含 billType/billTypeDesc/statusDesc/billCode/originalBillCode）
     */
    @Tool(description = """
            【报价单查询】用户提到"报价单"或"报价"时使用。

            触发条件：包含关键词"报价单"、"报价"、"报价列表"

            参数：
            - projectOrderId：纯数字订单号（必填）

            示例：
            - "826031111000001859的报价单" → projectOrderId=826031111000001859
            - "查询826031111000001859报价单列表" → projectOrderId=826031111000001859

            ⚠️ 注意：报价单 ≠ 子单，不要用子单工具查报价单""")
    public String queryBudgetBillList(String projectOrderId) {
        long start = System.currentTimeMillis();
        try {
            // 1. 获取报价单列表（已过滤字段）
            String billListJson = httpEndpointTool.callPredefinedEndpoint("budget-bill-list",
                    Map.of("projectOrderId", projectOrderId));

            // 2. 逐条报价单查询子单并聚合
            JsonNode billListNode = objectMapper.readTree(billListJson);
            ObjectNode result = objectMapper.createObjectNode();

            for (String listKey : List.of("decorateBudgetList", "personalBudgetList")) {
                JsonNode list = billListNode.path(listKey);
                if (!list.isArray()) {
                    result.set(listKey, list);
                    continue;
                }
                ArrayNode enrichedList = objectMapper.createArrayNode();
                for (JsonNode bill : list) {
                    ObjectNode enrichedBill = (ObjectNode) bill.deepCopy();
                    String billCode = bill.path("billCode").asText(null);
                    enrichedBill.set("subOrders", querySubOrdersForBill(projectOrderId, billCode));
                    enrichedList.add(enrichedBill);
                }
                result.set(listKey, enrichedList);
            }

            String json = objectMapper.writeValueAsString(result);
            int billCount = countTotalBills(result);
            log.info("[TOOL] queryBudgetBillList → {}ms, {} bills", System.currentTimeMillis() - start, billCount);
            return json;
        } catch (Exception e) {
            log.error("[TOOL] queryBudgetBillList → {}ms, error: {}", System.currentTimeMillis() - start, e.getMessage());
            return toErrorJson(e.getMessage());
        }
    }

    /**
     * 查询单条报价单对应的子单列表，提取 orderNo/projectChangeNo/mdmCode/dueAmount
     */
    private ArrayNode querySubOrdersForBill(String projectOrderId, String billCode) {
        ArrayNode subOrders = objectMapper.createArrayNode();
        if (billCode == null || billCode.isBlank()) return subOrders;
        try {
            String raw = httpEndpointTool.callPredefinedEndpointRaw("sub-order-info",
                    Map.of("homeOrderNo", projectOrderId, "quotationOrderNo", billCode, "projectChangeNo", ""));
            if (raw == null) return subOrders;

            JsonNode data = objectMapper.readTree(raw).path("data");
            if (!data.isArray()) return subOrders;

            for (JsonNode item : data) {
                ObjectNode subOrder = objectMapper.createObjectNode();
                subOrder.set("orderNo", item.path("orderNo"));
                subOrder.set("projectChangeNo", item.path("projectChangeNo"));
                subOrder.set("mdmCode", item.path("mdmCode"));
                // dueAmount 不是所有接口版本都返回，缺失时不写入
                if (!item.path("dueAmount").isMissingNode()) {
                    subOrder.set("dueAmount", item.path("dueAmount"));
                }
                subOrders.add(subOrder);
            }
        } catch (Exception e) {
            log.warn("querySubOrdersForBill 失败 billCode={}: {}", billCode, e.getMessage());
        }
        return subOrders;
    }

    /**
     * 根据订单号查询子单信息
     *
     * @param homeOrderNo       订单号（必填）
     * @param quotationOrderNo  报价单号（可选）
     * @param projectChangeNo   变更单号（可选）
     * @return JSON格式子单信息
     */
    @Tool(description = """
            【子单查询】用户提到"子单"或"S单"时使用。

            触发条件：包含关键词"子单"、"S单"

            参数：
            - homeOrderNo：订单号（必填）
            - quotationOrderNo：报价单号（可选，GBILL前缀）
            - projectChangeNo：变更单号（可选）

            示例：
            - "825123110000002753的子单" → homeOrderNo=825123110000002753
            - "825123110000002753下GBILL260309110407580001的子单" → 加上quotationOrderNo""")
    public String querySubOrderInfo(String homeOrderNo, String quotationOrderNo, String projectChangeNo) {
        long start = System.currentTimeMillis();
        try {
            Map<String, String> params = new HashMap<>();
            params.put("homeOrderNo", homeOrderNo);
            params.put("quotationOrderNo", quotationOrderNo != null ? quotationOrderNo : "");
            params.put("projectChangeNo", projectChangeNo != null ? projectChangeNo : "");

            String result = httpEndpointTool.callPredefinedEndpoint("sub-order-info", params);
            log.info("[TOOL] querySubOrderInfo → {}ms, ok", System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            log.error("[TOOL] querySubOrderInfo → {}ms, error: {}", System.currentTimeMillis() - start, e.getMessage());
            return toErrorJson(e.getMessage());
        }
    }

    /**
     * 统计结果中的数据行数
     */
    private int countDataRows(Map<String, Object> result) {
        if (result == null) return 0;
        int count = 0;
        for (Object value : result.values()) {
            if (value instanceof List) {
                count += ((List<?>) value).size();
            } else if (value instanceof Map) {
                count++; // 单个对象算1行
            }
        }
        return count > 0 ? count : 1;
    }

    /**
     * 统计报价单总数
     */
    private int countTotalBills(ObjectNode result) {
        int count = 0;
        for (String listKey : List.of("decorateBudgetList", "personalBudgetList")) {
            JsonNode list = result.path(listKey);
            if (list.isArray()) {
                count += list.size();
            }
        }
        return count;
    }
}
