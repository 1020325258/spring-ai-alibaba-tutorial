package com.yycome.sremate.trigger.agent;

import com.yycome.sremate.domain.knowledge.model.FeedbackType;
import com.yycome.sremate.domain.knowledge.model.KnowledgeResult;
import com.yycome.sremate.domain.knowledge.model.KnowledgeStatistics;
import com.yycome.sremate.domain.knowledge.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 知识查询工具（触发层）
 */
@Slf4j
@Component
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "spring.ai.vectorstore.elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
public class KnowledgeQueryTool {

    private final KnowledgeService knowledgeService;

    @Tool(description = """
            检索值班问题知识库，查找与用户问题相似的已知问题和解决方案。
            当用户询问运维问题、故障排查、常见问题时使用此工具。

            参数：
            - query: 用户的自然语言问题或关键词
            - topK: 返回结果数量，默认 3

            返回：匹配的知识条目，包含问题和解决方案
            """)
    public String searchKnowledge(String query,
                                   @ToolParam(required = false, description = "返回结果数量，默认3") Integer topK) {
        log.info("searchKnowledge - query: {}, topK: {}", query, topK);

        int k = (topK != null && topK > 0) ? topK : 3;
        List<KnowledgeResult> results = knowledgeService.searchHybrid(query, k);

        if (results.isEmpty()) {
            return "未找到相关知识条目";
        }

        return formatResults(results);
    }

    @Tool(description = """
            对知识库检索结果进行反馈，帮助优化知识库质量。

            参数：
            - query: 原始查询问题
            - docId: 文档ID
            - feedback: 反馈类型 (HELPFUL/UNHELPFUL)
            """)
    public String recordFeedback(String query, String docId, String feedback) {
        log.info("recordFeedback - query: {}, docId: {}, feedback: {}", query, docId, feedback);

        try {
            FeedbackType type = FeedbackType.valueOf(feedback.toUpperCase());
            knowledgeService.recordFeedback(query, docId, type);
            return "反馈已记录";
        } catch (IllegalArgumentException e) {
            return "无效的反馈类型，请使用 HELPFUL 或 UNHELPFUL";
        }
    }

    @Tool(description = """
            查看知识库统计报表，包括热门问题、低质量知识等。

            参数：
            - type: 报表类型
              - hot: 热门问题榜
              - low_quality: 低质量知识榜
              - missed: 未命中问题榜

            返回：对应类型的统计数据
            """)
    public String viewKnowledgeStats(String type) {
        log.info("viewKnowledgeStats - type: {}", type);

        KnowledgeStatistics stats = knowledgeService.getStatistics();

        return switch (type.toLowerCase()) {
            case "hot" -> formatHotQueries(stats);
            case "low_quality" -> formatLowQualityDocs(stats);
            case "missed" -> formatMissedQueries(stats);
            default -> "未知的报表类型，可选: hot, low_quality, missed";
        };
    }

    private String formatResults(List<KnowledgeResult> results) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            KnowledgeResult r = results.get(i);
            sb.append("【").append(i + 1).append("】").append(r.getTitle()).append("\n");
            sb.append("分类: ").append(r.getCategory()).append("\n");
            sb.append("相关度: ").append(String.format("%.3f", r.getScore())).append("\n");
            sb.append("---\n").append(r.getContent()).append("\n");
            sb.append("文档ID: ").append(r.getId()).append("\n");
            sb.append("\n");
        }
        return sb.toString();
    }

    private String formatHotQueries(KnowledgeStatistics stats) {
        if (stats.getHotQueries().isEmpty()) {
            return "暂无数据";
        }
        StringBuilder sb = new StringBuilder("=== 热门问题榜 ===\n");
        for (int i = 0; i < stats.getHotQueries().size(); i++) {
            var qc = stats.getHotQueries().get(i);
            sb.append(i + 1).append(". ").append(qc.getQuery())
              .append(" (").append(qc.getCount()).append("次)\n");
        }
        return sb.toString();
    }

    private String formatLowQualityDocs(KnowledgeStatistics stats) {
        if (stats.getLowQualityDocs().isEmpty()) {
            return "暂无数据";
        }
        StringBuilder sb = new StringBuilder("=== 低质量知识榜 ===\n");
        for (int i = 0; i < stats.getLowQualityDocs().size(); i++) {
            var doc = stats.getLowQualityDocs().get(i);
            sb.append(i + 1).append(". ").append(doc.getTitle())
              .append(" (点踩率: ").append(doc.getUnhelpfulCount())
              .append("/").append(doc.getTotalCount()).append(")\n");
        }
        return sb.toString();
    }

    private String formatMissedQueries(KnowledgeStatistics stats) {
        if (stats.getMissedQueries().isEmpty()) {
            return "暂无数据";
        }
        StringBuilder sb = new StringBuilder("=== 未命中问题榜 ===\n");
        for (int i = 0; i < stats.getMissedQueries().size(); i++) {
            sb.append(i + 1).append(". ").append(stats.getMissedQueries().get(i)).append("\n");
        }
        return sb.toString();
    }
}
