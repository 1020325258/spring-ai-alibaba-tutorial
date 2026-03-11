package com.yycome.sremate.infrastructure.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记工具为数据查询工具
 *
 * 被标记的工具结果会直接输出，跳过 LLM 最终回答生成。
 * 使用注解替代白名单，新增工具时无需修改 Aspect。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataQueryTool {
}
