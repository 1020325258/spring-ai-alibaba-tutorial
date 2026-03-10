package com.yycome.sremate.domain.contract.gateway;

/**
 * 表单数据网关接口（领域层定义契约）
 * 根据 platform_instance_id 查询版式表单数据
 */
public interface FormDataGateway {

    /**
     * 根据 platform_instance_id 查询版式表单数据
     * @param instanceId 平台实例ID
     * @return 接口原始响应 JSON 字符串
     */
    String queryFormData(String instanceId);
}
