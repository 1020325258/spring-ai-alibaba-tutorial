package com.yycome.sreagent.e2e;

import java.util.List;
import java.util.Map;

/**
 * 问答对数据结构
 * 用于从 YAML 加载评估用例
 */
public record QaPair(
    String id,
    String question,
    Expected expected,
    Expected also
) {
    /**
     * 期望结果定义
     */
    public record Expected(
        String type,
        String tool,
        Map<String, Object> params,
        String queryEntity,
        List<String> mustContain
    ) {
        /**
         * 是否为工具调用验证类型
         */
        public boolean isToolCallType() {
            return "tool_call".equals(type);
        }

        /**
         * 是否为 JSON 输出验证类型
         */
        public boolean isJsonOutputType() {
            return "json_output".equals(type);
        }

        /**
         * 是否为自然语言验证类型
         */
        public boolean isNaturalLanguageType() {
            return "natural_language".equals(type);
        }
    }
}
