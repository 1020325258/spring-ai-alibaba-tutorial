package com.yycome.sremate;

import org.junit.jupiter.api.Test;

/**
 * 合同配置表查询集成测试
 */
class ContractConfigToolIT extends BaseSREIT {

    private static final String CONTRACT_CODE = "C1767173898135504";
    private static final String PROJECT_ORDER_ID = "825123110000002753";

    @Test
    void configKeyword_shouldCallQueryContractConfig() {
        ask(CONTRACT_CODE + "的合同配置表数据");

        assertToolCalled("queryContractConfig");
        assertAllToolsSuccess();
    }

    @Test
    void orderIdWithConfigType_shouldCallQueryContractConfig() {
        ask(PROJECT_ORDER_ID + "的销售合同配置");

        assertToolCalled("queryContractConfig");
        assertAllToolsSuccess();
    }

    @Test
    void orderIdWithoutType_shouldCallQueryContractConfig() {
        ask(PROJECT_ORDER_ID + "的合同配置");

        assertToolCalled("queryContractConfig");
        assertAllToolsSuccess();
    }
}
