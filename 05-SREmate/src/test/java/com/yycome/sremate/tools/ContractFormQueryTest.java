package com.yycome.sremate.tools;

import com.yycome.sremate.domain.EndpointParameter;
import com.yycome.sremate.domain.EndpointTemplate;
import com.yycome.sremate.service.EndpointTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 合同版式查询测试
 *
 * - 单元测试（MockitoExtension）：mock 依赖，验证串联逻辑
 * - E2E 集成测试（@Tag("e2e")）：连接真实数据库和 HTTP 接口，需要 application-local.yml 和网络
 *
 * 运行全部测试：mvn test
 * 仅运行 E2E 测试：mvn test -Dgroups=e2e
 */
class ContractFormQueryTest {

    // ─────────────────────────────────────────────────────────────
    // 单元测试
    // ─────────────────────────────────────────────────────────────

    @ExtendWith(MockitoExtension.class)
    static class Unit {

        @Mock
        private JdbcTemplate jdbcTemplate;

        @Mock
        private HttpQueryTool httpQueryTool;

        private MySQLQueryTool mySQLQueryTool;

        @BeforeEach
        void setUp() {
            mySQLQueryTool = new MySQLQueryTool(jdbcTemplate, httpQueryTool);
        }

        @Test
        void queryContractFormId_contractNotFound_returnsNotFoundMessage() {
            // Given
            when(jdbcTemplate.queryForList(anyString(), eq("NOT_EXIST"))).thenReturn(List.of());

            // When
            String result = mySQLQueryTool.queryContractFormId("NOT_EXIST");

            // Then
            assertThat(result).contains("未找到合同编号为 NOT_EXIST 的合同记录");
            verifyNoInteractions(httpQueryTool);
        }

        @Test
        void queryContractFormId_contractFound_callsHttpEndpointWithInstanceId() {
            // Given
            String contractCode = "C1772854666284956";
            long expectedInstanceId = 116894442L;
            Map<String, Object> dbRow = new HashMap<>();
            dbRow.put("platform_instance_id", expectedInstanceId);

            when(jdbcTemplate.queryForList(anyString(), eq(contractCode)))
                    .thenReturn(List.of(dbRow));
            when(httpQueryTool.callPredefinedEndpoint(eq("contract-form-data"), any()))
                    .thenReturn("{\"form_id\":\"form_abc\"}");

            // When
            String result = mySQLQueryTool.queryContractFormId(contractCode);

            // Then
            ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
            verify(httpQueryTool).callPredefinedEndpoint(eq("contract-form-data"), paramsCaptor.capture());

            Map<String, String> capturedParams = paramsCaptor.getValue();
            assertThat(capturedParams).containsEntry("instanceId", String.valueOf(expectedInstanceId));
            assertThat(result).contains("form_id");
        }

        @Test
        void queryContractFormId_instanceIdIsNull_returnsNotFoundMessage() {
            // Given
            Map<String, Object> dbRow = new HashMap<>();
            dbRow.put("platform_instance_id", null);
            when(jdbcTemplate.queryForList(anyString(), eq("C_NULL")))
                    .thenReturn(List.of(dbRow));

            // When
            String result = mySQLQueryTool.queryContractFormId("C_NULL");

            // Then
            assertThat(result).contains("未找到合同编号为 C_NULL 的合同记录");
            verifyNoInteractions(httpQueryTool);
        }

        @Test
        void queryContractInstanceId_contractFound_returnsInstanceId() {
            // Given
            String contractCode = "C1772854666284956";
            Map<String, Object> dbRow = new HashMap<>();
            dbRow.put("platform_instance_id", 116894442L);
            when(jdbcTemplate.queryForList(anyString(), eq(contractCode)))
                    .thenReturn(List.of(dbRow));

            // When
            String result = mySQLQueryTool.queryContractInstanceId(contractCode);

            // Then
            assertThat(result).contains("116894442");
            assertThat(result).contains(contractCode);
        }

        @Test
        void queryContractInstanceId_contractNotFound_returnsNotFoundMessage() {
            // Given
            when(jdbcTemplate.queryForList(anyString(), eq("NOT_EXIST"))).thenReturn(List.of());

            // When
            String result = mySQLQueryTool.queryContractInstanceId("NOT_EXIST");

            // Then
            assertThat(result).contains("未找到合同编号为 NOT_EXIST 的合同记录");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // E2E 集成测试（需要 application-local.yml + 网络访问）
    // ─────────────────────────────────────────────────────────────

    @Tag("e2e")
    @SpringBootTest
    @ActiveProfiles("local")
    static class E2E {

        @Autowired
        private MySQLQueryTool mySQLQueryTool;

        /**
         * 验证完整链路：contract_code → DB 查 platform_instance_id → HTTP 查 form_id
         *
         * 前置条件：
         *   1. 数据库可访问（application-local.yml 配置正确）
         *   2. HTTP 接口 i.nrs-sales-project.home.ke.com 可访问
         *   3. 合同编号 C1772854666284956 在库中存在
         */
        @Test
        void queryContractFormId_fullChain_returnsFormIdFromApi() {
            // When
            String result = mySQLQueryTool.queryContractFormId("C1772854666284956");

            // Then
            System.out.println("=== E2E 结果 ===\n" + result);
            assertThat(result).isNotBlank();
            assertThat(result).doesNotContain("查询失败");
            assertThat(result).doesNotContain("未找到合同编号");
        }

        /**
         * 验证 queryContractInstanceId 能从数据库取到正确的 platform_instance_id
         */
        @Test
        void queryContractInstanceId_realDb_returnsInstanceId() {
            // When
            String result = mySQLQueryTool.queryContractInstanceId("C1772854666284956");

            // Then
            System.out.println("=== DB 查询结果 ===\n" + result);
            assertThat(result).isNotBlank();
            assertThat(result).doesNotContain("未找到");
            assertThat(result).contains("platform_instance_id");
        }
    }
}
