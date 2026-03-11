package com.yycome.sremate.trigger.agent;

import com.yycome.sremate.infrastructure.annotation.DataQueryTool;
import com.yycome.sremate.infrastructure.service.ToolExecutionTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 子单查询工具
 * 负责：子单基本信息查询
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubOrderTool {

    private final HttpEndpointTool httpEndpointTool;

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
    @DataQueryTool
    public String querySubOrderInfo(String homeOrderNo, String quotationOrderNo, String projectChangeNo) {
        return ToolExecutionTemplate.execute("querySubOrderInfo", () -> {
            Map<String, String> params = new HashMap<>();
            params.put("homeOrderNo", homeOrderNo);
            params.put("quotationOrderNo", quotationOrderNo != null ? quotationOrderNo : "");
            params.put("projectChangeNo", projectChangeNo != null ? projectChangeNo : "");
            return httpEndpointTool.callPredefinedEndpoint("sub-order-info", params);
        });
    }
}
