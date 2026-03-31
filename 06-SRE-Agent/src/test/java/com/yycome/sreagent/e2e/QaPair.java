package com.yycome.sreagent.e2e;

/**
 * 问答对数据结构
 * 用于从 YAML 加载评估用例
 *
 * @param id        唯一标识
 * @param question  用户问题（输入给 Agent）
 * @param expected  期望输出的自然语言描述
 */
public record QaPair(
    String id,
    String question,
    String expected
) {}
