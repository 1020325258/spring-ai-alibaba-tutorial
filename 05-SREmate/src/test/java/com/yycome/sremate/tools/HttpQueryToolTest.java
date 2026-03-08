package com.yycome.sremate.tools;

import com.yycome.sremate.infrastructure.gateway.EndpointTemplateService;
import com.yycome.sremate.infrastructure.gateway.model.EndpointParameter;
import com.yycome.sremate.infrastructure.gateway.model.EndpointTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * HttpQueryTool 单元测试
 */
@ExtendWith(MockitoExtension.class)
class HttpQueryToolTest {

    @Mock
    private EndpointTemplateService endpointTemplateService;

    private com.yycome.sremate.trigger.agent.HttpEndpointTool httpQueryTool;

    @BeforeEach
    void setUp() {
        WebClient.Builder webClientBuilder = WebClient.builder();
        httpQueryTool = new com.yycome.sremate.trigger.agent.HttpEndpointTool(webClientBuilder, endpointTemplateService);
    }

    @Test
    void listAvailableEndpoints_withNullCategory_callsServiceWithNull() {
        // Given
        String expectedDescription = "可用的接口列表:\n1. health-check - 应用健康检查";
        when(endpointTemplateService.getTemplatesDescription(null)).thenReturn(expectedDescription);

        // When
        String result = httpQueryTool.listAvailableEndpoints(null);

        // Then
        assertThat(result).isEqualTo(expectedDescription);
    }

    @Test
    void listAvailableEndpoints_withCategory_callsServiceWithCategory() {
        // Given
        String expectedDescription = "可用的接口列表:\n1. sign-order-list - 查询可签约S单列表";
        when(endpointTemplateService.getTemplatesDescription("contract")).thenReturn(expectedDescription);

        // When
        String result = httpQueryTool.listAvailableEndpoints("contract");

        // Then
        assertThat(result).isEqualTo(expectedDescription);
    }

    @Test
    void callPredefinedEndpoint_withNonExistentTemplate_returnsError() {
        // Given
        when(endpointTemplateService.getTemplate("non-existent")).thenReturn(null);

        // When
        String result = httpQueryTool.callPredefinedEndpoint("non-existent", new HashMap<>());

        // Then
        assertThat(result).contains("错误：未找到接口模板");
        assertThat(result).contains("listAvailableEndpoints");
    }

    @Test
    void callPredefinedEndpoint_withMissingRequiredParam_returnsValidationError() {
        // Given
        EndpointTemplate template = createTestTemplate();
        when(endpointTemplateService.getTemplate("test-endpoint")).thenReturn(template);
        // Mock validateParameters to throw exception
        doThrow(new IllegalArgumentException("缺少必需参数: orderId"))
                .when(endpointTemplateService).validateParameters(eq(template), any());

        // When - params is empty, missing required param
        String result = httpQueryTool.callPredefinedEndpoint("test-endpoint", new HashMap<>());

        // Then
        assertThat(result).contains("参数验证失败");
        assertThat(result).contains("缺少必需参数");
    }

    @Test
    void callPredefinedEndpoint_withNullParams_usesEmptyMap() {
        // Given
        EndpointTemplate template = createTestTemplateWithDefaults();
        Map<String, String> filledParams = new HashMap<>();
        filledParams.put("orderId", "default-order");

        when(endpointTemplateService.getTemplate("test-endpoint")).thenReturn(template);
        when(endpointTemplateService.fillDefaultValues(eq(template), any())).thenReturn(filledParams);
        when(endpointTemplateService.buildUrl(eq(template), eq(filledParams)))
                .thenReturn("http://localhost:8080/api/orders/default-order");

        // When
        String result = httpQueryTool.callPredefinedEndpoint("test-endpoint", null);

        // Then - 验证流程正确执行到构建URL阶段
        verify(endpointTemplateService).fillDefaultValues(eq(template), any());
        verify(endpointTemplateService).buildUrl(eq(template), eq(filledParams));
    }

    // Helper methods to create test templates

    private EndpointTemplate createTestTemplate() {
        EndpointTemplate template = new EndpointTemplate();
        template.setId("test-endpoint");
        template.setName("测试接口");
        template.setDescription("测试用接口");
        template.setCategory("test");
        template.setUrlTemplate("http://test.example.com/api/orders/${orderId}");
        template.setMethod("GET");
        template.setTimeout(10);

        EndpointParameter param = new EndpointParameter();
        param.setName("orderId");
        param.setType("string");
        param.setDescription("订单ID");
        param.setRequired(true);
        template.setParameters(List.of(param));

        return template;
    }

    private EndpointTemplate createTestTemplateWithDefaults() {
        EndpointTemplate template = new EndpointTemplate();
        template.setId("test-endpoint");
        template.setName("测试接口");
        template.setDescription("测试用接口");
        template.setCategory("test");
        template.setUrlTemplate("http://localhost:8080/api/orders/${orderId}");
        template.setMethod("GET");
        template.setTimeout(10);

        EndpointParameter param = new EndpointParameter();
        param.setName("orderId");
        param.setType("string");
        param.setDescription("订单ID");
        param.setRequired(false);
        param.setDefaultValue("default-order");
        template.setParameters(List.of(param));

        return template;
    }
}
