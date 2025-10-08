package com.alibaba.yycome.config;

import com.alibaba.cloud.ai.advisor.DocumentRetrievalAdvisor;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetriever;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetrieverOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.util.List;

@Configuration
public class AgentConfiguration {

    @Bean
    public DashScopeApi dashScopeApi() {
        return DashScopeApi.builder().apiKey(System.getenv("AI_DASHSCOPE_API_KEY")).build();
    }

    /**
     * 集成百炼的 RAG
     * @param builder
     * @param dashScopeApi
     * @return
     */
    @Bean
    public ChatClient ragAgent(ChatClient.Builder builder, DashScopeApi dashScopeApi) {
        DashScopeDocumentRetriever documentRetriever = new DashScopeDocumentRetriever(dashScopeApi, DashScopeDocumentRetrieverOptions.builder()
                .withIndexName("面试解析生成")
                // 最小相关性分数阈值
                .withRerankMinScore(0.6f)
                .withEnableRewrite(true)
                .build());
        return builder.defaultAdvisors(List.of(new DocumentRetrievalAdvisor(documentRetriever), new SimpleLoggerAdvisor())).build();
    }
}
