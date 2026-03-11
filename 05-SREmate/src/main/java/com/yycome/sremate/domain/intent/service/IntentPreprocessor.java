package com.yycome.sremate.domain.intent.service;

import com.yycome.sremate.domain.intent.model.PreprocessResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 意图预处理器
 * 在 LLM 调用前，通过确定性规则提取用户输入中的结构化信息
 */
@Slf4j
@Service
public class IntentPreprocessor {

    /** 合同编号正则：C前缀 + 数字 */
    private static final Pattern CONTRACT_CODE_PATTERN = Pattern.compile("\\bC\\d+\\b", Pattern.CASE_INSENSITIVE);

    /** 订单号正则：纯数字，至少15位 */
    private static final Pattern ORDER_ID_PATTERN = Pattern.compile("\\b\\d{15,}\\b");

    /** 报价单号正则：GBILL前缀 + 数字 */
    private static final Pattern QUOTATION_NO_PATTERN = Pattern.compile("\\bGBILL\\d+\\b", Pattern.CASE_INSENSITIVE);

    /** 关键词到工具的映射 */
    private static final Set<String> FORM_ID_KEYWORDS = Set.of("版式", "form_id", "版式数据", "版式ID", "formId");
    private static final Set<String> SUB_ORDER_KEYWORDS = Set.of("子单", "S单", "子单信息", "子订单");
    private static final Set<String> CONTRACT_CONFIG_KEYWORDS = Set.of("配置表", "合同配置", "配置数据");
    private static final Set<String> NODE_KEYWORDS = Set.of("节点", "日志", "节点数据", "操作日志", "合同日志");
    private static final Set<String> FIELD_KEYWORDS = Set.of("字段", "字段数据");
    private static final Set<String> USER_KEYWORDS = Set.of("签约人", "参与人", "合同用户", "签署人");

    /** 运维诊断关键词 */
    private static final Set<String> DIAGNOSIS_KEYWORDS = Set.of(
            "超时", "报错", "异常", "错误", "失败", "崩溃",
            "连接", "数据库", "服务", "内存", "CPU", "磁盘"
    );

    /**
     * 预处理用户输入
     */
    public PreprocessResult preprocess(String userInput) {
        log.debug("预处理用户输入: {}", userInput);

        PreprocessResult result = new PreprocessResult();
        result.setOriginalInput(userInput);

        // 1. 提取编号
        extractIds(userInput, result);

        // 2. 提取关键词
        extractKeywords(userInput, result);

        // 3. 推荐工具和数据类型
        recommendTool(result);

        log.debug("预处理结果: contractCodes={}, orderIds={}, keywords={}, recommendedTool={}",
                result.getContractCodes(), result.getOrderIds(), result.getKeywords(), result.getRecommendedTool());

        return result;
    }

    /**
     * 提取用户输入中的各类编号
     */
    private void extractIds(String input, PreprocessResult result) {
        String upperInput = input.toUpperCase();

        // 提取合同编号（C前缀）
        Matcher contractMatcher = CONTRACT_CODE_PATTERN.matcher(upperInput);
        while (contractMatcher.find()) {
            String code = contractMatcher.group().toUpperCase();
            if (!result.getContractCodes().contains(code)) {
                result.getContractCodes().add(code);
            }
        }

        // 提取订单号（纯数字，排除合同号中的数字部分）
        Matcher orderMatcher = ORDER_ID_PATTERN.matcher(input);
        while (orderMatcher.find()) {
            String orderId = orderMatcher.group();
            // 排除已经是合同号一部分的数字
            boolean isPartOfContractCode = result.getContractCodes().stream()
                    .anyMatch(cc -> cc.substring(1).equals(orderId) || cc.contains(orderId));
            if (!isPartOfContractCode && !result.getOrderIds().contains(orderId)) {
                result.getOrderIds().add(orderId);
            }
        }

        // 提取报价单号（用于子单查询）
        Matcher quotationMatcher = QUOTATION_NO_PATTERN.matcher(upperInput);
        while (quotationMatcher.find()) {
            String quotationNo = quotationMatcher.group().toUpperCase();
            result.getKeywords().add("quotationNo:" + quotationNo);
        }
    }

    /**
     * 提取关键词
     */
    private void extractKeywords(String input, PreprocessResult result) {
        // 合同数据类型关键词
        if (containsAny(input, NODE_KEYWORDS)) {
            result.getKeywords().add("CONTRACT_NODE");
        }
        if (containsAny(input, FIELD_KEYWORDS)) {
            result.getKeywords().add("CONTRACT_FIELD");
        }
        if (containsAny(input, USER_KEYWORDS)) {
            result.getKeywords().add("CONTRACT_USER");
        }

        // 特殊工具关键词
        if (containsAny(input, FORM_ID_KEYWORDS)) {
            result.getKeywords().add("FORM_ID");
        }
        if (containsAny(input, SUB_ORDER_KEYWORDS)) {
            result.getKeywords().add("SUB_ORDER");
        }
        if (containsAny(input, CONTRACT_CONFIG_KEYWORDS)) {
            result.getKeywords().add("CONTRACT_CONFIG");
        }

        // 运维诊断关键词
        if (containsAny(input, DIAGNOSIS_KEYWORDS)) {
            result.getKeywords().add("DIAGNOSIS");
        }

        // 检查是否有"合同数据"等通用查询意图
        if (input.contains("合同数据") || input.contains("合同详情") || input.contains("合同信息")) {
            result.getKeywords().add("CONTRACT_ALL");
        }
    }

    /**
     * 根据预处理结果推荐工具
     */
    private void recommendTool(PreprocessResult result) {
        // 优先级 1: 版式查询
        if (result.hasKeyword("FORM_ID") && result.hasContractCode()) {
            result.setRecommendedTool("queryContractFormId");
            return;
        }

        // 优先级 2: 子单查询
        if (result.hasKeyword("SUB_ORDER") && result.hasOrderId()) {
            result.setRecommendedTool("querySubOrderInfo");
            return;
        }

        // 优先级 3: 合同配置查询
        if (result.hasKeyword("CONTRACT_CONFIG")) {
            result.setRecommendedTool("queryContractConfig");
            return;
        }

        // 优先级 4: 合同数据查询（C前缀）
        if (result.hasContractCode()) {
            result.setRecommendedTool("queryContractData");

            // 根据关键词推荐 dataType
            if (result.hasKeyword("CONTRACT_NODE")) {
                result.setRecommendedDataType("CONTRACT_NODE");
            } else if (result.hasKeyword("CONTRACT_FIELD")) {
                result.setRecommendedDataType("CONTRACT_FIELD");
            } else if (result.hasKeyword("CONTRACT_USER")) {
                result.setRecommendedDataType("CONTRACT_USER");
            } else {
                result.setRecommendedDataType("ALL");
            }
            return;
        }

        // 优先级 5: 订单合同查询（纯数字）
        if (result.hasOrderId()) {
            result.setRecommendedTool("queryContractsByOrderId");
            return;
        }

        // 优先级 6: 运维诊断
        if (result.hasKeyword("DIAGNOSIS")) {
            result.setRecommendedTool("querySkills");
            return;
        }

        // 无明确推荐，由 LLM 决策
        result.setRecommendedTool(null);
    }

    /**
     * 检查输入是否包含任意关键词
     */
    private boolean containsAny(String input, Set<String> keywords) {
        return keywords.stream().anyMatch(input::contains);
    }
}
