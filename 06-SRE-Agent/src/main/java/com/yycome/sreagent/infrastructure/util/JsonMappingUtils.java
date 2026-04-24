package com.yycome.sreagent.infrastructure.util;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON 字段映射工具类
 * 用于从 JsonNode 中提取字段值，统一处理空值和类型转换
 */
public final class JsonMappingUtils {

    private JsonMappingUtils() {
    }

    /**
     * 获取字符串字段值
     *
     * @param node JSON 节点
     * @param path 字段路径，支持嵌套如 "parent.child"
     * @return 字段值，缺失时返回 null
     */
    public static String getText(JsonNode node, String path) {
        JsonNode field = getNestedNode(node, path);
        if (field.isMissingNode() || field.isNull()) {
            return null;
        }
        return field.asText(null);
    }

    /**
     * 获取整数字段值
     *
     * @param node JSON 节点
     * @param path 字段路径
     * @return 字段值，缺失时返回 null
     */
    public static Integer getInt(JsonNode node, String path) {
        JsonNode field = getNestedNode(node, path);
        if (field.isMissingNode() || field.isNull()) {
            return null;
        }
        return field.asInt();
    }

    /**
     * 获取长整数字段值
     *
     * @param node JSON 节点
     * @param path 字段路径
     * @return 字段值，缺失时返回 null
     */
    public static Long getLong(JsonNode node, String path) {
        JsonNode field = getNestedNode(node, path);
        if (field.isMissingNode() || field.isNull()) {
            return null;
        }
        return field.asLong();
    }

    /**
     * 获取浮点数字段值
     *
     * @param node JSON 节点
     * @param path 字段路径
     * @return 字段值，缺失时返回 null
     */
    public static Double getDouble(JsonNode node, String path) {
        JsonNode field = getNestedNode(node, path);
        if (field.isMissingNode() || field.isNull()) {
            return null;
        }
        return field.asDouble();
    }

    /**
     * 获取布尔字段值
     *
     * @param node JSON 节点
     * @param path 字段路径
     * @return 字段值，缺失时返回 null
     */
    public static Boolean getBoolean(JsonNode node, String path) {
        JsonNode field = getNestedNode(node, path);
        if (field.isMissingNode() || field.isNull()) {
            return null;
        }
        return field.asBoolean();
    }

    /**
     * 获取时间戳字段并格式化为字符串
     *
     * @param node JSON 节点
     * @param path 字段路径
     * @return 格式化后的时间字符串，缺失时返回 null
     */
    public static String formatDateTime(JsonNode node, String path) {
        JsonNode field = getNestedNode(node, path);
        if (field.isMissingNode() || field.isNull()) {
            return null;
        }
        // 支持时间戳（毫秒）或数字字符串
        if (field.isNumber()) {
            return DateTimeUtil.format(field.asLong());
        }
        return DateTimeUtil.format(field.asText(null));
    }

    /**
     * 获取嵌套节点
     * 支持 "parent.child" 格式的路径
     *
     * @param node 根节点
     * @param path 字段路径
     * @return 目标节点，缺失时返回 MissingNode
     */
    public static JsonNode getNestedNode(JsonNode node, String path) {
        if (node == null || path == null || path.isEmpty()) {
            return node.path(path);
        }

        JsonNode current = node;
        for (String segment : path.split("\\.")) {
            if (current == null || current.isMissingNode()) {
                break;
            }
            current = current.path(segment);
        }
        return current;
    }

    /**
     * 创建有序 Map，用于构建返回结果
     */
    public static Map<String, Object> newOrderedMap() {
        return new LinkedHashMap<>();
    }
}
