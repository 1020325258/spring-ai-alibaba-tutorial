package com.yycome.sremate.trigger.agent;

import com.yycome.sremate.domain.contract.gateway.ContractFormGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 合同版式网关实现（触发层适配）
 * 委托给 HttpEndpointTool 调用预定义接口
 */
@Component
@RequiredArgsConstructor
public class ContractFormGatewayImpl implements ContractFormGateway {

    private final HttpEndpointTool httpEndpointTool;

    @Override
    public String queryFormData(String instanceId) {
        Map<String, String> params = new HashMap<>();
        params.put("instanceId", instanceId);
        return httpEndpointTool.callPredefinedEndpoint("contract-form-data", params);
    }
}
