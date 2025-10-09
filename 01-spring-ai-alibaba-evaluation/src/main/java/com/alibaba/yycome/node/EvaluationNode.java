package com.alibaba.yycome.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.HashMap;
import java.util.Map;

public class EvaluationNode implements NodeAction {

    public ChatClient evaluationAgent;

    public EvaluationNode(ChatClient evaluationAgent) {
        this.evaluationAgent = evaluationAgent;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        Map<String, Object> result = new HashMap<>();
        String query = state.value("query", "");
        String answer = state.value("answer", "");
        UserMessage userMessage = new UserMessage(
                        "用户输入的问题为:" + query + "\n"
                + "生成的内容为:" + answer
        );
        String content = evaluationAgent.prompt().messages(userMessage).call().content();
        result.put("evaluation_content", content);
        return result;
    }
}
