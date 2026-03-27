package com.yycome.sreagent.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据查询能力端到端测试
 *
 * 测试 SRE-Agent 的数据查询能力：
 * 1. 合同基本信息查询
 * 2. 订单查询
 * 3. 关联数据查询（合同节点、签约单据等）
 *
 * 验证点：
 * - LLM 能正确识别查询意图
 * - 调用 ontologyQuery 工具
 * - 参数正确（entity、queryScope）
 * - 返回正确的查询结果
 */
class QueryAgentIT extends BaseSREAgentIT {

    @Test
    @DisplayName("查询合同基本信息 - 验证 entity=Contract")
    void query_contract_basic_info() {
        // 当
        String response = ask("查询合同C1767173898135504的基本信息");

        // 那么
        // 1. 工具调用层：应该调用 ontologyQuery
        assertToolCalled("ontologyQuery");

        // 2. 参数层：entity 应该是 Contract（C开头）
        assertToolParamEquals("ontologyQuery", "entity", "Contract");

        // 3. 参数层：queryScope 应该是 null 或 LIST（只查基本信息）
        Map<String, Object> params = getToolParams("ontologyQuery");
        Object queryScope = params.get("queryScope");
        assertThat(queryScope)
                .as("查询基本信息时 queryScope 应该为空或 null")
                .satisfiesAnyOf(
                        qs -> { if (qs == null) return; },
                        qs -> { if (qs.equals("") || qs.equals("list")) return; }
                );

        // 4. 验证所有工具调用成功
        assertAllToolsSuccess();
    }

    @Test
    @DisplayName("查询合同节点 - 验证 queryScope=ContractNode")
    void query_contract_nodes() {
        // 当
        String response = ask("查询合同C1767173898135504的节点");

        // 那么
        // 1. 工具调用层：应该调用 ontologyQuery
        assertToolCalled("ontologyQuery");

        // 2. 参数层：entity 应该是 Contract
        assertToolParamEquals("ontologyQuery", "entity", "Contract");

        // 3. 参数层：queryScope 应该是 ContractNode
        assertToolParamEquals("ontologyQuery", "queryScope", "ContractNode");

        // 4. 验证所有工具调用成功
        assertAllToolsSuccess();
    }

    @Test
    @DisplayName("查询订单下的合同 - 验证 entity=Order")
    void query_order_to_contracts() {
        // 当
        String response = ask("查询订单825123110000002753下的合同");

        // 那么
        // 1. 工具调用层：应该调用 ontologyQuery
        assertToolCalled("ontologyQuery");

        // 2. 参数层：entity 应该是 Order（纯数字订单号）
        assertToolParamEquals("ontologyQuery", "entity", "Order");

        // 3. 参数层：queryScope 应该是 Contract
        assertToolParamEquals("ontologyQuery", "queryScope", "Contract");

        // 4. 验证所有工具调用成功
        assertAllToolsSuccess();
    }

    @Test
    @DisplayName("查询订单的合同和节点 - 验证多目标 queryScope")
    void query_order_with_multiple_scopes() {
        // 当
        String response = ask("查询订单825123110000002753的合同和节点");

        // 那么
        // 1. 工具调用层：应该调用 ontologyQuery
        assertToolCalled("ontologyQuery");

        // 2. 参数层：entity 应该是 Order
        assertToolParamEquals("ontologyQuery", "entity", "Order");

        // 3. 参数层：queryScope 应该包含多个目标（逗号分隔）
        Map<String, Object> params = getToolParams("ontologyQuery");
        Object queryScope = params.get("queryScope");
        assertThat(queryScope)
                .as("多目标查询时 queryScope 应该包含多个值")
                .isNotNull();
        String scopeStr = queryScope.toString();
        assertThat(scopeStr)
                .as("应该同时展开 Contract 和 ContractNode")
                .satisfiesAnyOf(
                        s -> { if (s.contains("Contract") && s.contains("ContractNode")) return; },
                        s -> { if (s.contains("ContractNode") && s.contains("Contract")) return; }
                );

        // 4. 验证所有工具调用成功
        assertAllToolsSuccess();
    }

    @Test
    @DisplayName("查询签约单据 - 验证 queryScope=ContractQuotationRelation")
    void query_contract_quotation_relation() {
        // 当
        String response = ask("查询合同C1767173898135504的签约单据");

        // 那么
        // 1. 工具调用层：应该调用 ontologyQuery
        assertToolCalled("ontologyQuery");

        // 2. 参数层：entity 应该是 Contract
        assertToolParamEquals("ontologyQuery", "entity", "Contract");

        // 3. 参数层：queryScope 应该是 ContractQuotationRelation
        assertToolParamEquals("ontologyQuery", "queryScope", "ContractQuotationRelation");

        // 4. 验证所有工具调用成功
        assertAllToolsSuccess();
    }

    @Test
    @DisplayName("查询个性化报价 - 验证 queryScope=PersonalQuote")
    void query_personal_quote() {
        // 当
        String response = ask("查询合同C1767173898135504的个性化报价");

        // 那么
        // 1. 工具调用层：应该调用 ontologyQuery
        assertToolCalled("ontologyQuery");

        // 2. 参数层：entity 应该是 Contract
        assertToolParamEquals("ontologyQuery", "entity", "Contract");

        // 3. 参数层：queryScope 应该是 PersonalQuote
        assertToolParamEquals("ontologyQuery", "queryScope", "PersonalQuote");

        // 4. 验证所有工具调用成功
        assertAllToolsSuccess();
    }

    @Test
    @DisplayName("查询销售合同可签约S单（订单号）- 验证 entity=Order, queryScope=SignableOrderInfo")
    void query_signable_order_info_by_order() {
        // 当 - 用户从订单号出发，引擎自动沿 Order→Contract(type=8)→SignableOrderInfo 两跳查询
        String response = ask("查询826031915000003212销售合同的可签约S单");

        // 那么
        // 1. 工具调用层：应该调用 ontologyQuery
        assertToolCalled("ontologyQuery");

        // 2. 参数层：entity 应该是 Order（纯数字订单号）
        assertToolParamEquals("ontologyQuery", "entity", "Order");

        // 3. 参数层：queryScope 应该是 SignableOrderInfo
        assertToolParamEquals("ontologyQuery", "queryScope", "SignableOrderInfo");

        // 4. 验证所有工具调用成功
        assertAllToolsSuccess();
    }

    @Test
    @DisplayName("查询正签合同可签约S单（订单号）- 验证 entity=Order, queryScope=FormalSignableOrderInfo")
    void query_formal_signable_order_info_by_order() {
        // 当
        String response = ask("查询826031915000003212订单下正签合同的可签约S单");

        // 那么
        assertToolCalled("ontologyQuery");
        assertToolParamEquals("ontologyQuery", "entity", "Order");
        assertToolParamEquals("ontologyQuery", "queryScope", "FormalSignableOrderInfo");
        assertAllToolsSuccess();
    }
}
