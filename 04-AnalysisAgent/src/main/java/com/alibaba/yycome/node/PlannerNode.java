package com.alibaba.yycome.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.yycome.enums.StateKeyEnum;
import com.alibaba.yycome.util.PromptTemplateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlannerNode implements NodeAction {

    private final Logger logger = LoggerFactory.getLogger(PlannerNode.class);
    
    private ChatClient plannerAgent;

    public PlannerNode(ChatClient chatClient) {
        this.plannerAgent = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        Map<String, Object> result = new HashMap<>();
        String query = state.value(StateKeyEnum.QUERY.getKey(), "");

        List<Message> messages = new ArrayList<>();
        // 添加系统提示词
        messages.add(PromptTemplateUtil.getPlannerMessage(state));
        // 添加用户问题
        messages.add(new UserMessage(query));

        String content = plannerAgent.prompt().messages(messages).call().content();
        logger.info("planner output:" + content);
//        result.put(StateKeyEnum.PLANNER_CONTENT.getKey(), content);
        result.put(StateKeyEnum.PLANNER_CONTENT.getKey(), "[{'query': '你好'}, {]");

        return result;
    }
}
