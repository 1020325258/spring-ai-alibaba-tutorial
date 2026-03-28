package com.yycome.sreagent.trigger.http;

import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yycome.sreagent.config.SREAgentGraph;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.util.StreamUtils;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 拦截 /run_sse 请求，使用 streamMessages() 替代 stream() 避免重复输出。
 *
 * 根因：Studio 使用 agent.stream() 返回 Flux&lt;NodeOutput&gt;，
 * 在 LlmRoutingAgent + ReactAgent 两层结构中，两个节点都会发出包含相同文本的 NodeOutput，
 * 导致 Studio 前端拼接两次相同内容。使用 streamMessages() 可避免此问题。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RunSseFilter implements Filter {

    private final SREAgentGraph sreAgent;
    private final ObjectMapper objectMapper;

    @Autowired
    public RunSseFilter(SREAgentGraph sreAgent, ObjectMapper objectMapper) {
        this.sreAgent = sreAgent;
        this.objectMapper = objectMapper;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 只拦截 POST /run_sse
        if (!"POST".equalsIgnoreCase(httpRequest.getMethod())
                || !"/run_sse".equals(httpRequest.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        // 解析请求体：{"newMessage": {"content": "..."}, "appName": "..."}
        String body = StreamUtils.copyToString(httpRequest.getInputStream(), StandardCharsets.UTF_8);
        String content = parseContent(body);

        if (content == null || content.isBlank()) {
            httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            httpResponse.getWriter().write("{\"error\":\"missing content\"}");
            return;
        }

        // 设置 SSE 响应头
        httpResponse.setContentType("text/event-stream;charset=UTF-8");
        httpResponse.setHeader("Cache-Control", "no-cache");
        httpResponse.setHeader("Connection", "keep-alive");

        // 使用异步上下文释放 servlet 线程
        AsyncContext asyncContext = httpRequest.startAsync();
        asyncContext.setTimeout(120_000);
        PrintWriter writer = httpResponse.getWriter();

        try {
            sreAgent.streamMessages(content)
                    .filter(msg -> msg instanceof AssistantMessage am
                            && StringUtils.hasText(am.getText()))
                    .subscribe(
                            msg -> {
                                String chunk = ((AssistantMessage) msg).getText();
                                try {
                                    // 与 ExecutionController 相同格式
                                    String data = objectMapper.writeValueAsString(Map.of(
                                            "node", "sre-agent",
                                            "agent", "sre-agent",
                                            "chunk", chunk
                                    ));
                                    writer.write("data: " + data + "\n\n");
                                    writer.flush();
                                } catch (Exception ignored) {
                                    // 忽略写入异常
                                }
                            },
                            error -> {
                                try {
                                    writer.write("data: [DONE]\n\n");
                                    writer.flush();
                                } catch (Exception ignored) { }
                                asyncContext.complete();
                            },
                            () -> {
                                try {
                                    writer.write("data: [DONE]\n\n");
                                    writer.flush();
                                } catch (Exception ignored) { }
                                asyncContext.complete();
                            }
                    );
        } catch (GraphRunnerException e) {
            writer.write("data: [DONE]\n\n");
            writer.flush();
            asyncContext.complete();
        }
        // 短路：不调用 chain.doFilter()，避免 ExecutionController 再次处理
    }

    private String parseContent(String body) {
        try {
            var root = objectMapper.readTree(body);
            return root.path("newMessage").path("content").asText("");
        } catch (Exception e) {
            return "";
        }
    }
}