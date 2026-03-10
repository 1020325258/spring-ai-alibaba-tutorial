package com.yycome.sremate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 合同实例 ID 和版式 form_id 查询集成测试
 */
class ContractInstanceToolIT extends BaseSREIT {

    private static final String CONTRACT_CODE = "C1767173898135504";

    @Test
    void queryContractInstanceId_shouldReturnInstanceId() {
        String response = ask(CONTRACT_CODE + "的 platform_instance_id 是多少");

        assertThat(response).doesNotContain("error");
        assertThat(response).doesNotContain("未找到");
        assertThat(response).satisfiesAnyOf(
                r -> assertThat(r).containsIgnoringCase("platformInstanceId"),
                r -> assertThat(r).containsIgnoringCase("platform_instance_id"),
                r -> assertThat(r).containsIgnoringCase("instanceId")
        );
    }

    @Test
    void queryContractFormId_shouldReturnFormId() {
        String response = ask(CONTRACT_CODE + "的版式 form_id 是多少");

        assertThat(response).doesNotContain("error");
        assertThat(response).satisfiesAnyOf(
                r -> assertThat(r).containsIgnoringCase("form_id"),
                r -> assertThat(r).containsIgnoringCase("formId"),
                r -> assertThat(r).containsIgnoringCase("版式")
        );
    }
}
