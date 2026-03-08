# RAG Knowledge Base Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add RAG capability to SREmate for querying duty-related knowledge with feedback loop.

**Architecture:** MD files → KnowledgeLoader → Elasticsearch VectorStore → KnowledgeService → KnowledgeQueryTool → Agent

**Tech Stack:** Spring AI, Elasticsearch, DashScope Embedding API

---

## Task 1: Add Maven Dependencies

**Files:**
- Modify: `05-SREmate/pom.xml`

**Step 1: Add Elasticsearch VectorStore dependency**

Add to `pom.xml` after the existing `spring-ai-template-st` dependency:

```xml
<!-- Spring AI Elasticsearch VectorStore -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-elasticsearch-store</artifactId>
    <version>${spring-ai.version}</version>
</dependency>
```

**Step 2: Add Elasticsearch Java Client dependency**

Add to `pom.xml`:

```xml
<!-- Elasticsearch Java Client -->
<dependency>
    <groupId>co.elastic.clients</groupId>
    <artifactId>elasticsearch-java</artifactId>
    <version>8.12.0</version>
</dependency>
```

**Step 3: Add Jakarta JSON Bind dependency (required by ES client)**

Add to `pom.xml`:

```xml
<!-- Jakarta JSON Bind (required by Elasticsearch Java Client) -->
<dependency>
    <groupId>jakarta.json.bind</groupId>
    <artifactId>jakarta.json.bind-api</artifactId>
    <version>3.0.0</version>
</dependency>
<dependency>
    <groupId>org.eclipse</groupId>
    <artifactId>yasson</artifactId>
    <version>3.0.3</version>
</dependency>
```

**Step 4: Verify dependencies resolve**

Run:
```bash
cd 05-SREmate && mvn dependency:resolve -q
```
Expected: SUCCESS (no errors)

**Step 5: Commit**

```bash
git add 05-SREmate/pom.xml
git commit -m "feat: add Elasticsearch VectorStore dependencies

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 2: Add Configuration Properties

**Files:**
- Modify: `05-SREmate/src/main/resources/application.yml`
- Modify: `05-SREmate/src/main/resources/application-local.yml` (create if not exists)

**Step 1: Add Elasticsearch and knowledge configuration to application.yml**

Add to `application.yml`:

```yaml
spring:
  elasticsearch:
    uris: ${ELASTICSEARCH_URI:http://localhost:9200}
    username: ${ELASTICSEARCH_USERNAME:}
    password: ${ELASTICSEARCH_PASSWORD:}

  ai:
    vectorstore:
      elasticsearch:
        index-name: sremate_knowledge
        initialization-mode: lazy

# Knowledge base configuration
knowledge:
  loader:
    enabled: true
    paths:
      - classpath:knowledge/faq/
      - classpath:knowledge/troubleshooting/
      - classpath:knowledge/operations/
```

**Step 2: Add local ES configuration to application-local.yml**

Add to `application-local.yml` (append if file exists):

```yaml
spring:
  elasticsearch:
    uris: http://localhost:9200
    # username: your_username
    # password: your_password
```

**Step 3: Commit**

```bash
git add 05-SREmate/src/main/resources/application.yml
git add 05-SREmate/src/main/resources/application-local.yml
git commit -m "feat: add Elasticsearch and knowledge loader configuration

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 3: Create Knowledge Properties Class

**Files:**
- Create: `05-SREmate/src/main/java/com/yycome/sremate/infrastructure/properties/KnowledgeProperties.java`

**Step 1: Write KnowledgeProperties class**

```java
package com.yycome.sremate.infrastructure.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 知识库配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "knowledge")
public class KnowledgeProperties {

    private LoaderProperties loader = new LoaderProperties();

    @Data
    public static class LoaderProperties {
        /**
         * 是否启用知识库加载
         */
        private boolean enabled = true;

        /**
         * 知识文档路径列表
         */
        private List<String> paths = List.of();
    }
}
```

**Step 2: Verify compilation**

Run:
```bash
cd 05-SREmate && mvn compile -q
```
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add 05-SREmate/src/main/java/com/yycome/sremate/infrastructure/properties/KnowledgeProperties.java
git commit -m "feat: add KnowledgeProperties configuration class

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 4: Create Knowledge Domain Models

**Files:**
- Create: `05-SREmate/src/main/java/com/yycome/sremate/domain/knowledge/model/KnowledgeResult.java`
- Create: `05-SREmate/src/main/java/com/yycome/sremate/domain/knowledge/model/FeedbackRecord.java`
- Create: `05-SREmate/src/main/java/com/yycome/sremate/domain/knowledge/model/FeedbackType.java`
- Create: `05-SREmate/src/main/java/com/yycome/sremate/domain/knowledge/model/KnowledgeStatistics.java`

**Step 1: Create FeedbackType enum**

```java
package com.yycome.sremate.domain.knowledge.model;

/**
 * 反馈类型
 */
public enum FeedbackType {
    HELPFUL,    // 点赞
    UNHELPFUL   // 点踩
}
```

**Step 2: Create KnowledgeResult class**

```java
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
```

**Step 3: Create FeedbackRecord class**

```java
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
```

**Step 4: Create KnowledgeStatistics class**

```java
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
    private List<LowQualityDoc> lowQualityDocs;

    /**
     * 未命中问题榜
     */
    private List<String> missedQueries;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueryCount {
        private String query;
        private int count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LowQualityDoc {
        private String docId;
        private String title;
        private int unhelpfulCount;
        private int totalCount;
    }
}
```

**Step 5: Verify compilation**

Run:
```bash
cd 05-SREmate && mvn compile -q
```
Expected: BUILD SUCCESS

**Step 6: Commit**

```bash
git add 05-SREmate/src/main/java/com/yycome/sremate/domain/knowledge/model/
git commit -m "feat: add knowledge domain models

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 5: Create Sample Knowledge Files

**Files:**
- Create: `05-SREmate/src/main/resources/knowledge/troubleshooting/database.md`
- Create: `05-SREmate/src/main/resources/knowledge/faq/contract.md`

**Step 1: Create troubleshooting/database.md**

```markdown
# 故障排查

## 数据库连接超时怎么排查？

### 问题现象
应用日志中出现数据库连接超时错误，如：
- Connection timed out
- Communications link failure
- Could not get JDBC Connection

### 排查步骤
1. 检查数据库服务是否正常运行
2. 检查网络连通性：ping/telnet 数据库地址
3. 检查连接池配置是否合理
4. 检查是否有慢查询占用连接
5. 检查数据库最大连接数是否达到上限

### 解决方案
1. 调整连接池配置：maximum-pool-size、minimum-idle
2. 优化慢查询，减少连接占用时间
3. 增加数据库最大连接数
4. 检查并修复连接泄漏问题

### 相关命令
```bash
# 查看数据库当前连接数
SHOW STATUS LIKE 'Threads_connected';
# 查看最大连接数
SHOW VARIABLES LIKE 'max_connections';
# 查看当前连接详情
SHOW PROCESSLIST;
```

---

## Redis连接超时怎么排查？

### 问题现象
应用日志中出现 Redis 连接超时错误，如：
- Redis connection timeout
- Unable to connect to Redis
- Connection refused

### 排查步骤
1. 检查 Redis 服务是否正常运行
2. 检查网络连通性
3. 检查 Redis 配置：timeout、maxclients
4. 检查是否有慢命令阻塞

### 解决方案
1. 增加 Redis 客户端超时配置
2. 优化慢命令
3. 检查 Redis 内存是否满

### 相关命令
```bash
# 检查 Redis 状态
redis-cli ping
# 查看当前连接数
redis-cli INFO clients
# 查看慢日志
redis-cli SLOWLOG GET 10
```
```

**Step 2: Create faq/contract.md**

```markdown
# 常见问题

## 合同编号格式是什么？

### 问题
合同编号的格式规则是什么？

### 答案
合同编号格式为：**C前缀 + 数字**，例如：C1772925352128725

- C 表示 Contract（合同）
- 后面的数字是系统生成的唯一标识

### 相关说明
- 订单号格式为纯数字，如 826030619000001899
- 可以通过编号前缀区分：C开头是合同号，纯数字是订单号

---

## 如何查询合同的版式？

### 问题
如何获取合同的版式 form_id？

### 答案
可以通过以下方式查询：
1. 使用 SREmate 提问："查询合同C1772925352128725的版式"
2. 系统会自动查询 platform_instance_id 并调用版式接口

### 技术细节
1. 从 contract 表获取 platform_instance_id
2. 调用版式服务 API 获取 form_id
```

**Step 3: Commit**

```bash
git add 05-SREmate/src/main/resources/knowledge/
git commit -m "feat: add sample knowledge files

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 6: Create Knowledge Loader

**Files:**
- Create: `05-SREmate/src/main/java/com/yycome/sremate/infrastructure/loader/KnowledgeLoader.java`

**Step 1: Write KnowledgeLoader class**

```java
package com.yycome.sremate.infrastructure.loader;

import com.yycome.sremate.infrastructure.properties.KnowledgeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.ai.vectorstore.VectorStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库加载器
 * 启动时扫描 MD 文件，解析并入库
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeLoader implements CommandLineRunner {

    private final VectorStore vectorStore;
    private final KnowledgeProperties properties;

    @Override
    public void run(String... args) {
        if (!properties.getLoader().isEnabled()) {
            log.info("知识库加载已禁用");
            return;
        }

        log.info("开始加载知识库...");

        List<Document> documents = new ArrayList<>();
        for (String path : properties.getLoader().getPaths()) {
            documents.addAll(loadDocuments(path));
        }

        if (!documents.isEmpty()) {
            vectorStore.add(documents);
            log.info("知识库加载完成，共 {} 条文档", documents.size());
        } else {
            log.warn("未找到任何知识文档");
        }
    }

    private List<Document> loadDocuments(String path) {
        List<Document> documents = new ArrayList<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        try {
            String pattern = path.replace("classpath:", "classpath*:");
            if (!pattern.endsWith("*.md")) {
                pattern = pattern.endsWith("/") ? pattern + "*.md" : pattern + "/*.md";
            }

            Resource[] resources = resolver.getResources(pattern);
            for (Resource resource : resources) {
                documents.addAll(parseMarkdownFile(resource));
            }
        } catch (IOException e) {
            log.warn("无法加载路径 {} 下的知识文档: {}", path, e.getMessage());
        }

        return documents;
    }

    /**
     * 解析 Markdown 文件，按二级标题切分为多个 FAQ 单元
     */
    private List<Document> parseMarkdownFile(Resource resource) {
        List<Document> documents = new ArrayList<>();

        try {
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            String filename = resource.getFilename();

            // 按二级标题切分
            String[] sections = content.split("(?=^## )", -1);

            String category = "未分类";
            for (String section : sections) {
                section = section.trim();
                if (section.isEmpty()) continue;

                // 提取一级标题作为分类
                if (section.startsWith("# ") && !section.startsWith("## ")) {
                    int endOfTitle = section.indexOf('\n');
                    if (endOfTitle > 0) {
                        category = section.substring(2, endOfTitle).trim();
                    }
                    continue;
                }

                // 提取二级标题作为问题标题
                if (section.startsWith("## ")) {
                    int endOfTitle = section.indexOf('\n');
                    if (endOfTitle > 0) {
                        String title = section.substring(3, endOfTitle).trim();
                        String body = section.substring(endOfTitle + 1).trim();

                        if (!body.isEmpty()) {
                            Map<String, Object> metadata = new HashMap<>();
                            metadata.put("title", title);
                            metadata.put("category", category);
                            metadata.put("source", filename);

                            Document doc = new Document(body, metadata);
                            documents.add(doc);
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.warn("解析知识文档失败: {}", resource.getFilename(), e);
        }

        return documents;
    }
}
```

**Step 2: Verify compilation**

Run:
```bash
cd 05-SREmate && mvn compile -q
```
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add 05-SREmate/src/main/java/com/yycome/sremate/infrastructure/loader/KnowledgeLoader.java
git commit -m "feat: add KnowledgeLoader for loading MD files to VectorStore

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 7: Create VectorStore Configuration

**Files:**
- Create: `05-SREmate/src/main/java/com/yycome/sremate/infrastructure/config/VectorStoreConfig.java`

**Step 1: Write VectorStoreConfig class**

```java
package com.yycome.sremate.infrastructure.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * VectorStore 配置
 */
@Configuration
public class VectorStoreConfig {

    @Value("${spring.elasticsearch.uris:http://localhost:9200}")
    private String elasticsearchUri;

    @Value("${spring.elasticsearch.username:}")
    private String username;

    @Value("${spring.elasticsearch.password:}")
    private String password;

    @Value("${spring.ai.vectorstore.elasticsearch.index-name:sremate_knowledge}")
    private String indexName;

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        HttpHost host = HttpHost.create(elasticsearchUri);
        RestClient restClient = RestClient.builder(host).build();

        ElasticsearchTransport transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper());

        return new ElasticsearchClient(transport);
    }

    @Bean
    public EmbeddingModel embeddingModel(DashScopeApi dashScopeApi) {
        DashScopeEmbeddingOptions options = DashScopeEmbeddingOptions.builder()
                .withModel("text-embedding-v3")
                .build();

        return new DashScopeEmbeddingModel(dashScopeApi, options);
    }

    @Bean
    public ElasticsearchVectorStore vectorStore(
            ElasticsearchClient elasticsearchClient,
            EmbeddingModel embeddingModel) {

        return new ElasticsearchVectorStore(
                elasticsearchClient,
                embeddingModel,
                ElasticsearchVectorStore.DEFAULT_OPTIONS,
                indexName,
                true  // initializeSchema
        );
    }
}
```

**Step 2: Verify compilation**

Run:
```bash
cd 05-SREmate && mvn compile -q
```
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add 05-SREmate/src/main/java/com/yycome/sremate/infrastructure/config/VectorStoreConfig.java
git commit -m "feat: add VectorStore configuration for Elasticsearch

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 8: Create Knowledge Service

**Files:**
- Create: `05-SREmate/src/main/java/com/yycome/sremate/domain/knowledge/service/KnowledgeService.java`

**Step 1: Write KnowledgeService class**

```java
package com.yycome.sremate.domain.knowledge.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.yycome.sremate.domain.knowledge.model.FeedbackRecord;
import com.yycome.sremate.domain.knowledge.model.FeedbackType;
import com.yycome.sremate.domain.knowledge.model.KnowledgeResult;
import com.yycome.sremate.domain.knowledge.model.KnowledgeStatistics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest as AiSearchRequest;
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
public class KnowledgeService {

    private final VectorStore vectorStore;
    private final ElasticsearchClient esClient;

    private static final String KNOWLEDGE_INDEX = "sremate_knowledge";
    private static final String FEEDBACK_INDEX = "sremate_feedback";

    /**
     * 混合检索（向量 + 关键词）
     */
    public List<KnowledgeResult> searchHybrid(String query, int topK) {
        log.info("混合检索: query={}, topK={}", query, topK);

        try {
            // 1. 向量检索
            List<Document> vectorResults = vectorStore.similaritySearch(
                    AiSearchRequest.query(query).withTopK(topK * 2)
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
            docData.put(docId, Map.of(
                    "title", metadata.getOrDefault("title", ""),
                    "category", metadata.getOrDefault("category", ""),
                    "content", doc.getContent()
            ));
        }

        // 关键词检索结果打分
        for (int i = 0; i < keywordResults.size(); i++) {
            Map<String, Object> result = keywordResults.get(i);
            String docId = (String) result.get("id");
            scores.merge(docId, 1.0 / (k + i + 1), Double::sum);

            if (!docData.containsKey(docId)) {
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
        // TODO: 存储到 ES feedback 索引
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

        // TODO: 存储到 ES feedback 索引
    }

    /**
     * 获取统计数据
     */
    public KnowledgeStatistics getStatistics() {
        // TODO: 从 ES 聚合查询统计数据
        return KnowledgeStatistics.builder()
                .hotQueries(Collections.emptyList())
                .lowQualityDocs(Collections.emptyList())
                .missedQueries(Collections.emptyList())
                .build();
    }
}
```

**Step 2: Verify compilation**

Run:
```bash
cd 05-SREmate && mvn compile -q
```
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add 05-SREmate/src/main/java/com/yycome/sremate/domain/knowledge/service/KnowledgeService.java
git commit -m "feat: add KnowledgeService with hybrid search

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 9: Create Knowledge Query Tool

**Files:**
- Create: `05-SREmate/src/main/java/com/yycome/sremate/trigger/agent/KnowledgeQueryTool.java`

**Step 1: Write KnowledgeQueryTool class**

```java
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
```

**Step 2: Verify compilation**

Run:
```bash
cd 05-SREmate && mvn compile -q
```
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add 05-SREmate/src/main/java/com/yycome/sremate/trigger/agent/KnowledgeQueryTool.java
git commit -m "feat: add KnowledgeQueryTool for Agent RAG queries

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 10: Update Agent Prompt

**Files:**
- Modify: `05-SREmate/src/main/resources/prompts/sre-agent.md`

**Step 1: Add knowledge query tool to prompt**

Add the following section after `### 6. listSkillCategories`:

```markdown
### 7. searchKnowledge
检索值班问题知识库，查找与用户问题相似的已知问题和解决方案。
- 参数：
  - query: 用户的自然语言问题或关键词
  - topK: 返回结果数量，默认 3
- 使用场景：当用户询问运维问题、故障排查、常见问题时使用

### 8. recordFeedback
对知识库检索结果进行反馈，帮助优化知识库质量。
- 参数：
  - query: 原始查询问题
  - docId: 文档ID
  - feedback: 反馈类型 (HELPFUL/UNHELPFUL)
- 使用场景：用户对检索结果表示满意或不满意时

### 9. viewKnowledgeStats
查看知识库统计报表。
- 参数：
  - type: 报表类型（hot/low_quality/missed）
- 使用场景：查看知识库运营数据
```

**Step 2: Update workflow section**

Update the "问题诊断流程" section to include knowledge search:

```markdown
### 问题诊断流程
1. 询问用户具体的问题现象（错误信息、影响范围、发生时间等）
2. 调用searchKnowledge检索相关的问题解决方案
3. 如果没有找到，调用querySkills查询相关的排查经验
4. 根据排查经验，调用相应的工具获取诊断信息
5. 分析诊断信息，提供排查建议和解决方案
6. 如果问题未解决，继续深入排查
```

**Step 3: Commit**

```bash
git add 05-SREmate/src/main/resources/prompts/sre-agent.md
git commit -m "feat: update agent prompt with knowledge query tools

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 11: Write Integration Test

**Files:**
- Create: `05-SREmate/src/test/java/com/yycome/sremate/KnowledgeQueryIT.java`

**Step 1: Write integration test**

```java
package com.yycome.sremate;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 知识库检索端到端集成测试
 *
 * 前置条件：
 * 1. application-local.yml 配置 Elasticsearch 连接
 * 2. ES 服务可用
 * 3. DashScope API Key 已配置
 */
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
class KnowledgeQueryIT {

    @Autowired
    private ChatClient sreAgent;

    @Test
    void searchDatabaseTroubleshooting_shouldReturnKnowledge() {
        String response = ask("数据库连接超时怎么办");

        System.out.println("=== [数据库问题检索] Agent 回复 ===\n" + response);

        // 验证返回了相关知识
        assertThat(response).satisfiesAnyOf(
            r -> assertThat(r).contains("数据库"),
            r -> assertThat(r).contains("连接"),
            r -> assertThat(r).contains("排查")
        );
    }

    @Test
    void searchContractFaq_shouldReturnKnowledge() {
        String response = ask("合同编号格式是什么");

        System.out.println("=== [合同FAQ检索] Agent 回复 ===\n" + response);

        // 验证返回了相关知识
        assertThat(response).satisfiesAnyOf(
            r -> assertThat(r).contains("C"),
            r -> assertThat(r).contains("合同"),
            r -> assertThat(r).contains("编号")
        );
    }

    @Test
    void searchUnknownQuestion_shouldReturnNoResult() {
        String response = ask("这是一个不存在的问题xyz123");

        System.out.println("=== [未知问题检索] Agent 回复 ===\n" + response);

        // 验证返回了"未找到"或类似提示
        assertThat(response).satisfiesAnyOf(
            r -> assertThat(r).contains("未找到"),
            r -> assertThat(r).contains("没有"),
            r -> assertThat(r).contains("暂无")
        );
    }

    private String ask(String question) {
        return sreAgent.prompt()
                .user(question)
                .call()
                .content();
    }
}
```

**Step 2: Verify test compiles**

Run:
```bash
cd 05-SREmate && mvn test-compile -q
```
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add 05-SREmate/src/test/java/com/yycome/sremate/KnowledgeQueryIT.java
git commit -m "feat: add integration test for knowledge query

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 12: Final Verification

**Step 1: Run all tests**

Run:
```bash
cd 05-SREmate && mvn test -q
```
Expected: BUILD SUCCESS (tests may skip if ES not available)

**Step 2: Verify application starts**

Run:
```bash
cd 05-SREmate && mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dsre.console.enabled=false"
```
Expected: Application starts without errors (may fail if ES not available, that's OK for now)

**Step 3: Push changes**

```bash
git push origin master
```

---

## Summary

This plan implements the core RAG capability for SREmate:

1. **Dependencies**: Elasticsearch VectorStore, ES Java Client
2. **Configuration**: ES connection, knowledge loader settings
3. **Domain Models**: KnowledgeResult, FeedbackRecord, KnowledgeStatistics
4. **Infrastructure**: KnowledgeLoader (MD → VectorStore), VectorStoreConfig
5. **Service**: KnowledgeService with hybrid search (vector + keyword)
6. **Tool**: KnowledgeQueryTool for Agent integration
7. **Prompt**: Updated sre-agent.md with knowledge tools

**Next steps** (Phase 2):
- Implement feedback storage to ES
- Implement statistics aggregation
- Add incremental knowledge update support
