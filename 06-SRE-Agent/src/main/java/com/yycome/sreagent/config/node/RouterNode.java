package com.yycome.sreagent.config.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.yycome.sreagent.config.node.routing.LlmSkillRoutingStrategy;
import com.yycome.sreagent.config.node.routing.SkillRoutingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 路由节点：委托 SkillRoutingStrategy 判断用户意图，将目标节点名写入 state["routingTarget"]。
 * 若路由结果为 Skill name，同时写入 state["selectedSkill"]。
 */
public class RouterNode implements NodeAction {

    private static final Logger log = LoggerFactory.getLogger(RouterNode.class);

    private final SkillRoutingStrategy strategy;

    public RouterNode(SkillRoutingStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String input = state.value(OverAllState.DEFAULT_INPUT_KEY, "");
        log.info("RouterNode 收到 input: {}", input);

        String result = strategy.route(input);

        Map<String, Object> updates = new HashMap<>();

        if (strategy instanceof LlmSkillRoutingStrategy llmStrategy && llmStrategy.isSkillName(result)) {
            // 路由到具体 Skill → investigateAgent
            updates.put("routingTarget", "investigateAgent");
            updates.put("selectedSkill", result);
            log.info("RouterNode → investigateAgent, selectedSkill: {}", result);
        } else if (result.contains("query")) {
            updates.put("routingTarget", "queryAgent");
            log.info("RouterNode → queryAgent");
        } else {
            // admin / unclear / 其他 fallback → admin
            updates.put("routingTarget", "admin");
            log.info("RouterNode → admin (result={})", result);
        }

        return updates;
    }
}
