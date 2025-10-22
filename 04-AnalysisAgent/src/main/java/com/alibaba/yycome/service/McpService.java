package com.alibaba.yycome.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.WebFluxSseClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
public class McpService {

    private final McpClientTransport transport;

    public McpService() {
        this.transport = new WebFluxSseClientTransport(
                WebClient.builder().defaultHeader("Authorization", "Bearer " + System.getenv("AI_DASHSCOPE_API_KEY"))
                        .baseUrl("https://dashscope.aliyuncs.com"), new ObjectMapper(), "/api/v1/mcps/zhipu-websearch/sse");


    }

    public List<McpSchema.Content> query(String query) {
        // 创建 McpClient
        var client = McpClient.sync(transport).build();
        // 初始化与 MCP Server 的连接
        client.initialize();
        client.ping();

        // 列出并展示可用的工具
        McpSchema.ListToolsResult toolsList = client.listTools();
        System.out.println("可用工具 = " + toolsList);

        // 调用 Tool
        McpSchema.CallToolResult weatherForecastResult = client.callTool(new McpSchema.CallToolRequest("webSearchSogou",
                Map.of("search_query", query)));
        System.out.println("搜索结果: " + weatherForecastResult);
        return weatherForecastResult.content();
//        client.closeGracefully();
    }

}
