package com.alibaba.yycome.dispatcher;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.alibaba.yycome.enums.StateKeyEnum;

public class PlanAcceptDispatcher implements EdgeAction {
    @Override
    public String apply(OverAllState state) throws Exception {
        return state.value(StateKeyEnum.PLAN_ACCEPT_NEXT_NODE.getKey(), "search_node");
    }
}
