package com.yycome.sremate.domain.knowledge.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 查询计数统计
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryCount {
    /**
     * 查询内容
     */
    private String query;

    /**
     * 查询次数
     */
    private Integer count;
}
