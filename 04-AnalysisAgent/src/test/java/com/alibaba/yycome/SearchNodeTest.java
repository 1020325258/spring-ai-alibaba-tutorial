package com.alibaba.yycome;

import junit.framework.TestCase;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Unit test for simple App.
 */
@SpringBootTest
public class SearchNodeTest extends TestCase {

    @Autowired
    private ChatClient searchAgent;

   @Test
    public void test() {
       String userInput1 = "mysql的面试题有哪些";
       System.out.println(searchAgent.prompt(userInput1).call().content());
   }
}
