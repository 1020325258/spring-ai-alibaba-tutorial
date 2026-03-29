package com.yycome.sreagent.config.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.Map;

/**
 * 路由节点：LLM 判断用户意图，将目标节点名写入 state["routingTarget"]
 */
public class RouterNode implements NodeAction {

    private static final Logger log = LoggerFactory.getLogger(RouterNode.class);

    static final String ROUTER_PROMPT = """
            你是一个路由器，根据用户问题判断应该使用哪个 Agent 处理。

            回复规则：
            - 用户想查询数据（订单、合同、节点、报价等）→ 只回复 "query"
            - 用户想排查问题（排查工单、诊断异常、弹窗提示等）→ 只回复 "investigate"
            - 用户询问或切换运行环境（当前环境、切换到xxx环境）→ 只回复 "admin"

            用户问题: %s

            注意：只回复一个单词，不要其他文字。
            """;

    private final ChatModel chatModel;

    public RouterNode(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String input = state.value(OverAllState.DEFAULT_INPUT_KEY, "");
        log.info("RouterNode 收到 input: {}", input);

        String fullPrompt = String.format(ROUTER_PROMPT, input);
        var response = chatModel.call(new Prompt(fullPrompt));
        String result = response.getResult().getOutput().getText().trim().toLowerCase();

        String target = result.contains("admin") ? "admin"
                : result.contains("query") ? "queryAgent" : "investigateAgent";
        log.info("RouterNode routingTarget: {}", target);

        return Map.of("routingTarget", target);
    }
}
