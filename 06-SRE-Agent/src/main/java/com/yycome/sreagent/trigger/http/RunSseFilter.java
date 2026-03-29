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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 拦截 /run_sse 请求，调用 streamMessages() 替代 Studio ExecutionController 的 stream() 路径。
 *
 * 根因：Studio ExecutionController 调用 agent.stream(UserMessage, RunnableConfig)，
 * 返回 Flux&lt;NodeOutput&gt;。SREAgentGraph 的自定义节点（AgentNode / AdminNode）通过
 * state["result"] 传递结果，产生普通 NodeOutput 而非 StreamingOutput，Studio 无法从中
 * 提取可渲染的 chunk 内容，导致 UI 无输出。
 *
 * 本 Filter 短路 Studio 的处理链，直接调用 streamMessages() 获取 Flux&lt;Message&gt;，
 * 并以与 ExecutionController 相同的 SSE 格式写回响应。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RunSseFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RunSseFilter.class);

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
                                log.error("RunSseFilter streamMessages error", error);
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
