package com.yycome.sreagent.config.node.routing;

/**
 * 意图路由策略接口。
 * RouterNode 委托此接口完成路由决策，实现可替换（LLM Prompt / Embedding 等）。
 */
public interface SkillRoutingStrategy {

    /**
     * 根据用户输入返回路由目标。
     *
     * @param userInput 用户原始输入
     * @return Skill name（如 "sales-contract-sign-dialog-diagnosis"）、
     *         "query"、"admin"、"unclear" 之一
     */
    String route(String userInput);
}
