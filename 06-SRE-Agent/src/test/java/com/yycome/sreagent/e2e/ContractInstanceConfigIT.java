package com.yycome.sreagent.e2e;

import org.junit.jupiter.api.Test;

/**
 * ContractInstance、ContractConfig 和 ContractUser 查询场景测试
 *
 * <p>验证 LLM 能正确选择 entity 和 queryScope 参数：
 * <ul>
 *   <li>"C开头的合同实例" → entity=Contract, queryScope=ContractInstance</li>
 *   <li>"C开头的合同配置" → entity=Contract, queryScope=ContractConfig</li>
 *   <li>"C开头的签约人" → entity=Contract, queryScope=ContractUser</li>
 * </ul>
 *
 * @see BaseSREAgentIT
 */
class ContractInstanceConfigIT extends BaseSREAgentIT {

    /**
     * 验证"查询C1775588596710124合同实例"能正确选择参数。
     * 期望：entity=Contract, queryScope=ContractInstance
     */
    @Test
    void contractInstance_shouldUseContractEntityAndContractInstanceScope() {
        ask("查询C1775588596710124合同实例");

        // 验证工具调用
        assertToolCalled("ontologyQuery");
        assertToolParamEquals("ontologyQuery", "entity", "Contract");
        assertToolParamEquals("ontologyQuery", "queryScope", "ContractInstance");
        assertAllToolsSuccess();
    }

    /**
     * 验证"查询C1775588596710124合同配置"能正确选择参数。
     * 期望：entity=Contract, queryScope=ContractConfig
     */
    @Test
    void contractConfig_shouldUseContractEntityAndContractConfigScope() {
        ask("查询C1775588596710124合同配置");

        // 验证工具调用
        assertToolCalled("ontologyQuery");
        assertToolParamEquals("ontologyQuery", "entity", "Contract");
        assertToolParamEquals("ontologyQuery", "queryScope", "ContractConfig");
        assertAllToolsSuccess();
    }

    /**
     * 验证"查询C1767150648920281签约人"能正确选择参数。
     * 期望：entity=Contract, queryScope=ContractUser
     */
    @Test
    void contractUser_shouldUseContractEntityAndContractUserScope() {
        ask("查询C1767150648920281签约人");

        // 验证工具调用
        assertToolCalled("ontologyQuery");
        assertToolParamEquals("ontologyQuery", "entity", "Contract");
        assertToolParamEquals("ontologyQuery", "queryScope", "ContractUser");
        assertAllToolsSuccess();
    }
}
