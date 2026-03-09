package com.yycome.sremate.domain.knowledge.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档质量统计
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocQuality {
    /**
     * 文档ID
     */
    private String docId;

    /**
     * 文档标题
     */
    private String title;

    /**
     * 有帮助次数
     */
    private Integer helpfulCount;

    /**
     * 无帮助次数
     */
    private Integer unhelpfulCount;

    /**
     * 总反馈次数
     */
    private Integer totalCount;
}
