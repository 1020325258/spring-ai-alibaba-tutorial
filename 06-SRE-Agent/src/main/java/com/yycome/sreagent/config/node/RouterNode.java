package com.yycome.sreagent.config.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.HashMap;
import java.util.Map;

/**
 * 路由节点：调用 LLM 判断用户意图，路由到对应 Agent。
 *
 * 三种路由目标：
 * - queryAgent：查询意图，用户想查看/获取数据
 * - investigateAgent：排查意图，用户反馈异常症状或想诊断问题
 * - admin：需要引导，用户意图不明确
 */
public class RouterNode implements NodeAction {

    private static final Logger log = LoggerFactory.getLogger(RouterNode.class);

    private static final String ROUTER_PROMPT = """
            你是一个路由器，判断用户意图属于哪一类：

            ## 意图分类

            **query** - 查询意图，用户想查看/获取数据
            - 查订单、查合同、查节点、查报价单
            - 弹窗有哪些S单、合同的签约单据
            - 某个实体/模型的数据详情

            **investigate** - 排查意图，用户反馈异常症状或想诊断问题
            - 弹窗提示XXX、缺少XXX、无XXX
            - 为什么XXX、排查XXX、诊断XXX
            - 某功能不工作、某数据异常

            **admin** - 需要引导，用户意图不明确或需要帮助
            - 输入太短、表述模糊
            - 询问系统配置、环境、怎么做

            ## 用户问题
            %s

            注意：只回复一个单词（query / investigate / admin），不要其他文字。
            """;

    private final ChatModel chatModel;

    public RouterNode(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String input = state.value(OverAllState.DEFAULT_INPUT_KEY, "");
        log.info("RouterNode 收到 input: {}", input);

        String result = routeByLLM(input);

        Map<String, Object> updates = new HashMap<>();
        updates.put("routingTarget", result);
        log.info("RouterNode → {}", result);

        return updates;
    }

    private String routeByLLM(String userInput) {
        String fullPrompt = String.format(ROUTER_PROMPT, userInput);
        var response = chatModel.call(new Prompt(fullPrompt));
        String result = response.getResult().getOutput().getText().trim().toLowerCase();

        // 校验返回值，默认走 admin
        if (result.equals("query") || result.equals("investigate")) {
            return result;
        }
        return "admin";
    }
}
