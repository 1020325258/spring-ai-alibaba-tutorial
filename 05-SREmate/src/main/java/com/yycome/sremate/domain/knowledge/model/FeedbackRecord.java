package com.yycome.sremate.domain.knowledge.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 反馈记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackRecord {
    /**
     * 记录ID
     */
    private String id;

    /**
     * 用户查询
     */
    private String query;

    /**
     * 检索到的文档ID
     */
    private String docId;

    /**
     * 反馈类型
     */
    private FeedbackType type;

    /**
     * 时间戳
     */
    private LocalDateTime timestamp;
}
