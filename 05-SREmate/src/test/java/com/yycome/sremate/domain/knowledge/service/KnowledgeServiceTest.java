package com.yycome.sremate.domain.knowledge.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yycome.sremate.domain.knowledge.model.KnowledgeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * KnowledgeService 单元测试
 * 测试 RRF 融合排序和混合检索逻辑
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ElasticsearchClient esClient;

    @Mock
    private SearchResponse<Map> searchResponse;

    @Mock
    private HitsMetadata<Map> hitsMetadata;

    private KnowledgeService knowledgeService;

    @BeforeEach
    void setUp() {
        knowledgeService = new KnowledgeService(vectorStore, esClient, new ObjectMapper());
    }

    /**
     * 测试空结果处理
     */
    @Test
    void searchHybrid_emptyResults_returnsEmptyList() throws IOException {
        // Given
        when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class)))
                .thenReturn(Collections.emptyList());
        when(esClient.search(any(SearchRequest.class), eq(Map.class)))
                .thenReturn(searchResponse);
        when(searchResponse.hits()).thenReturn(hitsMetadata);
        when(hitsMetadata.hits()).thenReturn(Collections.emptyList());

        // When
        List<KnowledgeResult> results = knowledgeService.searchHybrid("测试查询", 3);

        // Then - 空结果也是有效响应
        assertThat(results).isNotNull();
        assertThat(results).isEmpty();
    }

    /**
     * 测试 RRF 融合排序 - 重复出现的文档分数更高
     * RRF 公式: score = 1/(k + rank), k=60
     * doc2 在向量(rank=2)和关键词(rank=1)都出现，总分 = 1/62 + 1/61 ≈ 0.032
     * doc1 只在向量(rank=1)出现，总分 = 1/61 ≈ 0.016
     * 所以 doc2 应该排第一
     */
    @Test
    void mergeWithRRF_duplicateDocs_higherCombinedScore() {
        // Given - 模拟向量检索结果
        Document doc1 = new Document("doc1", "内容1", Map.of("title", "标题1", "category", "分类1"));
        Document doc2 = new Document("doc2", "内容2", Map.of("title", "标题2", "category", "分类2"));
        List<Document> vectorResults = List.of(doc1, doc2);

        // 模拟关键词检索结果（doc2 排第一）
        List<Map<String, Object>> keywordResults = List.of(
                Map.of("id", "doc2", "score", 1.0, "source", Map.of("title", "标题2", "content", "内容2")),
                Map.of("id", "doc3", "score", 0.8, "source", Map.of("title", "标题3", "content", "内容3"))
        );

        // When
        List<KnowledgeResult> merged = invokeMergeWithRRF(vectorResults, keywordResults, 3);

        // Then - doc2 在两个列表都出现，RRF 分数应该最高
        assertThat(merged).hasSize(3);
        assertThat(merged.get(0).getId()).isEqualTo("doc2");
        assertThat(merged.get(0).getScore()).isGreaterThan(merged.get(1).getScore());
    }

    /**
     * 测试 RRF 融合排序 - 重复文档合并分数
     */
    @Test
    void mergeWithRRF_duplicateDocs_mergeScores() {
        // Given - 同一文档在两个检索中都出现
        Document doc1 = new Document("doc1", "内容1", Map.of("title", "标题1", "category", "分类1"));
        List<Document> vectorResults = List.of(doc1);

        List<Map<String, Object>> keywordResults = List.of(
                Map.of("id", "doc1", "score", 1.0, "source", Map.of("title", "标题1", "content", "内容1"))
        );

        // When
        List<KnowledgeResult> merged = invokeMergeWithRRF(vectorResults, keywordResults, 3);

        // Then - doc1 出现两次，RRF 分数应该累加
        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).getId()).isEqualTo("doc1");
        // RRF(60, doc1在向量排1) + RRF(60, doc1在关键词排1) = 1/61 + 1/61 ≈ 0.033
        assertThat(merged.get(0).getScore()).isGreaterThan(0.03);
    }

    /**
     * 测试 RRF 融合排序 - 限制返回数量
     */
    @Test
    void mergeWithRRF_moreResultsThanTopK_returnsLimited() {
        // Given - 5 个向量结果
        List<Document> vectorResults = List.of(
                new Document("doc1", "内容1", Map.of("title", "标题1", "category", "分类1")),
                new Document("doc2", "内容2", Map.of("title", "标题2", "category", "分类2")),
                new Document("doc3", "内容3", Map.of("title", "标题3", "category", "分类3")),
                new Document("doc4", "内容4", Map.of("title", "标题4", "category", "分类4")),
                new Document("doc5", "内容5", Map.of("title", "标题5", "category", "分类5"))
        );

        List<Map<String, Object>> keywordResults = Collections.emptyList();

        // When
        List<KnowledgeResult> merged = invokeMergeWithRRF(vectorResults, keywordResults, 3);

        // Then - 只返回 topK 个结果
        assertThat(merged).hasSize(3);
    }

    /**
     * 使用反射调用 private 方法进行测试
     */
    @SuppressWarnings("unchecked")
    private List<KnowledgeResult> invokeMergeWithRRF(
            List<Document> vectorResults,
            List<Map<String, Object>> keywordResults,
            int topK) {
        try {
            var method = KnowledgeService.class.getDeclaredMethod(
                    "mergeWithRRF", List.class, List.class, int.class);
            method.setAccessible(true);
            return (List<KnowledgeResult>) method.invoke(
                    knowledgeService, vectorResults, keywordResults, topK);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke mergeWithRRF", e);
        }
    }
}
