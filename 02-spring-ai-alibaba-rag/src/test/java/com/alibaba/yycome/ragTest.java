package com.alibaba.yycome;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetriever;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetrieverOptions;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class ragTest {

    Logger logger = LoggerFactory.getLogger(ragTest.class);

    @Test
    public void test_rag_retrieval() throws GraphStateException {
        DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(System.getenv("AI_DASHSCOPE_API_KEY")).build();
        DashScopeDocumentRetriever documentRetriever = new DashScopeDocumentRetriever(dashScopeApi, DashScopeDocumentRetrieverOptions.builder()
                // IndexName 就是数据库的名称
                .withIndexName("面试解析生成")
                // 最小相关性分数阈值
                .withRerankMinScore(0.6f)
                // 不重写结果
                .withEnableRewrite(false)
                .build());
        List<Document> retrieve = documentRetriever.retrieve(new Query("sql的更新流程"));
        for (Document document : retrieve) {
            logger.info(document.getText());
        }
    }
}
