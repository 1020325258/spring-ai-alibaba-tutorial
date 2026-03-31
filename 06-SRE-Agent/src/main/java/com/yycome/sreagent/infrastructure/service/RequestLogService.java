package com.yycome.sreagent.infrastructure.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 请求日志服务
 * 记录完整的请求/响应到独立日志文件
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequestLogService {

    private static final Logger REQUEST_LOG = LoggerFactory.getLogger("REQUEST_LOG");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final ObjectMapper objectMapper;

    /**
     * 记录请求开始
     */
    public String logRequestStart(String sessionId, String message) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        REQUEST_LOG.info("\n{}", buildSeparator('=', 80));
        REQUEST_LOG.info("[{}] 请求开始", timestamp);
        REQUEST_LOG.info("SessionId: {}", sessionId);
        REQUEST_LOG.info("用户输入: {}", message);
        REQUEST_LOG.info("{}", buildSeparator('-', 80));
        return timestamp;
    }

    /**
     * 记录路由决策
     */
    public void logRouting(String sessionId, String target) {
        REQUEST_LOG.info("[路由] → {}", target);
    }

    /**
     * 记录工具调用
     */
    public void logToolCall(String sessionId, String toolName, Map<String, Object> params, Object result, long durationMs) {
        try {
            String paramsJson = objectMapper.writeValueAsString(params);
            String resultJson = result != null ? objectMapper.writeValueAsString(result) : "null";

            REQUEST_LOG.info("[工具] {}({})", toolName, paramsJson);
            REQUEST_LOG.info("[结果] {}ms | {}", durationMs, truncate(resultJson, 500));
        } catch (Exception e) {
            REQUEST_LOG.info("[工具] {} - 记录失败: {}", toolName, e.getMessage());
        }
    }

    /**
     * 记录响应完成
     */
    public void logResponseComplete(String sessionId, String response, long totalDurationMs) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        REQUEST_LOG.info("{}", buildSeparator('-', 80));
        REQUEST_LOG.info("[{}] 响应完成", timestamp);
        REQUEST_LOG.info("总耗时: {}ms", totalDurationMs);
        REQUEST_LOG.info("输出长度: {} 字符", response != null ? response.length() : 0);
        REQUEST_LOG.info("输出内容:\n{}", response);
        REQUEST_LOG.info("{}\n", buildSeparator('=', 80));
    }

    /**
     * 记录错误
     */
    public void logError(String sessionId, String error) {
        REQUEST_LOG.error("[错误] SessionId: {}, Error: {}", sessionId, error);
    }

    private String buildSeparator(char c, int length) {
        return String.valueOf(c).repeat(Math.max(0, length));
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() > maxLen ? s.substring(0, maxLen) + "...(truncated)" : s;
    }
}
