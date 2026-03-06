# SREmate Agent Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 构建智能SRE值班客服Agent，支持多工具并行调用、流式输出、结果缓存和完整的可观测性。

**Architecture:** 模块化分层架构，包含工具层、编排层、基础设施层。工具通过统一接口定义，编排层支持并行调用和结果聚合，基础设施层提供追踪、缓存和监控能力。

**Tech Stack:** Java 21, Spring Boot 3.x, Spring AI Alibaba, Caffeine Cache, Micrometer, Elasticsearch Client

---

## Task 1: 创建统一工具接口和基础类

**Files:**
- Create: `src/main/java/com/yycome/sremate/tools/core/SRETool.java`
- Create: `src/main/java/com/yycome/sremate/tools/core/ToolMetadata.java`
- Create: `src/main/java/com/yycome/sremate/tools/core/ToolRequest.java`
- Create: `src/main/java/com/yycome/sremate/tools/core/ToolResult.java`
- Create: `src/main/java/com/yycome/sremate/tools/core/ParameterSpec.java`
- Test: `src/test/java/com/yycome/sremate/tools/core/ToolMetadataTest.java`

**Step 1: Write the failing test**

```java
package com.yycome.sremate.tools.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ToolMetadataTest {

    @Test
    void shouldCreateToolMetadata() {
        ToolMetadata metadata = ToolMetadata.builder()
            .name("testTool")
            .description("Test tool description")
            .category("test")
            .build();

        assertEquals("testTool", metadata.getName());
        assertEquals("Test tool description", metadata.getDescription());
        assertEquals("test", metadata.getCategory());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ToolMetadataTest`
Expected: FAIL with "Cannot resolve symbol 'ToolMetadata'"

**Step 3: Write ParameterSpec class**

```java
package com.yycome.sremate.tools.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParameterSpec {
    private String name;
    private String description;
    private boolean required;
    private String defaultValue;
}
```

**Step 4: Write ToolMetadata class**

```java
package com.yycome.sremate.tools.core;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolMetadata {
    private String name;
    private String description;
    private List<ParameterSpec> parameters;
    private String category;
}
```

**Step 5: Write ToolRequest class**

```java
package com.yycome.sremate.tools.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolRequest {
    private Map<String, Object> params;
}
```

**Step 6: Write ToolResult class**

```java
package com.yycome.sremate.tools.core;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {
    private boolean success;
    private String result;
    private String error;
    private long duration;

    public static ToolResult success(String result, long duration) {
        return ToolResult.builder()
            .success(true)
            .result(result)
            .duration(duration)
            .build();
    }

    public static ToolResult failure(String error, long duration) {
        return ToolResult.builder()
            .success(false)
            .error(error)
            .duration(duration)
            .build();
    }
}
```

**Step 7: Write SRETool interface**

```java
package com.yycome.sremate.tools.core;

public interface SRETool {
    /**
     * 获取工具元数据
     */
    ToolMetadata getMetadata();

    /**
     * 执行工具调用
     */
    ToolResult execute(ToolRequest request);

    /**
     * 是否支持并行调用
     */
    default boolean supportsParallel() {
        return true;
    }
}
```

**Step 8: Run test to verify it passes**

Run: `mvn test -Dtest=ToolMetadataTest`
Expected: PASS

**Step 9: Commit**

```bash
git add src/main/java/com/yycome/sremate/tools/core/
git add src/test/java/com/yycome/sremate/tools/core/
git commit -m "feat: add unified tool interface and core classes"
```

---

## Task 2: 创建追踪基础设施

**Files:**
- Create: `src/main/java/com/yycome/sremate/infrastructure/tracing/TracingContext.java`
- Create: `src/main/java/com/yycome/sremate/infrastructure/tracing/TraceSession.java`
- Create: `src/main/java/com/yycome/sremate/infrastructure/tracing/TraceChain.java`
- Create: `src/main/java/com/yycome/sremate/infrastructure/tracing/TracingService.java`
- Test: `src/test/java/com/yycome/sremate/infrastructure/tracing/TracingServiceTest.java`

**Step 1: Write the failing test**

```java
package com.yycome.sremate.infrastructure.tracing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TracingServiceTest {

    @Test
    void shouldStartAndEndToolCall() {
        TracingService tracingService = new TracingService();

        TracingContext context = tracingService.startToolCall("testTool");
        assertNotNull(context);
        assertEquals("testTool", context.getToolName());

        ToolResult result = ToolResult.success("test", 100);
        tracingService.endToolCall(context, result);

        assertTrue(context.getEndTime() > 0);
        assertTrue(context.isSuccess());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=TracingServiceTest`
Expected: FAIL with "Cannot resolve symbol 'TracingService'"

**Step 3: Write TracingContext class**

```java
package com.yycome.sremate.infrastructure.tracing;

import lombok.Data;
import java.util.Map;

@Data
public class TracingContext {
    private String sessionId;
    private String toolName;
    private Map<String, Object> params;
    private long startTime;
    private long endTime;
    private boolean success;
    private Object result;
    private Exception error;
}
```

**Step 4: Write TraceSession class**

```java
package com.yycome.sremate.infrastructure.tracing;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class TraceSession {
    private String sessionId;
    private String userId;
    private String query;
    private LocalDateTime startTime;
    private List<TracingContext> toolCalls = new ArrayList<>();

    public TraceSession(String sessionId, String userId, String query) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.query = query;
        this.startTime = LocalDateTime.now();
    }

    public void addToolCall(TracingContext context) {
        toolCalls.add(context);
    }
}
```

**Step 5: Write TraceChain class**

```java
package com.yycome.sremate.infrastructure.tracing;

import lombok.Data;
import java.util.List;

@Data
public class TraceChain {
    private String sessionId;
    private String query;
    private List<TracingContext> toolCalls;

    public static TraceChain from(TraceSession session) {
        TraceChain chain = new TraceChain();
        chain.setSessionId(session.getSessionId());
        chain.setQuery(session.getQuery());
        chain.setToolCalls(session.getToolCalls());
        return chain;
    }
}
```

**Step 6: Write TracingService class**

```java
package com.yycome.sremate.infrastructure.tracing;

import com.yycome.sremate.tools.core.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class TracingService {

    private final Map<String, TraceSession> sessions = new ConcurrentHashMap<>();

    public TraceSession startSession(String userId, String query) {
        String sessionId = UUID.randomUUID().toString();
        TraceSession session = new TraceSession(sessionId, userId, query);
        sessions.put(sessionId, session);
        log.info("Started trace session: {}", sessionId);
        return session;
    }

    public TracingContext startToolCall(String toolName) {
        TracingContext context = new TracingContext();
        context.setToolName(toolName);
        context.setStartTime(System.currentTimeMillis());
        log.debug("Started tool call: {}", toolName);
        return context;
    }

    public TracingContext startToolCall(String toolName, Map<String, Object> params) {
        TracingContext context = startToolCall(toolName);
        context.setParams(params);
        return context;
    }

    public void endToolCall(TracingContext context, ToolResult result) {
        context.setEndTime(System.currentTimeMillis());
        context.setSuccess(result.isSuccess());
        context.setResult(result);
        log.debug("Ended tool call: {}, success: {}, duration: {}ms",
            context.getToolName(), context.isSuccess(),
            context.getEndTime() - context.getStartTime());
    }

    public void failToolCall(TracingContext context, Exception error) {
        context.setEndTime(System.currentTimeMillis());
        context.setSuccess(false);
        context.setError(error);
        log.error("Failed tool call: {}", context.getToolName(), error);
    }

    public TraceChain getTraceChain(String sessionId) {
        TraceSession session = sessions.get(sessionId);
        if (session == null) {
            return null;
        }
        return TraceChain.from(session);
    }
}
```

**Step 7: Run test to verify it passes**

Run: `mvn test -Dtest=TracingServiceTest`
Expected: PASS

**Step 8: Commit**

```bash
git add src/main/java/com/yycome/sremate/infrastructure/tracing/
git add src/test/java/com/yycome/sremate/infrastructure/tracing/
git commit -m "feat: add tracing infrastructure"
```

---

## Task 3: 创建缓存服务

**Files:**
- Create: `src/main/java/com/yycome/sremate/infrastructure/cache/CacheService.java`
- Create: `src/main/java/com/yycome/sremate/infrastructure/cache/CacheEntry.java`
- Test: `src/test/java/com/yycome/sremate/infrastructure/cache/CacheServiceTest.java`

**Step 1: Add Caffeine dependency to pom.xml**

```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
    <version>3.1.8</version>
</dependency>
```

**Step 2: Write the failing test**

```java
package com.yycome.sremate.infrastructure.cache;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CacheServiceTest {

    @Test
    void shouldCacheValue() {
        CacheService cacheService = new CacheService();

        AtomicInteger counter = new AtomicInteger(0);

        String result1 = cacheService.getOrCompute("key1", () -> {
            counter.incrementAndGet();
            return "value" + counter.get();
        });

        String result2 = cacheService.getOrCompute("key1", () -> {
            counter.incrementAndGet();
            return "value" + counter.get();
        });

        assertEquals("value1", result1);
        assertEquals("value1", result2);
        assertEquals(1, counter.get());
    }
}
```

**Step 3: Run test to verify it fails**

Run: `mvn test -Dtest=CacheServiceTest`
Expected: FAIL with "Cannot resolve symbol 'CacheService'"

**Step 4: Write CacheEntry class**

```java
package com.yycome.sremate.infrastructure.cache;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CacheEntry<T> {
    private T value;
    private long expireTime;
}
```

**Step 5: Write CacheService class**

```java
package com.yycome.sremate.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

@Slf4j
@Service
public class CacheService {

    private final Cache<String, Object> cache;

    public CacheService() {
        this.cache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .recordStats()
            .build();
    }

    @SuppressWarnings("unchecked")
    public <T> T getOrCompute(String key, Supplier<T> supplier) {
        Object value = cache.get(key, k -> {
            log.debug("Cache miss for key: {}", key);
            return supplier.get();
        });

        if (value != null) {
            log.debug("Cache hit for key: {}", key);
        }

        return (T) value;
    }

    public void put(String key, Object value) {
        cache.put(key, value);
        log.debug("Put value to cache for key: {}", key);
    }

    public void invalidate(String key) {
        cache.invalidate(key);
        log.debug("Invalidated cache for key: {}", key);
    }

    public CacheStats getStats() {
        return cache.stats();
    }

    public long size() {
        return cache.estimatedSize();
    }
}
```

**Step 6: Run test to verify it passes**

Run: `mvn test -Dtest=CacheServiceTest`
Expected: PASS

**Step 7: Commit**

```bash
git add pom.xml
git add src/main/java/com/yycome/sremate/infrastructure/cache/
git add src/test/java/com/yycome/sremate/infrastructure/cache/
git commit -m "feat: add cache service with Caffeine"
```

---

## Task 4: 创建性能指标收集器

**Files:**
- Create: `src/main/java/com/yycome/sremate/infrastructure/metrics/ToolMetrics.java`
- Create: `src/main/java/com/yycome/sremate/infrastructure/metrics/MetricsCollector.java`
- Create: `src/main/java/com/yycome/sremate/infrastructure/metrics/PerformanceReport.java`
- Test: `src/test/java/com/yycome/sremate/infrastructure/metrics/MetricsCollectorTest.java`

**Step 1: Write the failing test**

```java
package com.yycome.sremate.infrastructure.metrics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MetricsCollectorTest {

    @Test
    void shouldRecordToolMetrics() {
        MetricsCollector collector = new MetricsCollector();

        collector.recordToolCall("testTool", 100, true);
        collector.recordToolCall("testTool", 200, true);
        collector.recordToolCall("testTool", 150, false);

        PerformanceReport report = collector.getReport();
        ToolMetrics metrics = report.getToolMetrics().get("testTool");

        assertNotNull(metrics);
        assertEquals(3, metrics.getTotalCalls());
        assertEquals(2, metrics.getSuccessCalls());
        assertEquals(1, metrics.getFailedCalls());
        assertEquals(150.0, metrics.getAverageDuration(), 0.1);
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=MetricsCollectorTest`
Expected: FAIL with "Cannot resolve symbol 'MetricsCollector'"

**Step 3: Write ToolMetrics class**

```java
package com.yycome.sremate.infrastructure.metrics;

import lombok.Data;
import java.util.concurrent.atomic.AtomicLong;

@Data
public class ToolMetrics {
    private String toolName;
    private AtomicLong totalCalls = new AtomicLong(0);
    private AtomicLong successCalls = new AtomicLong(0);
    private AtomicLong failedCalls = new AtomicLong(0);
    private AtomicLong totalDuration = new AtomicLong(0);

    public ToolMetrics(String toolName) {
        this.toolName = toolName;
    }

    public void recordCall(long duration, boolean success) {
        totalCalls.incrementAndGet();
        totalDuration.addAndGet(duration);
        if (success) {
            successCalls.incrementAndGet();
        } else {
            failedCalls.incrementAndGet();
        }
    }

    public double getAverageDuration() {
        long calls = totalCalls.get();
        return calls == 0 ? 0 : (double) totalDuration.get() / calls;
    }

    public double getSuccessRate() {
        long calls = totalCalls.get();
        return calls == 0 ? 0 : (double) successCalls.get() / calls * 100;
    }
}
```

**Step 4: Write PerformanceReport class**

```java
package com.yycome.sremate.infrastructure.metrics;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

@Data
public class PerformanceReport {
    private Map<String, ToolMetrics> toolMetrics = new HashMap<>();

    public void addToolMetrics(String toolName, ToolMetrics metrics) {
        toolMetrics.put(toolName, metrics);
    }
}
```

**Step 5: Write MetricsCollector class**

```java
package com.yycome.sremate.infrastructure.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class MetricsCollector {

    private final Map<String, ToolMetrics> toolMetricsMap = new ConcurrentHashMap<>();

    public void recordToolCall(String toolName, long duration, boolean success) {
        ToolMetrics metrics = toolMetricsMap.computeIfAbsent(
            toolName,
            k -> new ToolMetrics(toolName)
        );

        metrics.recordCall(duration, success);
        log.debug("Recorded tool call: {}, duration: {}ms, success: {}",
            toolName, duration, success);
    }

    public PerformanceReport getReport() {
        PerformanceReport report = new PerformanceReport();
        toolMetricsMap.forEach((name, metrics) -> {
            report.addToolMetrics(name, metrics);
        });
        return report;
    }

    public ToolMetrics getToolMetrics(String toolName) {
        return toolMetricsMap.get(toolName);
    }
}
```

**Step 6: Run test to verify it passes**

Run: `mvn test -Dtest=MetricsCollectorTest`
Expected: PASS

**Step 7: Commit**

```bash
git add src/main/java/com/yycome/sremate/infrastructure/metrics/
git add src/test/java/com/yycome/sremate/infrastructure/metrics/
git commit -m "feat: add metrics collector"
```

---

## Task 5: 重构现有工具实现统一接口

**Files:**
- Modify: `src/main/java/com/yycome/sremate/tools/SkillQueryTool.java`
- Modify: `src/main/java/com/yycome/sremate/tools/MySQLQueryTool.java`
- Modify: `src/main/java/com/yycome/sremate/tools/HttpQueryTool.java`
- Test: `src/test/java/com/yycome/sremate/tools/SkillQueryToolTest.java`

**Step 1: Write the failing test**

```java
package com.yycome.sremate.tools;

import com.yycome.sremate.tools.core.SRETool;
import com.yycome.sremate.tools.core.ToolMetadata;
import com.yycome.sremate.tools.core.ToolRequest;
import com.yycome.sremate.tools.core.ToolResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SkillQueryToolTest {

    @Autowired
    private SkillQueryTool skillQueryTool;

    @Test
    void shouldImplementSREToolInterface() {
        assertTrue(skillQueryTool instanceof SRETool);
    }

    @Test
    void shouldHaveValidMetadata() {
        ToolMetadata metadata = skillQueryTool.getMetadata();
        assertNotNull(metadata);
        assertEquals("querySkills", metadata.getName());
        assertNotNull(metadata.getDescription());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=SkillQueryToolTest`
Expected: FAIL with "SkillQueryTool does not implement SRETool"

**Step 3: Refactor SkillQueryTool**

```java
package com.yycome.sremate.tools;

import com.yycome.sremate.service.SkillService;
import com.yycome.sremate.tools.core.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Slf4j
@Component
@RequiredArgsConstructor
public class SkillQueryTool implements SRETool {

    private final SkillService skillService;

    @Override
    public ToolMetadata getMetadata() {
        return ToolMetadata.builder()
            .name("querySkills")
            .description("查询SRE运维知识库，获取问题排查经验和解决方案")
            .parameters(Arrays.asList(
                new ParameterSpec("queryType", "查询类型（diagnosis/operations/knowledge）", false, "diagnosis"),
                new ParameterSpec("keywords", "关键词，用于匹配相关文档", false, null)
            ))
            .category("knowledge")
            .build();
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        long startTime = System.currentTimeMillis();
        try {
            Map<String, Object> params = request.getParams();
            String queryType = (String) params.getOrDefault("queryType", "diagnosis");
            String keywords = (String) params.get("keywords");

            log.info("调用SkillQueryTool - 类型: {}, 关键词: {}", queryType, keywords);
            String result = skillService.querySkills(queryType, keywords);

            return ToolResult.success(result, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("SkillQueryTool执行失败", e);
            return ToolResult.failure(e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

    // Keep original methods for backward compatibility
    @Tool(description = "查询SRE运维知识库，获取问题排查经验和解决方案。" +
            "queryType可选值：diagnosis（问题诊断）、operations（运维咨询）、knowledge（通用知识）。" +
            "keywords用于匹配相关的文档，多个关键词用空格分隔。")
    public String querySkills(String queryType, String keywords) {
        log.info("调用SkillQueryTool - 类型: {}, 关键词: {}", queryType, keywords);
        return skillService.querySkills(queryType, keywords);
    }

    @Tool(description = "列出SRE运维知识库的所有分类")
    public String listSkillCategories() {
        log.info("调用listSkillCategories");
        return "可用的Skills分类：" + String.join(", ", skillService.listSkillCategories());
    }
}
```

**Step 4: Refactor MySQLQueryTool**

```java
package com.yycome.sremate.tools;

import com.yycome.sremate.tools.core.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MySQLQueryTool implements SRETool {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public ToolMetadata getMetadata() {
        return ToolMetadata.builder()
            .name("executeQuery")
            .description("执行MySQL查询，用于排查数据库相关问题")
            .parameters(Arrays.asList(
                new ParameterSpec("sql", "SELECT或SHOW语句", true, null),
                new ParameterSpec("description", "查询描述", false, null)
            ))
            .category("database")
            .build();
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        long startTime = System.currentTimeMillis();
        try {
            Map<String, Object> params = request.getParams();
            String sql = (String) params.get("sql");
            String description = (String) params.getOrDefault("description", "");

            String result = executeQueryInternal(sql, description);
            return ToolResult.success(result, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("MySQLQueryTool执行失败", e);
            return ToolResult.failure(e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

    // Keep original method for backward compatibility
    @Tool(description = "执行MySQL查询，用于排查数据库相关问题。" +
            "仅支持SELECT查询，禁止执行INSERT、UPDATE、DELETE等修改操作。" +
            "sql参数是要执行的SELECT语句，description参数是对查询的描述。")
    public String executeQuery(String sql, String description) {
        return executeQueryInternal(sql, description);
    }

    private String executeQueryInternal(String sql, String description) {
        log.info("调用MySQLQueryTool - 描述: {}, SQL: {}", description, sql);

        // 安全检查：只允许SELECT查询
        String trimmedSql = sql.trim().toUpperCase();
        if (!trimmedSql.startsWith("SELECT") && !trimmedSql.startsWith("SHOW")) {
            return "错误：仅支持SELECT和SHOW查询，禁止执行修改操作";
        }

        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

            if (results.isEmpty()) {
                return "查询结果为空";
            }

            // 格式化输出
            StringBuilder result = new StringBuilder();
            result.append(String.format("查询: %s\n", description));
            result.append(String.format("SQL: %s\n", sql));
            result.append(String.format("返回 %d 条记录:\n\n", results.size()));

            for (int i = 0; i < results.size(); i++) {
                Map<String, Object> row = results.get(i);
                result.append(String.format("记录 %d:\n", i + 1));
                row.forEach((key, value) ->
                    result.append(String.format("  %s: %s\n", key, value))
                );
                result.append("\n");

                // 限制输出数量，避免结果过大
                if (i >= 9) {
                    result.append("...(仅显示前10条记录)\n");
                    break;
                }
            }

            return result.toString();

        } catch (Exception e) {
            log.error("MySQL查询执行失败", e);
            return "查询执行失败: " + e.getMessage();
        }
    }
}
```

**Step 5: Refactor HttpQueryTool**

```java
package com.yycome.sremate.tools;

import com.yycome.sremate.tools.core.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;

@Slf4j
@Component
public class HttpQueryTool implements SRETool {

    private final WebClient webClient;

    public HttpQueryTool(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    @Override
    public ToolMetadata getMetadata() {
        return ToolMetadata.builder()
            .name("callHttpEndpoint")
            .description("调用HTTP接口获取系统状态或诊断信息")
            .parameters(Arrays.asList(
                new ParameterSpec("url", "接口URL", true, null),
                new ParameterSpec("method", "HTTP方法（GET/POST）", false, "GET"),
                new ParameterSpec("params", "请求参数（POST请求使用）", false, null)
            ))
            .category("http")
            .build();
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        long startTime = System.currentTimeMillis();
        try {
            Map<String, Object> params = request.getParams();
            String url = (String) params.get("url");
            String method = (String) params.getOrDefault("method", "GET");
            @SuppressWarnings("unchecked")
            Map<String, Object> bodyParams = (Map<String, Object>) params.get("params");

            String result = callHttpEndpointInternal(url, method, bodyParams);
            return ToolResult.success(result, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("HttpQueryTool执行失败", e);
            return ToolResult.failure(e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

    // Keep original method for backward compatibility
    @Tool(description = "调用HTTP接口获取系统状态或诊断信息。" +
            "url参数是完整的接口地址，method参数是HTTP方法（GET或POST），" +
            "params参数是请求参数（仅POST请求需要，JSON格式）。")
    public String callHttpEndpoint(String url, String method, Map<String, Object> params) {
        return callHttpEndpointInternal(url, method, params);
    }

    private String callHttpEndpointInternal(String url, String method, Map<String, Object> params) {
        log.info("调用HttpQueryTool - URL: {}, 方法: {}", url, method);

        try {
            Mono<String> responseMono;

            if ("GET".equalsIgnoreCase(method)) {
                responseMono = webClient.get()
                        .uri(url)
                        .retrieve()
                        .bodyToMono(String.class);
            } else if ("POST".equalsIgnoreCase(method)) {
                responseMono = webClient.post()
                        .uri(url)
                        .bodyValue(params != null ? params : "{}")
                        .retrieve()
                        .bodyToMono(String.class);
            } else {
                return "错误：不支持的HTTP方法: " + method;
            }

            String response = responseMono
                    .timeout(Duration.ofSeconds(30))
                    .block();

            return String.format("接口: %s\n方法: %s\n响应:\n%s", url, method, response);

        } catch (Exception e) {
            log.error("HTTP接口调用失败", e);
            return "接口调用失败: " + e.getMessage();
        }
    }
}
```

**Step 6: Run test to verify it passes**

Run: `mvn test -Dtest=SkillQueryToolTest`
Expected: PASS

**Step 7: Commit**

```bash
git add src/main/java/com/yycome/sremate/tools/
git add src/test/java/com/yycome/sremate/tools/
git commit -m "refactor: implement SRETool interface for existing tools"
```

---

## Task 6: 创建ELK日志查询工具

**Files:**
- Create: `src/main/java/com/yycome/sremate/tools/ElkLogQueryTool.java`
- Test: `src/test/java/com/yycome/sremate/tools/ElkLogQueryToolTest.java`

**Step 1: Add Elasticsearch dependency to pom.xml**

```xml
<dependency>
    <groupId>co.elastic.clients</groupId>
    <artifactId>elasticsearch-java</artifactId>
    <version>8.11.0</version>
</dependency>
```

**Step 2: Write the failing test**

```java
package com.yycome.sremate.tools;

import com.yycome.sremate.tools.core.SRETool;
import com.yycome.sremate.tools.core.ToolMetadata;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ElkLogQueryToolTest {

    @Test
    void shouldImplementSREToolInterface() {
        ElkLogQueryTool tool = new ElkLogQueryTool(null);
        assertTrue(tool instanceof SRETool);
    }

    @Test
    void shouldHaveValidMetadata() {
        ElkLogQueryTool tool = new ElkLogQueryTool(null);
        ToolMetadata metadata = tool.getMetadata();
        assertNotNull(metadata);
        assertEquals("queryLogs", metadata.getName());
        assertEquals("log", metadata.getCategory());
    }
}
```

**Step 3: Run test to verify it fails**

Run: `mvn test -Dtest=ElkLogQueryToolTest`
Expected: FAIL with "Cannot resolve symbol 'ElkLogQueryTool'"

**Step 4: Write ElkLogQueryTool class**

```java
package com.yycome.sremate.tools;

import com.yycome.sremate.tools.core.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class ElkLogQueryTool implements SRETool {

    private final WebClient webClient;
    private final String elasticsearchUrl;

    public ElkLogQueryTool(
            WebClient.Builder webClientBuilder,
            @Value("${elasticsearch.url:http://localhost:9200}") String elasticsearchUrl) {
        this.elasticsearchUrl = elasticsearchUrl;
        this.webClient = webClientBuilder
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    @Override
    public ToolMetadata getMetadata() {
        return ToolMetadata.builder()
            .name("queryLogs")
            .description("查询ELK日志，用于排查应用日志相关问题")
            .parameters(Arrays.asList(
                new ParameterSpec("serviceName", "服务名称", true, null),
                new ParameterSpec("level", "日志级别（INFO/WARN/ERROR）", false, null),
                new ParameterSpec("keywords", "关键词，多个用空格分隔", false, null),
                new ParameterSpec("startTime", "开始时间（格式：yyyy-MM-dd HH:mm:ss）", false, null),
                new ParameterSpec("endTime", "结束时间（格式：yyyy-MM-dd HH:mm:ss）", false, null),
                new ParameterSpec("limit", "返回条数限制，默认100", false, "100")
            ))
            .category("log")
            .build();
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        long startTime = System.currentTimeMillis();
        try {
            Map<String, Object> params = request.getParams();
            String serviceName = (String) params.get("serviceName");
            String level = (String) params.get("level");
            String keywords = (String) params.get("keywords");
            String startTimeStr = (String) params.get("startTime");
            String endTimeStr = (String) params.get("endTime");
            int limit = Integer.parseInt((String) params.getOrDefault("limit", "100"));

            String result = queryLogsInternal(serviceName, level, keywords, startTimeStr, endTimeStr, limit);
            return ToolResult.success(result, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("ElkLogQueryTool执行失败", e);
            return ToolResult.failure(e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

    @Tool(description = "查询ELK日志，用于排查应用日志相关问题。" +
            "serviceName是服务名称（必填），level是日志级别（INFO/WARN/ERROR），" +
            "keywords是关键词（多个用空格分隔），startTime和endTime是时间范围，" +
            "limit是返回条数限制（默认100）。")
    public String queryLogs(String serviceName, String level, String keywords,
                          String startTime, String endTime, String limit) {
        int limitInt = Integer.parseInt(limit != null ? limit : "100");
        return queryLogsInternal(serviceName, level, keywords, startTime, endTime, limitInt);
    }

    private String queryLogsInternal(String serviceName, String level, String keywords,
                                    String startTime, String endTime, int limit) {
        log.info("调用ElkLogQueryTool - 服务: {}, 级别: {}, 关键词: {}",
            serviceName, level, keywords);

        try {
            // 构建Elasticsearch查询
            Map<String, Object> query = buildElasticsearchQuery(
                serviceName, level, keywords, startTime, endTime, limit
            );

            // 调用Elasticsearch API
            String indexName = "logs-" + serviceName;
            String response = webClient.post()
                    .uri(elasticsearchUrl + "/" + indexName + "/_search")
                    .bodyValue(query)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            return formatLogResponse(response);

        } catch (Exception e) {
            log.error("ELK日志查询失败", e);
            return "日志查询失败: " + e.getMessage();
        }
    }

    private Map<String, Object> buildElasticsearchQuery(String serviceName, String level,
                                                        String keywords, String startTime,
                                                        String endTime, int limit) {
        Map<String, Object> query = new HashMap<>();
        Map<String, Object> boolQuery = new HashMap<>();
        Map<String, Object> must = new HashMap<>();

        // 服务名过滤
        Map<String, Object> serviceNameFilter = new HashMap<>();
        serviceNameFilter.put("service_name.keyword", serviceName);
        must.put("term", serviceNameFilter);

        // 日志级别过滤
        if (level != null && !level.isEmpty()) {
            Map<String, Object> levelFilter = new HashMap<>();
            levelFilter.put("level.keyword", level);
            must.put("term", levelFilter);
        }

        // 关键词搜索
        if (keywords != null && !keywords.isEmpty()) {
            Map<String, Object> matchQuery = new HashMap<>();
            matchQuery.put("message", keywords);
            must.put("match", matchQuery);
        }

        // 时间范围过滤
        if (startTime != null || endTime != null) {
            Map<String, Object> rangeQuery = new HashMap<>();
            Map<String, Object> timestampRange = new HashMap<>();

            if (startTime != null) {
                timestampRange.put("gte", convertToIsoFormat(startTime));
            }
            if (endTime != null) {
                timestampRange.put("lte", convertToIsoFormat(endTime));
            }

            rangeQuery.put("@timestamp", timestampRange);
            must.put("range", rangeQuery);
        }

        boolQuery.put("must", must);
        query.put("query", boolQuery);
        query.put("size", limit);
        query.put("sort", Map.of("@timestamp", Map.of("order", "desc")));

        return query;
    }

    private String convertToIsoFormat(String dateTime) {
        if (dateTime == null || dateTime.isEmpty()) {
            return null;
        }
        try {
            LocalDateTime ldt = LocalDateTime.parse(dateTime,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return ldt.format(DateTimeFormatter.ISO_DATE_TIME) + "Z";
        } catch (Exception e) {
            log.warn("日期格式转换失败: {}", dateTime, e);
            return dateTime;
        }
    }

    private String formatLogResponse(String response) {
        // 简化处理，直接返回响应
        // 实际项目中应该解析JSON并格式化输出
        return "日志查询结果:\n" + response;
    }
}
```

**Step 5: Run test to verify it passes**

Run: `mvn test -Dtest=ElkLogQueryToolTest`
Expected: PASS

**Step 6: Commit**

```bash
git add pom.xml
git add src/main/java/com/yycome/sremate/tools/ElkLogQueryTool.java
git add src/test/java/com/yycome/sremate/tools/ElkLogQueryToolTest.java
git commit -m "feat: add ELK log query tool"
```

---

## Task 7: 创建工具编排器

**Files:**
- Create: `src/main/java/com/yycome/sremate/orchestration/ToolExecutionRequest.java`
- Create: `src/main/java/com/yycome/sremate/orchestration/ToolOrchestrator.java`
- Create: `src/main/java/com/yycome/sremate/orchestration/ParallelExecutor.java`
- Test: `src/test/java/com/yycome/sremate/orchestration/ToolOrchestratorTest.java`

**Step 1: Write the failing test**

```java
package com.yycome.sremate.orchestration;

import com.yycome.sremate.infrastructure.tracing.TracingService;
import com.yycome.sremate.tools.core.SRETool;
import com.yycome.sremate.tools.core.ToolMetadata;
import com.yycome.sremate.tools.core.ToolRequest;
import com.yycome.sremate.tools.core.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolOrchestratorTest {

    @Test
    void shouldExecuteTool() {
        Map<String, SRETool> toolRegistry = new HashMap<>();
        toolRegistry.put("testTool", new MockTool());

        TracingService tracingService = new TracingService();
        ParallelExecutor parallelExecutor = new ParallelExecutor();
        ToolOrchestrator orchestrator = new ToolOrchestrator(toolRegistry, parallelExecutor, tracingService);

        ToolResult result = orchestrator.executeTool("testTool", Map.of("param1", "value1"));

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("mock result", result.getResult());
    }

    private static class MockTool implements SRETool {
        @Override
        public ToolMetadata getMetadata() {
            return ToolMetadata.builder()
                .name("testTool")
                .description("Mock tool")
                .build();
        }

        @Override
        public ToolResult execute(ToolRequest request) {
            return ToolResult.success("mock result", 100);
        }
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ToolOrchestratorTest`
Expected: FAIL with "Cannot resolve symbol 'ToolOrchestrator'"

**Step 3: Write ToolExecutionRequest class**

```java
package com.yycome.sremate.orchestration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolExecutionRequest {
    private String toolName;
    private Map<String, Object> params;
}
```

**Step 4: Write ParallelExecutor class**

```java
package com.yycome.sremate.orchestration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParallelExecutor {

    private final ToolOrchestrator orchestrator;

    /**
     * 并行执行多个工具调用
     */
    public List<ToolResult> executeParallel(List<ToolExecutionRequest> requests) {
        log.info("开始并行执行 {} 个工具调用", requests.size());

        List<CompletableFuture<ToolResult>> futures = requests.stream()
            .map(req -> CompletableFuture.supplyAsync(() -> {
                try {
                    return orchestrator.executeTool(req.getToolName(), req.getParams());
                } catch (Exception e) {
                    log.error("工具调用失败: {}", req.getToolName(), e);
                    return ToolResult.failure(e.getMessage(), 0);
                }
            }))
            .collect(Collectors.toList());

        List<ToolResult> results = futures.stream()
            .map(CompletableFuture::join)
            .collect(Collectors.toList());

        log.info("并行执行完成，成功: {}，失败: {}",
            results.stream().filter(ToolResult::isSuccess).count(),
            results.stream().filter(r -> !r.isSuccess()).count());

        return results;
    }
}
```

**Step 5: Write ToolOrchestrator class**

```java
package com.yycome.sremate.orchestration;

import com.yycome.sremate.infrastructure.metrics.MetricsCollector;
import com.yycome.sremate.infrastructure.tracing.TracingContext;
import com.yycome.sremate.infrastructure.tracing.TracingService;
import com.yycome.sremate.tools.core.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ToolOrchestrator {

    private final Map<String, SRETool> toolRegistry;
    private final ParallelExecutor parallelExecutor;
    private final TracingService tracingService;
    private final MetricsCollector metricsCollector;

    @Autowired
    public ToolOrchestrator(
            List<SRETool> tools,
            ParallelExecutor parallelExecutor,
            TracingService tracingService,
            MetricsCollector metricsCollector) {
        this.parallelExecutor = parallelExecutor;
        this.tracingService = tracingService;
        this.metricsCollector = metricsCollector;

        // 注册所有工具
        this.toolRegistry = new java.util.HashMap<>();
        for (SRETool tool : tools) {
            String toolName = tool.getMetadata().getName();
            toolRegistry.put(toolName, tool);
            log.info("注册工具: {}", toolName);
        }
    }

    /**
     * 执行单个工具调用
     */
    public ToolResult executeTool(String toolName, Map<String, Object> params) {
        log.info("执行工具: {}, 参数: {}", toolName, params);

        SRETool tool = toolRegistry.get(toolName);
        if (tool == null) {
            String error = "工具不存在: " + toolName;
            log.error(error);
            return ToolResult.failure(error, 0);
        }

        TracingContext tracing = tracingService.startToolCall(toolName, params);
        long startTime = System.currentTimeMillis();

        try {
            ToolResult result = tool.execute(new ToolRequest(params));
            tracingService.endToolCall(tracing, result);

            // 记录指标
            metricsCollector.recordToolCall(toolName, result.getDuration(), result.isSuccess());

            log.info("工具执行完成: {}, 耗时: {}ms, 成功: {}",
                toolName, result.getDuration(), result.isSuccess());

            return result;

        } catch (Exception e) {
            tracingService.failToolCall(tracing, e);
            long duration = System.currentTimeMillis() - startTime;

            // 记录失败指标
            metricsCollector.recordToolCall(toolName, duration, false);

            log.error("工具执行失败: {}", toolName, e);
            return ToolResult.failure(e.getMessage(), duration);
        }
    }

    /**
     * 并行执行多个工具调用
     */
    public List<ToolResult> executeParallel(List<ToolExecutionRequest> requests) {
        return parallelExecutor.executeParallel(requests);
    }

    /**
     * 获取所有已注册的工具
     */
    public Map<String, SRETool> getRegisteredTools() {
        return new java.util.HashMap<>(toolRegistry);
    }
}
```

**Step 6: Run test to verify it passes**

Run: `mvn test -Dtest=ToolOrchestratorTest`
Expected: PASS

**Step 7: Commit**

```bash
git add src/main/java/com/yycome/sremate/orchestration/
git add src/test/java/com/yycome/sremate/orchestration/
git commit -m "feat: add tool orchestrator with parallel execution"
```

---

## Task 8: 创建结果聚合器

**Files:**
- Create: `src/main/java/com/yycome/sremate/orchestration/AggregatedResult.java`
- Create: `src/main/java/com/yycome/sremate/orchestration/ResultAggregator.java`
- Test: `src/test/java/com/yycome/sremate/orchestration/ResultAggregatorTest.java`

**Step 1: Write the failing test**

```java
package com.yycome.sremate.orchestration;

import com.yycome.sremate.tools.core.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResultAggregatorTest {

    @Test
    void shouldAggregateResults() {
        ResultAggregator aggregator = new ResultAggregator();

        List<ToolResult> results = Arrays.asList(
            ToolResult.success("result1", 100),
            ToolResult.success("result2", 200),
            ToolResult.failure("error1", 50)
        );

        AggregatedResult aggregated = aggregator.aggregate(results);

        assertEquals(2, aggregated.getSuccessResults().size());
        assertEquals(1, aggregated.getFailedResults().size());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ResultAggregatorTest`
Expected: FAIL with "Cannot resolve symbol 'ResultAggregator'"

**Step 3: Write AggregatedResult class**

```java
package com.yycome.sremate.orchestration;

import com.yycome.sremate.tools.core.ToolResult;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AggregatedResult {
    private List<ToolResult> successResults = new ArrayList<>();
    private List<ToolResult> failedResults = new ArrayList<>();
    private List<String> keyInsights = new ArrayList<>();

    public void addSuccessResult(ToolResult result) {
        successResults.add(result);
    }

    public void addFailedResult(ToolResult result) {
        failedResults.add(result);
    }

    public void setKeyInsights(List<String> insights) {
        this.keyInsights = insights;
    }

    public boolean hasErrors() {
        return !failedResults.isEmpty();
    }

    public int getTotalResults() {
        return successResults.size() + failedResults.size();
    }
}
```

**Step 4: Write ResultAggregator class**

```java
package com.yycome.sremate.orchestration;

import com.yycome.sremate.tools.core.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ResultAggregator {

    /**
     * 聚合多个工具结果
     */
    public AggregatedResult aggregate(List<ToolResult> results) {
        log.info("开始聚合 {} 个工具结果", results.size());

        AggregatedResult aggregated = new AggregatedResult();

        for (ToolResult result : results) {
            if (result.isSuccess()) {
                aggregated.addSuccessResult(result);
            } else {
                aggregated.addFailedResult(result);
            }
        }

        // 提取关键信息
        List<String> insights = extractKeyInsights(results);
        aggregated.setKeyInsights(insights);

        log.info("聚合完成: {} 成功, {} 失败",
            aggregated.getSuccessResults().size(),
            aggregated.getFailedResults().size());

        return aggregated;
    }

    /**
     * 提取关键信息
     */
    private List<String> extractKeyInsights(List<ToolResult> results) {
        List<String> insights = new ArrayList<>();

        for (ToolResult result : results) {
            if (result.isSuccess() && result.getResult() != null) {
                // 简单的关键信息提取：查找错误关键字
                String content = result.getResult().toLowerCase();
                if (content.contains("error") || content.contains("exception")) {
                    insights.add("发现错误或异常信息");
                }
                if (content.contains("timeout")) {
                    insights.add("发现超时问题");
                }
                if (content.contains("connection")) {
                    insights.add("发现连接相关问题");
                }
            }
        }

        return insights;
    }

    /**
     * 生成聚合报告
     */
    public String generateReport(AggregatedResult aggregated) {
        StringBuilder report = new StringBuilder();
        report.append("=== 工具调用结果汇总 ===\n\n");

        report.append(String.format("总调用数: %d\n", aggregated.getTotalResults()));
        report.append(String.format("成功: %d\n", aggregated.getSuccessResults().size()));
        report.append(String.format("失败: %d\n", aggregated.getFailedResults().size()));
        report.append("\n");

        if (!aggregated.getKeyInsights().isEmpty()) {
            report.append("关键发现:\n");
            for (String insight : aggregated.getKeyInsights()) {
                report.append("- ").append(insight).append("\n");
            }
            report.append("\n");
        }

        if (aggregated.hasErrors()) {
            report.append("失败的调用:\n");
            for (ToolResult result : aggregated.getFailedResults()) {
                report.append("- 错误: ").append(result.getError()).append("\n");
            }
        }

        return report.toString();
    }
}
```

**Step 5: Run test to verify it passes**

Run: `mvn test -Dtest=ResultAggregatorTest`
Expected: PASS

**Step 6: Commit**

```bash
git add src/main/java/com/yycome/sremate/orchestration/
git add src/test/java/com/yycome/sremate/orchestration/
git commit -m "feat: add result aggregator"
```

---

## Task 9: 实现流式输出

**Files:**
- Modify: `src/main/java/com/yycome/sremate/console/SREConsole.java`
- Modify: `src/main/resources/prompts/sre-agent.md`

**Step 1: Modify SREConsole to support streaming**

在 `SREConsole.java` 中添加流式输出支持：

```java
// 在调用Agent的部分，改为流式输出
Flux<String> responseStream = sreAgent.prompt()
        .messages(conversationHistory)
        .stream()
        .content();

StringBuilder fullResponse = new StringBuilder();
System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).a("\nSRE助手: ").reset());

responseStream.subscribe(chunk -> {
    System.out.print(chunk);
    fullResponse.append(chunk);
});

// 等待流完成
responseStream.blockLast();

// 添加助手消息到历史
conversationHistory.add(new AssistantMessage(fullResponse.toString()));
```

**Step 2: Update system prompt**

更新 `sre-agent.md`，添加工具调用追踪说明：

```markdown
## 工具调用追踪

当调用工具时，请遵循以下格式：

1. **调用前**：简要说明将要调用哪个工具以及目的
2. **调用中**：如果支持实时显示，显示工具调用进度
3. **调用后**：简要说明获取到了什么信息

示例：
我需要查询数据库连接状态...
[调用 executeQuery 工具]
获取到数据库连接信息：当前连接数 150/200
```

**Step 3: Test streaming output**

Run: `mvn spring-boot:run`
Test: 在控制台输入问题，观察流式输出效果

**Step 4: Commit**

```bash
git add src/main/java/com/yycome/sremate/console/SREConsole.java
git add src/main/resources/prompts/sre-agent.md
git commit -m "feat: implement streaming output in console"
```

---

## Task 10: 更新Agent配置

**Files:**
- Modify: `src/main/java/com/yycome/sremate/config/AgentConfiguration.java`

**Step 1: Update AgentConfiguration**

更新Agent配置，注册新工具：

```java
@Bean
public ToolCallbackProvider sreTools(
        SkillQueryTool skillQueryTool,
        MySQLQueryTool mySQLQueryTool,
        HttpQueryTool httpQueryTool,
        ElkLogQueryTool elkLogQueryTool) {
    return MethodToolCallbackProvider.builder()
            .toolObjects(skillQueryTool, mySQLQueryTool, httpQueryTool, elkLogQueryTool)
            .build();
}
```

**Step 2: Test configuration**

Run: `mvn test`
Expected: PASS

**Step 3: Commit**

```bash
git add src/main/java/com/yycome/sremate/config/AgentConfiguration.java
git commit -m "feat: register ElkLogQueryTool in agent configuration"
```

---

## Task 11: 添加配置文件

**Files:**
- Modify: `src/main/resources/application.yml`

**Step 1: Add configuration**

```yaml
spring:
  application:
    name: sremate

  # 数据库配置
  datasource:
    url: jdbc:mysql://localhost:3306/your_database?useSSL=false&serverTimezone=UTC
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver

  # Elasticsearch配置
  elasticsearch:
    url: http://localhost:9200

  # AI配置
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
      chat:
        options:
          model: qwen-plus
          temperature: 0.7

# SRE配置
sre:
  console:
    enabled: true
  cache:
    enabled: true
    ttl: 600  # 10分钟

# 监控配置
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always

# 日志配置
logging:
  level:
    com.yycome.sremate: DEBUG
    org.springframework.ai: INFO
```

**Step 2: Commit**

```bash
git add src/main/resources/application.yml
git commit -m "feat: add application configuration"
```

---

## Task 12: 编写集成测试

**Files:**
- Create: `src/test/java/com/yycome/sremate/integration/SREAgentIntegrationTest.java`

**Step 1: Write integration test**

```java
package com.yycome.sremate.integration;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SREAgentIntegrationTest {

    @Autowired
    private ChatClient sreAgent;

    @Test
    void shouldRespondToUserQuery() {
        String response = sreAgent.prompt()
                .user("查询数据库连接状态")
                .call()
                .content();

        assertNotNull(response);
        assertTrue(response.length() > 0);
    }
}
```

**Step 2: Run integration test**

Run: `mvn test -Dtest=SREAgentIntegrationTest`
Expected: PASS

**Step 3: Commit**

```bash
git add src/test/java/com/yycome/sremate/integration/
git commit -m "feat: add integration tests"
```

---

## Task 13: 更新文档

**Files:**
- Create: `README.md`

**Step 1: Write README**

```markdown
# SREmate - 智能SRE值班客服Agent

## 简介

SREmate是一个基于Spring AI Alibaba的智能SRE值班客服Agent，帮助研发人员快速排查和解决应用层问题。

## 核心功能

1. **意图理解**：理解用户问题描述，识别问题类型
2. **知识库查询**：查询Skills文档，获取排查经验
3. **多工具调用**：支持数据库、HTTP接口、日志查询等工具
4. **并行执行**：多个工具可以并行调用，提升响应速度
5. **流式输出**：实时显示Agent的思考和回复过程
6. **结果缓存**：缓存常用查询结果，减少重复调用
7. **可观测性**：完整的调用追踪、性能监控和链路可视化

## 技术栈

- Java 21
- Spring Boot 3.x
- Spring AI Alibaba
- Caffeine Cache
- Elasticsearch Client
- MySQL

## 快速开始

### 1. 配置环境变量

```bash
export DASHSCOPE_API_KEY=your_api_key
```

### 2. 配置数据库和Elasticsearch

编辑 `src/main/resources/application.yml`，配置数据库和Elasticsearch连接信息。

### 3. 运行应用

```bash
mvn spring-boot:run
```

### 4. 开始使用

在控制台输入问题，例如：
- "数据库连接超时了，怎么办？"
- "查询user-service最近的错误日志"
- "检查数据库当前连接数"

## 工具列表

1. **querySkills** - 查询SRE运维知识库
2. **executeQuery** - 执行MySQL查询
3. **callHttpEndpoint** - 调用HTTP接口
4. **queryLogs** - 查询ELK日志

## 扩展开发

### 添加新工具

1. 实现 `SRETool` 接口
2. 添加 `@Component` 注解
3. 在Agent配置中注册

示例：

```java
@Component
public class NewTool implements SRETool {
    @Override
    public ToolMetadata getMetadata() {
        return ToolMetadata.builder()
            .name("newTool")
            .description("新工具描述")
            .build();
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        // 实现工具逻辑
        return ToolResult.success("result", 100);
    }
}
```

## 监控和调试

访问以下端点查看监控信息：

- 健康检查：http://localhost:8080/actuator/health
- 性能指标：http://localhost:8080/actuator/metrics

## 许可证

MIT License
```

**Step 2: Commit**

```bash
git add README.md
git commit -m "docs: add README"
```

---

## Task 14: 最终测试和清理

**Step 1: Run all tests**

Run: `mvn clean test`
Expected: All tests pass

**Step 2: Run application**

Run: `mvn spring-boot:run`
Test: 在控制台进行交互测试

**Step 3: Final commit**

```bash
git add .
git commit -m "feat: complete SREmate agent implementation"
```

---

## Summary

本实现计划包含14个主要任务，涵盖：

1. **基础设施层**：追踪、缓存、监控
2. **工具层**：统一接口、现有工具重构、新工具添加
3. **编排层**：工具编排器、并行执行器、结果聚合器
4. **性能优化**：并行调用、流式输出、结果缓存
5. **可观测性**：调用追踪、性能监控、链路可视化
6. **测试和文档**：单元测试、集成测试、使用文档

遵循TDD原则，每个任务都包含：测试先行、最小实现、验证通过、提交代码。
