package com.yycome.sremate.trigger.agent;

import com.yycome.sremate.infrastructure.annotation.DataQueryTool;
import com.yycome.sremate.infrastructure.client.HttpEndpointClient;
import com.yycome.sremate.infrastructure.service.ToolExecutionTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 个性化报价查询工具
 * 负责：根据订单号和单据号查询个性化报价数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersonalQuoteTool {

    private final HttpEndpointClient httpEndpointClient;

    /**
     * 根据订单号和单据号查询个性化报价数据
     *
     * @param projectOrderId  订单号（必填）
     * @param subOrderNoList  S单号列表，逗号分隔（可选）
     * @param changeOrderId   变更单号（可选）
     * @param billCodeList    报价单号列表，逗号分隔（可选）
     * @return JSON 格式个性化报价数据
     */
    @Tool(description = """
            【个性化报价查询】用户提到"个性化报价"时使用。

            触发条件：包含关键词"个性化报价"

            参数：
            - projectOrderId：纯数字订单号（必填）
            - subOrderNoList：S单号列表，逗号分隔（可选，如 S15260312120004471）
            - billCodeList：报价单号列表，逗号分隔（可选，如 GBILL260312104241050001）
            - changeOrderId：变更单号（可选，格式与订单号类似）

            约束：subOrderNoList、billCodeList、changeOrderId 至少填一个

            示例：
            - "826031210000003581下S15260312120004471的个性化报价"
              → projectOrderId=826031210000003581, subOrderNoList=S15260312120004471
            - "826031210000003581的GBILL260312104241050001个性化报价"
              → projectOrderId=826031210000003581, billCodeList=GBILL260312104241050001""")
    @DataQueryTool
    public String queryContractPersonalData(String projectOrderId,
                                            String subOrderNoList,
                                            String changeOrderId,
                                            String billCodeList) {
        return ToolExecutionTemplate.execute("queryContractPersonalData", () -> {
            boolean allEmpty = isBlank(subOrderNoList)
                    && isBlank(changeOrderId)
                    && isBlank(billCodeList);
            if (allEmpty) {
                return "请提供至少一种单据号：S单号（如 S15260312120004471）、"
                        + "报价单号（如 GBILL260312104241050001）或变更单号，以便查询个性化报价数据。";
            }
            Map<String, String> params = new HashMap<>();
            params.put("projectOrderId", projectOrderId);
            params.put("subOrderNoList",  subOrderNoList  != null ? subOrderNoList  : "");
            params.put("billCodeList",    billCodeList    != null ? billCodeList    : "");
            params.put("changeOrderId",   changeOrderId   != null ? changeOrderId   : "");
            return httpEndpointClient.callPredefinedEndpointFiltered("contract-personal-data", params);
        });
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
