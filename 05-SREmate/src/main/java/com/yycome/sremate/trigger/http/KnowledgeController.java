package com.yycome.sremate.trigger.http;

import com.yycome.sremate.infrastructure.loader.KnowledgeLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 知识库管理接口
 * 提供手动加载知识库的能力
 */
@Slf4j
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "spring.ai.vectorstore.elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
public class KnowledgeController {

    private final KnowledgeLoader knowledgeLoader;

    /**
     * 手动加载知识库
     * 调用此接口会触发 embedding 模型，消耗额度
     */
    @PostMapping("/load")
    public ResponseEntity<Map<String, Object>> loadKnowledge() {
        log.info("收到手动加载知识库请求");
        try {
            int count = knowledgeLoader.loadKnowledge();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "知识库加载完成",
                    "documentCount", count
            ));
        } catch (Exception e) {
            log.error("知识库加载失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "知识库加载失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 检查知识库状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "loaded", knowledgeLoader.isLoaded()
        ));
    }
}
