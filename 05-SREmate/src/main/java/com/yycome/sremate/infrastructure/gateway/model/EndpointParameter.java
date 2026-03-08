package com.yycome.sremate.infrastructure.gateway.model;

import lombok.Data;

/**
 * 接口参数定义
 * 用于描述预定义接口的参数信息
 */
@Data
public class EndpointParameter {

    /** 参数名 */
    private String name;

    /** 参数类型 (string, integer, boolean) */
    private String type;

    /** 参数描述 */
    private String description;

    /** 是否必需 */
    private boolean required;

    /** 默认值 */
    private String defaultValue;

    /** 示例值 */
    private String example;
}
