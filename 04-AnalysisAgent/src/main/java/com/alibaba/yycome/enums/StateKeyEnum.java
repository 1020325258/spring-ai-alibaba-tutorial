package com.alibaba.yycome.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum StateKeyEnum {

    QUERY("query", "用户问题"),
    AUTO_ACCEPT_PLAN("auto_accept_plan", "是否自动接收计划"),
    FINAL_ANSWER("final_answer", "最终输出"),
    PLANNER_CONTENT("planner_content", "计划节点输出内容"),
    MAX_STEP_NUM("max_step_num", "planner 节点输出计划的最大步骤数"),
    PLAN("plan", "Agent 最终需执行的 Plan"),
    SEARCH_CONTENT("search_content", "SearchNode 节点输出结果"),
    ;

    private final String key;
    private final String desc;
}
