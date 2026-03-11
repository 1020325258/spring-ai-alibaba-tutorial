package com.yycome.sremate.domain.intent.service;

import com.yycome.sremate.domain.intent.model.PreprocessResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 意图感知的聊天服务
 * 在调用 LLM 前，先进行规则预处理，将预处理结果注入到上下文中
 */
@Slf4j
@Service
public class IntentAwareChatService {

    private final ChatClient sreAgent;
    private final IntentPreprocessor preprocessor;

    public IntentAwareChatService(ChatClient sreAgent, IntentPreprocessor preprocessor) {
        this.sreAgent = sreAgent;
        this.preprocessor = preprocessor;
    }

    /**
     * 发起聊天请求，自动注入预处理结果
     *
     * @param userMessage 用户输入
     * @return LLM 响应
     */
    public String chat(String userMessage) {
        return chat(userMessage, false);
    }

    /**
     * 发起聊天请求
     *
     * @param userMessage      用户输入
     * @param includePreprocess 是否在提示词中包含预处理结果
     * @return LLM 响应
     */
    public String chat(String userMessage, boolean includePreprocess) {
        log.info("[IntentAwareChat] 用户输入: {}", userMessage);

        // 预处理
        PreprocessResult preprocessResult = preprocessor.preprocess(userMessage);
        log.info("[IntentAwareChat] 预处理结果: recommendedTool={}, contractCodes={}, orderIds={}",
                preprocessResult.getRecommendedTool(),
                preprocessResult.getContractCodes(),
                preprocessResult.getOrderIds());

        // 构建增强的用户输入
        String enhancedMessage = buildEnhancedMessage(userMessage, preprocessResult, includePreprocess);

        // 调用 LLM
        String response = sreAgent.prompt()
                .user(enhancedMessage)
                .call()
                .content();

        log.info("[IntentAwareChat] LLM 响应长度: {}", response != null ? response.length() : 0);
        return response;
    }

    /**
     * 获取预处理结果（供外部使用）
     */
    public PreprocessResult getPreprocessResult(String userMessage) {
        return preprocessor.preprocess(userMessage);
    }

    /**
     * 构建增强的用户输入
     * 将预处理结果作为上下文注入
     */
    private String buildEnhancedMessage(String originalMessage, PreprocessResult result, boolean includePreprocess) {
        if (!includePreprocess || result.getRecommendedTool() == null) {
            return originalMessage;
        }

        StringBuilder sb = new StringBuilder();

        // 添加预处理结果作为上下文
        sb.append(result.toSummary());
        sb.append("\n---\n\n");
        sb.append("用户原始问题：").append(originalMessage);

        return sb.toString();
    }

    /**
     * 快速路由：如果预处理结果置信度很高，直接返回推荐信息
     * 用于简单查询场景，减少 LLM 调用开销
     *
     * @return 如果可以快速路由，返回路由信息；否则返回 null
     */
    public QuickRouteResult tryQuickRoute(String userMessage) {
        PreprocessResult result = preprocessor.preprocess(userMessage);

        // 只有当有明确的编号和推荐工具时才快速路由
        if (result.getRecommendedTool() == null) {
            return null;
        }

        // 返回快速路由信息，供调用方决定是否使用
        return new QuickRouteResult(
                result.getRecommendedTool(),
                result.getFirstContractCode(),
                result.getFirstOrderId(),
                result.getRecommendedDataType(),
                result.getKeywords()
        );
    }

    /**
     * 快速路由结果
     */
    public record QuickRouteResult(
            String recommendedTool,
            String contractCode,
            String orderId,
            String dataType,
            java.util.Set<String> keywords
    ) {
        public boolean hasContractCode() {
            return contractCode != null;
        }

        public boolean hasOrderId() {
            return orderId != null;
        }
    }
}
