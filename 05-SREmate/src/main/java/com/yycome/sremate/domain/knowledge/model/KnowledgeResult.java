package com.yycome.sremate.domain.knowledge.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识检索结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeResult {
    /**
     * 文档ID
     */
    private String id;

    /**
     * 问题标题
     */
    private String title;

    /**
     * 分类
     */
    private String category;

    /**
     * 内容
     */
    private String content;

    /**
     * 相似度分数
     */
    private double score;
}
