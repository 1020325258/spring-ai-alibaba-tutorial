package com.yycome.sremate.infrastructure.loader;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import com.yycome.sremate.infrastructure.properties.KnowledgeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
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
 * 支持启动时自动加载和手动触发加载
 * 依赖 Elasticsearch VectorStore，仅当启用时加载
 */
@Slf4j
@Component
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "spring.ai.vectorstore.elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
public class KnowledgeLoader implements CommandLineRunner {

    private final VectorStore vectorStore;
    private final KnowledgeProperties properties;
    private final ElasticsearchClient esClient;

    @Value("${spring.ai.vectorstore.elasticsearch.index-name:sremate_knowledge}")
    private String indexName;

    // 标记是否已加载
    private volatile boolean loaded = false;

    @Override
    public void run(String... args) {
        if (!properties.getLoader().isEnabled()) {
            log.info("知识库自动加载已禁用，可调用手动加载接口触发");
            return;
        }

        loadKnowledge();
    }

    /**
     * 手动加载知识库（全量重建）
     * @return 加载的文档数量
     */
    public int loadKnowledge() {
        log.info("开始加载知识库...");

        // 清空旧数据（全量重建）
        clearIndex();

        List<Document> documents = new ArrayList<>();
        for (String path : properties.getLoader().getPaths()) {
            documents.addAll(loadDocuments(path));
        }

        if (!documents.isEmpty()) {
            vectorStore.add(documents);
            loaded = true;
            log.info("知识库加载完成，共 {} 条文档", documents.size());
        } else {
            log.warn("未找到任何知识文档");
        }

        return documents.size();
    }

    /**
     * 检查知识库是否已加载
     */
    public boolean isLoaded() {
        return loaded;
    }

    /**
     * 清空 ES 索引，实现全量重建
     */
    private void clearIndex() {
        try {
            DeleteIndexRequest request = DeleteIndexRequest.of(d -> d.index(indexName));
            boolean deleted = esClient.indices().delete(request).acknowledged();
            if (deleted) {
                log.info("已清空知识库索引: {}", indexName);
            }
        } catch (Exception e) {
            // 索引不存在时会报错，忽略
            log.debug("清空索引失败（可能不存在）: {}", e.getMessage());
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

            log.debug("正在解析知识库路径: {} -> pattern: {}", path, pattern);
            Resource[] resources = resolver.getResources(pattern);
            log.info("路径 {} 解析到 {} 个资源文件", path, resources.length);

            for (Resource resource : resources) {
                log.debug("  - 加载文件: {}", resource.getFilename());
                List<Document> parsed = parseMarkdownFile(resource);
                log.info("  - {} 解析出 {} 条文档", resource.getFilename(), parsed.size());
                documents.addAll(parsed);
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

            // 按二级标题切分（(?m) 开启多行模式，使 ^ 匹配每行开头）
            String[] sections = content.split("(?m)(?=^## )", -1);

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
