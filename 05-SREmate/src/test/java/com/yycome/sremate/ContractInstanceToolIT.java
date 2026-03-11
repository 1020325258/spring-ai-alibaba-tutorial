package com.yycome.sremate;

import org.junit.jupiter.api.Test;

/**
 * 合同实例 ID 和版式 form_id 查询集成测试
 */
class ContractInstanceToolIT extends BaseSREIT {

    private static final String CONTRACT_CODE = "C1767173898135504";

    @Test
    void instanceIdKeyword_shouldCallQueryContractInstanceId() {
        ask(CONTRACT_CODE + "的 platform_instance_id 是多少");

        assertToolCalled("queryContractInstanceId");
        assertAllToolsSuccess();
    }

    @Test
    void formIdKeyword_shouldCallQueryContractFormId() {
        ask(CONTRACT_CODE + "的版式 form_id 是多少");

        assertToolCalled("queryContractFormId");
        assertAllToolsSuccess();
    }
}
