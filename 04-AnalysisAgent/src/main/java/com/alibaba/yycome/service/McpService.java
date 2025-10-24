package com.alibaba.yycome.service;

import com.alibaba.yycome.entity.SearchResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.WebFluxSseClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
public class McpService {

    private final Logger logger = LoggerFactory.getLogger(McpService.class);

    private final McpClientTransport transport;

    private final McpSyncClient mcpSyncClient;

    public McpService() {
        // 初始化与 McpServer 的 SSE 传输通道
        this.transport = new WebFluxSseClientTransport(
                WebClient.builder().defaultHeader("Authorization", "Bearer " + System.getenv("AI_DASHSCOPE_API_KEY"))
                        .baseUrl("https://dashscope.aliyuncs.com"), new ObjectMapper(), "/api/v1/mcps/zhipu-websearch/sse");
        // 初始化 McpClient
        this.mcpSyncClient = McpClient.sync(transport).build();
        // 初始化与 MCP Server 的连接
        mcpSyncClient.initialize();
    }

    public List<SearchResult> query(String query) {

        logger.info("McpService query: " + query);

        // 列出并展示可用的工具
//        McpSchema.ListToolsResult toolsList = mcpSyncClient.listTools();
//        System.out.println("可用工具 = " + toolsList);

        // 调用 Tool
        McpSchema.CallToolResult weatherForecastResult = mcpSyncClient.callTool(new McpSchema.CallToolRequest("webSearchSogou",
                Map.of("search_query", query, "count", 5)));

        List<SearchResult> results = processSearchResult(weatherForecastResult.content());

        logger.info("McpService searchResult: {}", weatherForecastResult.content());
        return results;
    }

    private List<SearchResult> processSearchResult(List<McpSchema.Content> contentList) {
        List<SearchResult> results = null;
        for (McpSchema.Content content : contentList) {
            String type = content.type();
            if ("text".equals(type)) {
                String text = ((McpSchema.TextContent) content).text();
                logger.info("原始文本内容: {}", text);
                ObjectMapper mapper = new ObjectMapper();
                // 去除双引号
                text = text.substring(1, text.length() - 1);
                // 去除转义
                text = StringEscapeUtils.unescapeJson(text);
                logger.info("处理后文本内容: {}", text);
                try {
                    results = mapper.readValue(text, new TypeReference<List<SearchResult>>() {});
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("序列化失败");
                }

            }
        }
        return results;
    }

}
