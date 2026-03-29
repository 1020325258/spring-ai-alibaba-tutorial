package com.yycome.sreagent.config.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 路由分发器：读取 RouterNode 写入的目标节点名，返回给 StateGraph 做条件路由
 */
public class RouterDispatcher implements EdgeAction {

    private static final Logger log = LoggerFactory.getLogger(RouterDispatcher.class);

    @Override
    public String apply(OverAllState state) throws Exception {
        String target = state.value("routingTarget", "investigateAgent");
        log.info("RouterDispatcher 目标节点: {}", target);
        return target;
    }
}
