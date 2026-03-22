package com.yycome.sremate.domain.ontology.gateway;

import com.yycome.sremate.infrastructure.client.HttpEndpointClient;
import com.yycome.sremate.infrastructure.dao.ContractDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonalQuoteGatewayTest {

    @Mock HttpEndpointClient httpEndpointClient;
    @Mock ContractDao contractDao;

    PersonalQuoteGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new PersonalQuoteGateway(httpEndpointClient, null, contractDao);
    }

    private Map<String, Object> parentRecord(String contractCode, String billCode, String bindType) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("contractCode", contractCode);
        record.put("billCode", billCode);
        record.put("bindType", bindType);
        return record;
    }

    // ── bindType 映射测试 ────────────────────────────────────

    @Test
    void queryByFieldWithContext_bindType1_shouldMapToBillCodeList() {
        // bindType=1 → billCodeList
        Map<String, Object> parent = parentRecord("C1767150648920281", "GBILL001", "1");

        when(contractDao.fetchContractBase("C1767150648920281"))
            .thenReturn(Map.of("projectOrderId", "826031018000004758"));

        when(httpEndpointClient.callPredefinedEndpointRaw(eq("contract-personal-data"), any()))
            .thenReturn("{\"data\":{}}");

        List<Map<String, Object>> result = gateway.queryByFieldWithContext("projectOrderId", "GBILL001", parent);

        assertThat(result).isNotEmpty();
        // 验证参数映射：billCode=GBILL001 应映射到 billCodeList
        verify(httpEndpointClient).callPredefinedEndpointRaw(eq("contract-personal-data"),
            argThat(params -> "GBILL001".equals(params.get("billCodeList"))));
    }

    @Test
    void queryByFieldWithContext_bindType2_shouldMapToChangeOrderId() {
        // bindType=2 → changeOrderId（变更单）
        Map<String, Object> parent = parentRecord("C1767150648920281", "CHG001", "2");

        when(contractDao.fetchContractBase("C1767150648920281"))
            .thenReturn(Map.of("projectOrderId", "826031018000004758"));

        when(httpEndpointClient.callPredefinedEndpointRaw(eq("contract-personal-data"), any()))
            .thenReturn("{\"data\":{}}");

        List<Map<String, Object>> result = gateway.queryByFieldWithContext("projectOrderId", "CHG001", parent);

        assertThat(result).isNotEmpty();
        // 验证参数映射：billCode=变更单号 应映射到 changeOrderId
        verify(httpEndpointClient).callPredefinedEndpointRaw(eq("contract-personal-data"),
            argThat(params -> "CHG001".equals(params.get("changeOrderId"))));
    }

    @Test
    void queryByFieldWithContext_bindType3_shouldMapToSubOrderNoList() {
        // bindType=3 → subOrderNoList（S单号）
        Map<String, Object> parent = parentRecord("C1767150648920281", "S15260312120004471", "3");

        when(contractDao.fetchContractBase("C1767150648920281"))
            .thenReturn(Map.of("projectOrderId", "826031018000004758"));

        when(httpEndpointClient.callPredefinedEndpointRaw(eq("contract-personal-data"), any()))
            .thenReturn("{\"data\":{}}");

        List<Map<String, Object>> result = gateway.queryByFieldWithContext("projectOrderId", "S15260312120004471", parent);

        assertThat(result).isNotEmpty();
        // 验证参数映射：billCode=S单号 应映射到 subOrderNoList
        verify(httpEndpointClient).callPredefinedEndpointRaw(eq("contract-personal-data"),
            argThat(params -> "S15260312120004471".equals(params.get("subOrderNoList"))));
    }

    // ── 无效 bindType 测试 ────────────────────────────────────

    @Test
    void queryByFieldWithContext_invalidBindType_shouldReturnEmptyList() {
        // bindType=0 无效
        Map<String, Object> parent = parentRecord("C1767150648920281", "GBILL001", "0");

        List<Map<String, Object>> result = gateway.queryByFieldWithContext("projectOrderId", "GBILL001", parent);

        assertThat(result).isEmpty();
        // 不应调用 HTTP 客户端
        verify(httpEndpointClient, never()).callPredefinedEndpointRaw(anyString(), any());
    }

}
