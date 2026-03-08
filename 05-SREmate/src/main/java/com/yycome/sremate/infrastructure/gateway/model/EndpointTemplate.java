package com.yycome.sremate.infrastructure.gateway.model;

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

    /** 超时时间（秒） */
    private int timeout;

    /** 使用示例列表 */
    private List<String> examples;
}
