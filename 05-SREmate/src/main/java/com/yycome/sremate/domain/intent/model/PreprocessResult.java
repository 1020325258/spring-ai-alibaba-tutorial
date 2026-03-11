package com.yycome.sremate.domain.intent.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 意图预处理结果
 * 在 LLM 调用前，通过规则提取的结构化信息
 */
@Data
public class PreprocessResult {

    /** 原始用户输入 */
    private String originalInput;

    /** 识别到的合同编号列表（C前缀） */
    private List<String> contractCodes = new ArrayList<>();

    /** 识别到的订单编号列表（纯数字） */
    private List<String> orderIds = new ArrayList<>();

    /** 识别到的关键词集合 */
    private Set<String> keywords = new HashSet<>();

    /** 推荐的工具名称 */
    private String recommendedTool;

    /** 推荐的数据类型（用于 queryContractData） */
    private String recommendedDataType;

    /** 置信度（0-1，规则预处理通常为 1.0） */
    private double confidence = 1.0;

    // ===== 便捷方法 =====

    public boolean hasContractCode() {
        return !contractCodes.isEmpty();
    }

    public boolean hasOrderId() {
        return !orderIds.isEmpty();
    }

    public String getFirstContractCode() {
        return contractCodes.isEmpty() ? null : contractCodes.get(0);
    }

    public String getFirstOrderId() {
        return orderIds.isEmpty() ? null : orderIds.get(0);
    }

    public boolean hasKeyword(String keyword) {
        return keywords.contains(keyword);
    }

    /**
     * 生成预处理摘要，用于注入 LLM 提示词
     */
    public String toSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("## 用户输入预处理结果\n\n");

        if (hasContractCode()) {
            sb.append("- **识别到的合同编号**: ").append(String.join(", ", contractCodes)).append("\n");
        } else {
            sb.append("- **识别到的合同编号**: 无\n");
        }

        if (hasOrderId()) {
            sb.append("- **识别到的订单编号**: ").append(String.join(", ", orderIds)).append("\n");
        } else {
            sb.append("- **识别到的订单编号**: 无\n");
        }

        if (!keywords.isEmpty()) {
            sb.append("- **识别到的关键词**: ").append(String.join(", ", keywords)).append("\n");
        }

        if (recommendedTool != null) {
            sb.append("- **推荐工具**: ").append(recommendedTool);
            if (recommendedDataType != null) {
                sb.append(" (dataType=").append(recommendedDataType).append(")");
            }
            sb.append("\n");
        }

        sb.append("\n请根据以上信息选择合适的工具执行查询。\n");
        return sb.toString();
    }
}
