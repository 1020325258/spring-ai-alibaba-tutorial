package com.yycome.sremate.domain.knowledge.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.TermsAggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yycome.sremate.domain.knowledge.model.FeedbackRecord;
import com.yycome.sremate.domain.knowledge.model.FeedbackType;
import com.yycome.sremate.domain.knowledge.model.KnowledgeResult;
import com.yycome.sremate.domain.knowledge.model.KnowledgeStatistics;
import com.yycome.sremate.domain.knowledge.model.QueryCount;
import com.yycome.sremate.domain.knowledge.model.DocQuality;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest.Builder;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识查询服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "spring.ai.vectorstore.elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
public class KnowledgeService {

    private final VectorStore vectorStore;
    private final ElasticsearchClient esClient;
    private final ObjectMapper objectMapper;

    private static final String KNOWLEDGE_INDEX = "sremate_knowledge";
    private static final String FEEDBACK_INDEX = "sremate_feedback";
    private static final String SEARCH_LOG_INDEX = "sremate_search_log";

    /**
     * 混合检索（向量 + 关键词）
     */
    public List<KnowledgeResult> searchHybrid(String query, int topK) {
        log.info("混合检索: query={}, topK={}", query, topK);

        try {
            // 1. 向量检索
            List<Document> vectorResults = vectorStore.similaritySearch(
                    new Builder().query(query).topK(topK * 2).build()
            );

            // 2. 关键词检索 (BM25)
            List<Map<String, Object>> keywordResults = searchByKeyword(query, topK * 2);

            // 3. RRF 融合排序
            List<KnowledgeResult> merged = mergeWithRRF(vectorResults, keywordResults, topK);

            // 4. 记录检索日志
            logSearchQuery(query, merged);

            return merged;
        } catch (Exception e) {
            log.error("混合检索失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 关键词检索 (BM25)
     */
    private List<Map<String, Object>> searchByKeyword(String query, int size) throws IOException {
        SearchRequest request = SearchRequest.of(s -> s
                .index(KNOWLEDGE_INDEX)
                .size(size)
                .query(q -> q
                        .match(m -> m
                                .field("content")
                                .query(query)
                        )
                )
        );

        SearchResponse<Map> response = esClient.search(request, Map.class);

        return response.hits().hits().stream()
                .map(hit -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("id", hit.id());
                    result.put("score", hit.score());
                    result.put("source", hit.source());
                    return result;
                })
                .collect(Collectors.toList());
    }

    /**
     * RRF (Reciprocal Rank Fusion) 融合排序
     */
    private List<KnowledgeResult> mergeWithRRF(
            List<Document> vectorResults,
            List<Map<String, Object>> keywordResults,
            int topK) {

        Map<String, Double> scores = new HashMap<>();
        Map<String, Map<String, Object>> docData = new HashMap<>();
        int k = 60; // RRF 常数

        // 向量检索结果打分
        for (int i = 0; i < vectorResults.size(); i++) {
            Document doc = vectorResults.get(i);
            String docId = doc.getId();
            scores.merge(docId, 1.0 / (k + i + 1), Double::sum);

            Map<String, Object> metadata = doc.getMetadata();
            Map<String, Object> data = new HashMap<>();
            data.put("title", metadata.getOrDefault("title", ""));
            data.put("category", metadata.getOrDefault("category", ""));
            data.put("content", doc.getText());
            docData.put(docId, data);
        }

        // 关键词检索结果打分
        for (int i = 0; i < keywordResults.size(); i++) {
            Map<String, Object> result = keywordResults.get(i);
            String docId = (String) result.get("id");
            scores.merge(docId, 1.0 / (k + i + 1), Double::sum);

            if (!docData.containsKey(docId)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> source = (Map<String, Object>) result.get("source");
                if (source != null) {
                    docData.put(docId, source);
                }
            }
        }

        // 按分数排序取 topK
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> {
                    String docId = entry.getKey();
                    Map<String, Object> data = docData.getOrDefault(docId, Collections.emptyMap());
                    return KnowledgeResult.builder()
                            .id(docId)
                            .title((String) data.getOrDefault("title", ""))
                            .category((String) data.getOrDefault("category", ""))
                            .content((String) data.getOrDefault("content", ""))
                            .score(entry.getValue())
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * 记录检索日志
     */
    private void logSearchQuery(String query, List<KnowledgeResult> results) {
        log.info("检索日志 - query: {}, 结果数: {}", query, results.size());

        try {
            Map<String, Object> searchLog = new HashMap<>();
            searchLog.put("query", query);
            searchLog.put("resultCount", results.size());
            searchLog.put("timestamp", LocalDateTime.now().toString());
            if (!results.isEmpty()) {
                searchLog.put("topDocId", results.get(0).getId());
            }

            IndexRequest<Map<String, Object>> request = IndexRequest.of(i -> i
                    .index(SEARCH_LOG_INDEX)
                    .document(searchLog)
            );
            esClient.index(request);
        } catch (IOException e) {
            log.warn("记录检索日志失败: {}", e.getMessage());
        }
    }

    /**
     * 记录用户反馈
     */
    public void recordFeedback(String query, String docId, FeedbackType type) {
        log.info("记录反馈: query={}, docId={}, type={}", query, docId, type);

        FeedbackRecord record = FeedbackRecord.builder()
                .id(UUID.randomUUID().toString())
                .query(query)
                .docId(docId)
                .type(type)
                .timestamp(LocalDateTime.now())
                .build();

        try {
            IndexRequest<FeedbackRecord> request = IndexRequest.of(i -> i
                    .index(FEEDBACK_INDEX)
                    .id(record.getId())
                    .document(record)
            );
            esClient.index(request);
            log.debug("反馈已存储到ES: {}", record.getId());
        } catch (IOException e) {
            log.warn("存储反馈失败: {}", e.getMessage());
        }
    }

    /**
     * 获取统计数据
     */
    public KnowledgeStatistics getStatistics() {
        return KnowledgeStatistics.builder()
                .hotQueries(getHotQueries())
                .lowQualityDocs(getLowQualityDocs())
                .missedQueries(getMissedQueries())
                .build();
    }

    /**
     * 获取热门查询
     */
    private List<QueryCount> getHotQueries() {
        try {
            SearchRequest request = SearchRequest.of(s -> s
                    .index(SEARCH_LOG_INDEX)
                    .size(0)
                    .aggregations("hot_queries", a -> a
                            .terms(t -> t
                                    .field("query.keyword")
                                    .size(10)
                            )
                    )
            );

            SearchResponse<Void> response = esClient.search(request, Void.class);
            var buckets = response.aggregations().get("hot_queries").sterms().buckets();

            return buckets.array().stream()
                    .map(bucket -> QueryCount.builder()
                            .query(bucket.key().stringValue())
                            .count((int) bucket.docCount())
                            .build())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("获取热门查询失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取低质量文档
     */
    private List<DocQuality> getLowQualityDocs() {
        try {
            // 查询所有反馈，在 Java 中聚合计算
            SearchRequest request = SearchRequest.of(s -> s
                    .index(FEEDBACK_INDEX)
                    .size(200)
            );

            SearchResponse<Map> response = esClient.search(request, Map.class);

            // 按 docId 分组统计
            Map<String, int[]> docStats = new HashMap<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map<String, Object> source = hit.source();
                if (source == null) continue;

                String docId = (String) source.get("docId");
                String type = (String) source.get("type");

                if (docId != null) {
                    int[] stats = docStats.computeIfAbsent(docId, k -> new int[2]);
                    if ("UNHELPFUL".equals(type)) {
                        stats[0]++;
                    } else {
                        stats[1]++;
                    }
                }
            }

            // 过滤低质量文档（点踩率>30%且总反馈>2）
            return docStats.entrySet().stream()
                    .filter(e -> {
                        int unhelpful = e.getValue()[0];
                        int helpful = e.getValue()[1];
                        int total = unhelpful + helpful;
                        return total > 2 && (double) unhelpful / total > 0.3;
                    })
                    .map(e -> DocQuality.builder()
                            .docId(e.getKey())
                            .title(e.getKey())
                            .unhelpfulCount(e.getValue()[0])
                            .helpfulCount(e.getValue()[1])
                            .totalCount(e.getValue()[0] + e.getValue()[1])
                            .build())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("获取低质量文档失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取未命中查询（无结果的查询）
     */
    private List<String> getMissedQueries() {
        try {
            SearchRequest request = SearchRequest.of(s -> s
                    .index(SEARCH_LOG_INDEX)
                    .size(20)
                    .query(q -> q
                            .term(t -> t.field("resultCount").value(0))
                    )
                    .sort(sort -> sort.field(f -> f.field("timestamp").order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)))
            );

            SearchResponse<Map> response = esClient.search(request, Map.class);

            return response.hits().hits().stream()
                    .map(hit -> {
                        Map<String, Object> source = hit.source();
                        return source != null ? (String) source.get("query") : null;
                    })
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("获取未命中查询失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
