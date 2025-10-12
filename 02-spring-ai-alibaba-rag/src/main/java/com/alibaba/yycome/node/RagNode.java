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

        // 获取用户输入
        String query = state.value("query", "");
        Message userMessage = new UserMessage(query);

        // 调用大模型获取结果
        String content = ragAgent.prompt().messages(userMessage).call().content();

        result.put("rag_content", content);

        return result;
    }
}
