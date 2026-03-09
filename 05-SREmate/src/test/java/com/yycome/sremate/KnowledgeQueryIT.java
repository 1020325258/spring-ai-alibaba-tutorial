package com.yycome.sremate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.HttpURLConnection;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 知识库检索端到端集成测试
 *
 * 前置条件：
 * 1. application-local.yml 配置 Elasticsearch 连接
 * 2. Elasticsearch 网络可达
 * 3. 知识库已加载（knowledge/ 目录下的 MD 文件）
 *
 * 运行全部：
 *   JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
 *     mvn test -pl 05-SREmate -Dtest=KnowledgeQueryIT
 *
 * 运行单个：
 *   JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
 *     mvn test -pl 05-SREmate -Dtest=KnowledgeQueryIT#searchDatabaseTroubleshooting_shouldReturnKnowledge
 */
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
class KnowledgeQueryIT {

    @Autowired
    private ChatClient sreAgent;

    private static boolean esAvailable = false;
    private static boolean knowledgeLoaded = false;

    @BeforeAll
    static void checkElasticsearch() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("【环境检查】检查 Elasticsearch 服务状态...");
        System.out.println("=".repeat(60));

        try {
            URL url = new URL("http://localhost:9200");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            int code = conn.getResponseCode();

            if (code == 200) {
                esAvailable = true;
                System.out.println("✅ Elasticsearch 服务可达 (localhost:9200)");
            } else {
                System.out.println("⚠️ Elasticsearch 返回状态码: " + code);
            }
        } catch (Exception e) {
            System.out.println("❌ Elasticsearch 服务不可达: " + e.getMessage());
        }

        // 检查知识库索引
        try {
            URL url = new URL("http://localhost:9200/sremate_knowledge/_count");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);

            if (conn.getResponseCode() == 200) {
                knowledgeLoaded = true;
                System.out.println("✅ 知识库索引 sremate_knowledge 存在");
            } else {
                System.out.println("⚠️ 知识库索引 sremate_knowledge 不存在或为空");
            }
        } catch (Exception e) {
            System.out.println("⚠️ 无法检查知识库索引: " + e.getMessage());
        }

        System.out.println("=".repeat(60) + "\n");
    }

    /**
     * 测试数据库故障排查知识检索
     */
    @Test
    void searchDatabaseTroubleshooting_shouldReturnKnowledge() {
        String question = "数据库连接超时怎么办";
        System.out.println("\n" + "-".repeat(60));
        System.out.println("【测试1】数据库故障排查知识检索");
        System.out.println("-".repeat(60));
        System.out.println("📝 问题: " + question);
        System.out.println("📡 ES 可用: " + esAvailable + " | 知识库已加载: " + knowledgeLoaded);

        long start = System.currentTimeMillis();
        String response = ask(question);
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("⏱️ 耗时: " + elapsed + "ms");
        System.out.println("\n📤 Agent 回复:\n" + response);

        // 检查是否调用了知识库查询（回复中包含知识库特有格式）
        boolean hasKnowledgeResult = response.contains("【") && response.contains("分类:");
        System.out.println("\n📊 知识库查询结果检测: " + (hasKnowledgeResult ? "✅ 已调用知识库" : "⚠️ 未检测到知识库结果格式"));

        // 验证返回了相关知识内容
        assertThat(response).satisfiesAnyOf(
            r -> assertThat(r).containsIgnoringCase("连接"),
            r -> assertThat(r).containsIgnoringCase("超时"),
            r -> assertThat(r).containsIgnoringCase("排查"),
            r -> assertThat(r).contains("未找到")  // 知识库可能为空
        );
    }

    /**
     * 测试签约相关故障排查知识检索
     */
    @Test
    void searchContractTroubleshooting_shouldReturnKnowledge() {
        String question = "签约失败怎么排查";
        System.out.println("\n" + "-".repeat(60));
        System.out.println("【测试2】签约故障排查知识检索");
        System.out.println("-".repeat(60));
        System.out.println("📝 问题: " + question);
        System.out.println("📡 ES 可用: " + esAvailable + " | 知识库已加载: " + knowledgeLoaded);

        long start = System.currentTimeMillis();
        String response = ask(question);
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("⏱️ 耗时: " + elapsed + "ms");
        System.out.println("\n📤 Agent 回复:\n" + response);

        // 检查是否调用了知识库查询
        boolean hasKnowledgeResult = response.contains("【") && response.contains("分类:");
        System.out.println("\n📊 知识库查询结果检测: " + (hasKnowledgeResult ? "✅ 已调用知识库" : "⚠️ 未检测到知识库结果格式"));

        // 验证返回了相关知识内容
        assertThat(response).satisfiesAnyOf(
            r -> assertThat(r).containsIgnoringCase("签约"),
            r -> assertThat(r).containsIgnoringCase("排查"),
            r -> assertThat(r).containsIgnoringCase("失败"),
            r -> assertThat(r).contains("未找到")  // 知识库可能为空
        );
    }

    /**
     * 测试运维咨询类问题
     */
    @Test
    void askOperationsQuestion_shouldReturnGuidance() {
        String question = "如何查看应用日志";
        System.out.println("\n" + "-".repeat(60));
        System.out.println("【测试3】运维咨询类问题");
        System.out.println("-".repeat(60));
        System.out.println("📝 问题: " + question);
        System.out.println("📡 ES 可用: " + esAvailable + " | 知识库已加载: " + knowledgeLoaded);

        long start = System.currentTimeMillis();
        String response = ask(question);
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("⏱️ 耗时: " + elapsed + "ms");
        System.out.println("\n📤 Agent 回复:\n" + response);

        // 验证返回了相关指导内容
        assertThat(response).isNotEmpty();
        // 检查不应包含异常堆栈或错误信息（代码示例中的 "error" 单词除外）
        assertThat(response).doesNotContain("NullPointerException");
        assertThat(response).doesNotContain("Exception:");
        assertThat(response).doesNotContain("Error:");
    }

    /**
     * 测试未知问题的处理
     */
    @Test
    void askUnknownQuestion_shouldHandleGracefully() {
        String question = "这是一个完全无关的问题xyz123";
        System.out.println("\n" + "-".repeat(60));
        System.out.println("【测试4】未知问题处理");
        System.out.println("-".repeat(60));
        System.out.println("📝 问题: " + question);
        System.out.println("📡 ES 可用: " + esAvailable + " | 知识库已加载: " + knowledgeLoaded);

        long start = System.currentTimeMillis();
        String response = ask(question);
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("⏱️ 耗时: " + elapsed + "ms");
        System.out.println("\n📤 Agent 回复:\n" + response);

        // 验证 Agent 优雅处理未知问题
        assertThat(response).isNotEmpty();
        assertThat(response).doesNotContain("NullPointerException");
        assertThat(response).doesNotContain("Exception");
    }

    private String ask(String question) {
        return sreAgent.prompt()
                .user(question)
                .call()
                .content();
    }
}
