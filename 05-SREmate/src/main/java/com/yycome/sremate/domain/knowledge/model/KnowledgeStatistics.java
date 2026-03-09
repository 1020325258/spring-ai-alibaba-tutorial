package com.yycome.sremate.domain.knowledge.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 知识库统计信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeStatistics {
    /**
     * 热门问题榜
     */
    private List<QueryCount> hotQueries;

    /**
     * 低质量知识榜
     */
    private List<DocQuality> lowQualityDocs;

    /**
     * 未命中问题榜
     */
    private List<String> missedQueries;
}
