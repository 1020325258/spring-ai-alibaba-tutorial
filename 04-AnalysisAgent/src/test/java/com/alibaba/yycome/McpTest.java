package com.alibaba.yycome;

import com.alibaba.cloud.ai.toolcalling.aliyunaisearch.AliyunAiSearchService;
import com.alibaba.cloud.ai.toolcalling.baidusearch.BaiduAiSearchService;
import com.alibaba.cloud.ai.toolcalling.common.interfaces.SearchService;
import com.alibaba.fastjson.JSON;
import com.alibaba.yycome.entity.SearchResult;
import com.alibaba.yycome.service.McpService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class McpTest {

    @Autowired
    McpService mcpService;

    @Autowired
    ChatClient searchAgent;

    @Test
    public void test() {
        String query = "明确 MySQL 支持的事务隔离级别（READ UNCOMMITTED、READ COMMITTED、REPEATABLE READ、SERIALIZABLE），收集每种隔离级别的定义、行为特征、解决的并发问题（脏读、不可重复读、幻读）以及 MySQL 默认隔离级别。同时调研 InnoDB 引擎对各隔离级别的具体实现机制（如 MVCC、Next-Key Lock 等）。";

        System.out.println(searchAgent.prompt(query).call().content());

        String query2 = "\"收集面试中关于事务隔离级别的常见错误回答（如混淆幻读与不可重复读、误认为 REPEATABLE READ 能完全避免幻读）、典型变体问题（如‘RR 级别下是否会出现幻读？’‘如何手动避免幻读？’），并分析面试官通过此题考察的维度（数据库基础、并发控制理解、实战调优意识）。";
        System.out.println(searchAgent.prompt(query2).call().content());

    }

    @Test
    public void testMcpService() throws JsonProcessingException {
        List<SearchResult> query = mcpService.query("mysql 的面试题有哪些？");
        query.stream().forEach(System.out::println);
    }

    @Autowired
    private AliyunAiSearchService searchService;

    /**
     * 需从阿里云申请 api-key，网址：https://opensearch.console.aliyun.com/cn-shanghai/rag/experience-center?serverType=web-search&serverId=ops-web-search-001
     * 阿里云提供的 search 能力较弱
     */
    @Test
    public void testAliyunSearch() {
        SearchService.SearchResult searchResult = searchService.query("mysql 的面试题有哪些？").getSearchResult();
        searchResult.results().stream().forEach(System.out::println);
    }
}
