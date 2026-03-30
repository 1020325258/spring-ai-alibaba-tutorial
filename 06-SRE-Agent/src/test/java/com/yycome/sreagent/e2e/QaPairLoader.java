package com.yycome.sreagent.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 问答对加载器
 * 从 YAML 文件加载评估用例
 */
public final class QaPairLoader {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private QaPairLoader() {}

    /**
     * 从 classpath 加载问答对文件
     *
     * @param resourcePath 资源路径，如 "qa-pairs/sre-agent-qa.yaml"
     * @return 问答对列表
     */
    public static List<QaPair> load(String resourcePath) {
        try {
            String content = new ClassPathResource(resourcePath)
                    .getContentAsString(StandardCharsets.UTF_8);
            return parseYaml(content);
        } catch (IOException e) {
            throw new RuntimeException("无法加载问答对文件: " + resourcePath, e);
        }
    }

    /**
     * 解析 YAML 内容为问答对列表
     */
    @SuppressWarnings("unchecked")
    private static List<QaPair> parseYaml(String yamlContent) {
        try {
            Map<String, Object> root = YAML_MAPPER.readValue(yamlContent, Map.class);
            List<Map<String, Object>> qaPairsRaw = (List<Map<String, Object>>) root.get("qa-pairs");

            return qaPairsRaw.stream()
                    .map(QaPairLoader::toQaPair)
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException("解析 YAML 失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static QaPair toQaPair(Map<String, Object> raw) {
        String id = (String) raw.get("id");
        String question = (String) raw.get("question");
        QaPair.Expected expected = toExpected((Map<String, Object>) raw.get("expected"));
        QaPair.Expected also = raw.containsKey("also")
                ? toExpected((Map<String, Object>) raw.get("also"))
                : null;

        return new QaPair(id, question, expected, also);
    }

    @SuppressWarnings("unchecked")
    private static QaPair.Expected toExpected(Map<String, Object> raw) {
        if (raw == null) return null;

        return new QaPair.Expected(
                (String) raw.get("type"),
                (String) raw.get("tool"),
                (Map<String, Object>) raw.get("params"),
                (String) raw.get("queryEntity"),
                (List<String>) raw.get("mustContain")
        );
    }
}
