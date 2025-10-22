package com.alibaba.yycome;

import com.alibaba.yycome.service.McpService;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class McpTest {

    @Autowired
    McpService mcpService;

    @Test
    public void test() {
        List<McpSchema.Content> query = mcpService.query("mysql 的面试题有哪些?");

        McpSchema.Content content = query.getLast();
        if ("text".equals(content.type())) {
            content = (McpSchema.TextContent) content;
            System.out.println(content);
        }
    }

}
