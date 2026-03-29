package com.yycome.sreagent.infrastructure.gateway.model;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 接口模板定义
 * 用于描述预定义接口的完整信息
 */
@Data
public class EndpointTemplate {

    /** 接口标识 */
    private String id;

    /** 接口名称 */
    private String name;

    /** 接口描述（帮助 LLM 理解） */
    private String description;

    /** 分类（system/database/monitoring） */
    private String category;

    /** URL 模板（支持占位符，如 ${host}） */
    private String urlTemplate;

    /** HTTP 方法（GET/POST） */
    private String method;

    /** 参数定义列表 */
    private List<EndpointParameter> parameters;

    /** 请求头 */
    private Map<String, String> headers;

    /** POST 请求体模板（支持 ${paramName} 占位符） */
    private String requestBodyTemplate;

    /**
     * 响应字段过滤：仅保留指定的顶层数组字段中的特定 key。
     * 格式：{ "arrayField": ["key1", "key2", ...] }
     * 例如：{ "decorateBudgetList": ["billType","billCode"], "personalBudgetList": ["billType","billCode"] }
     */
    private Map<String, List<String>> responseFields;

    /** 超时时间（秒） */
    private int timeout;

    /** 使用示例列表 */
    private List<String> examples;
}
