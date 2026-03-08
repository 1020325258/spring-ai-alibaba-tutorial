package com.yycome.sremate.service;

import com.yycome.sremate.infrastructure.gateway.EndpointTemplateService;
import com.yycome.sremate.infrastructure.gateway.model.EndpointTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EndpointTemplateService 单元测试
 */
class EndpointTemplateServiceTest {

    private com.yycome.sremate.infrastructure.gateway.EndpointTemplateService endpointTemplateService;

    @BeforeEach
    void setUp() {
        endpointTemplateService = new com.yycome.sremate.infrastructure.gateway.EndpointTemplateService();
        endpointTemplateService.init();
    }

    @Test
    void init_shouldLoadAllTemplates() {
        // 验证模板加载
        List<EndpointTemplate> templates = endpointTemplateService.getAllTemplates();
        assertThat(templates).isNotEmpty();
    }

    @Test
    void getTemplate_withValidId_returnsTemplate() {
        // 测试获取 health-check 模板
        EndpointTemplate template = endpointTemplateService.getTemplate("health-check");
        assertThat(template).isNotNull();
        assertThat(template.getName()).isEqualTo("应用健康检查");
        assertThat(template.getCategory()).isEqualTo("system");
        assertThat(template.getMethod()).isEqualTo("GET");
    }

    @Test
    void getTemplate_withInvalidId_returnsNull() {
        EndpointTemplate template = endpointTemplateService.getTemplate("non-existent-id");
        assertThat(template).isNull();
    }

    @Test
    void getTemplatesByCategory_withSystemCategory_returnsSystemTemplates() {
        List<EndpointTemplate> templates = endpointTemplateService.getTemplatesByCategory("system");
        assertThat(templates).isNotEmpty();
        assertThat(templates).allMatch(t -> "system".equals(t.getCategory()));
    }

    @Test
    void getTemplatesByCategory_withContractCategory_returnsContractTemplates() {
        List<EndpointTemplate> templates = endpointTemplateService.getTemplatesByCategory("contract");
        assertThat(templates).isNotEmpty();
        assertThat(templates).allMatch(t -> "contract".equals(t.getCategory()));
        assertThat(templates).anyMatch(t -> "sign-order-list".equals(t.getId()));
    }

    @Test
    void getTemplatesDescription_shouldReturnFormattedDescription() {
        String description = endpointTemplateService.getTemplatesDescription();
        assertThat(description).contains("可用的接口列表");
        assertThat(description).contains("health-check");
        assertThat(description).contains("sign-order-list");
    }

    @Test
    void getTemplatesDescription_withCategory_returnsOnlyCategoryTemplates() {
        String description = endpointTemplateService.getTemplatesDescription("contract");
        assertThat(description).contains("sign-order-list");
        assertThat(description).doesNotContain("health-check");
    }

    @Test
    void buildUrl_withSimpleParams_replacesPlaceholders() {
        EndpointTemplate template = endpointTemplateService.getTemplate("health-check");
        Map<String, String> params = new HashMap<>();
        params.put("host", "localhost");
        params.put("port", "8080");

        String url = endpointTemplateService.buildUrl(template, params);

        assertThat(url).isEqualTo("http://localhost:8080/actuator/health");
    }

    @Test
    void buildUrl_withDefaultValue_usesDefaultWhenParamMissing() {
        EndpointTemplate template = endpointTemplateService.getTemplate("metrics");
        Map<String, String> params = new HashMap<>();
        params.put("host", "localhost");
        params.put("port", "8080");
        // metricName 有默认值 jvm.memory.used

        String url = endpointTemplateService.buildUrl(template, params);

        assertThat(url).isEqualTo("http://localhost:8080/actuator/metrics/jvm.memory.used");
    }

    @Test
    void buildUrl_withAllParams_usesProvidedParams() {
        EndpointTemplate template = endpointTemplateService.getTemplate("metrics");
        Map<String, String> params = new HashMap<>();
        params.put("host", "192.168.1.100");
        params.put("port", "9090");
        params.put("metricName", "process.cpu.usage");

        String url = endpointTemplateService.buildUrl(template, params);

        assertThat(url).isEqualTo("http://192.168.1.100:9090/actuator/metrics/process.cpu.usage");
    }

    @Test
    void buildUrl_forContractEndpoint_buildsCorrectUrl() {
        EndpointTemplate template = endpointTemplateService.getTemplate("sign-order-list");
        Map<String, String> params = new HashMap<>();
        params.put("projectOrderId", "826022518000001562");

        String url = endpointTemplateService.buildUrl(template, params);

        assertThat(url).contains("projectOrderId=826022518000001562");
        assertThat(url).contains("/home/contract/getSignOrderList/V3");
    }

    @Test
    void validateParameters_withAllRequiredParams_succeeds() {
        EndpointTemplate template = endpointTemplateService.getTemplate("health-check");
        Map<String, String> params = new HashMap<>();
        params.put("host", "localhost");
        params.put("port", "8080");

        // 不应该抛出异常
        endpointTemplateService.validateParameters(template, params);
    }

    @Test
    void validateParameters_withMissingRequiredParam_throwsException() {
        EndpointTemplate template = endpointTemplateService.getTemplate("health-check");
        Map<String, String> params = new HashMap<>();
        params.put("host", "localhost");
        // 缺少 port 参数

        assertThatThrownBy(() -> endpointTemplateService.validateParameters(template, params))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("缺少必需参数");
    }

    @Test
    void validateParameters_withOptionalParamMissing_succeeds() {
        EndpointTemplate template = endpointTemplateService.getTemplate("sign-order-list");
        Map<String, String> params = new HashMap<>();
        params.put("projectOrderId", "826022518000001562");
        // host 是可选的，有默认值

        // 不应该抛出异常
        endpointTemplateService.validateParameters(template, params);
    }

    @Test
    void fillDefaultValues_withMissingOptionalParams_fillsDefaults() {
        EndpointTemplate template = endpointTemplateService.getTemplate("sign-order-list");
        Map<String, String> params = new HashMap<>();
        params.put("projectOrderId", "826022518000001562");

        Map<String, String> filled = endpointTemplateService.fillDefaultValues(template, params);

        assertThat(filled.get("projectOrderId")).isEqualTo("826022518000001562");
        assertThat(filled.get("host")).isEqualTo("utopia-nrs-sales-project.nrs-escrow.ttb.test.ke.com");
    }

    @Test
    void fillDefaultValues_withProvidedParams_keepsProvidedValues() {
        EndpointTemplate template = endpointTemplateService.getTemplate("sign-order-list");
        Map<String, String> params = new HashMap<>();
        params.put("projectOrderId", "826022518000001562");
        params.put("host", "custom.host.com");

        Map<String, String> filled = endpointTemplateService.fillDefaultValues(template, params);

        assertThat(filled.get("host")).isEqualTo("custom.host.com");
    }
}
