package com.yycome.sremate.infrastructure.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 知识库配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "knowledge")
public class KnowledgeProperties {

    private LoaderProperties loader = new LoaderProperties();

    @Data
    public static class LoaderProperties {
        /**
         * 是否启用知识库加载
         */
        private boolean enabled = true;

        /**
         * 知识文档路径列表
         */
        private List<String> paths = List.of();
    }
}
