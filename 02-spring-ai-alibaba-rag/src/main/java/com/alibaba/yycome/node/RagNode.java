package com.alibaba.yycome.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.HashMap;
import java.util.Map;

public class RagNode implements NodeAction {

    private ChatClient ragAgent;

    public RagNode(ChatClient ragAgent) {
        this.ragAgent = ragAgent;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {

        Map<String, Object> result = new HashMap<>();

        Message userMessage = new UserMessage(
                """
                        sql 的更新流程是什么？
                        """
        );
        String content = ragAgent.prompt().messages(userMessage).call().content();

        result.put("rag_content", content);

        return result;
    }
}
