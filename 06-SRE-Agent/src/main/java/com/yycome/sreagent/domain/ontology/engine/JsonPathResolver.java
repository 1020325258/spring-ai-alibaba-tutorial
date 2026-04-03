package com.yycome.sreagent.domain.ontology.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 基于 Jackson JsonNode 的轻量级路径解析器
 * 支持简单字段、嵌套字段、数组展平、多数组合并、查询参数注入
 *
 * Source 语法：
 * - 简单字段: "fieldName"
 * - 嵌套字段: "parent.child"
 * - 数组元素: "data[].field" (取数组每项的 field)
 * - 数组展平: "data[].items[]" (末尾 [] 表示展平为多条记录)
 * - 多数组合并: "listA[].field,listB[].field"
 * - 查询参数: "$param.fieldName"
 */
@Slf4j
public class JsonPathResolver {

    private final JsonNode root;
    private final Map<String, Object> queryParams;
    private final ObjectMapper objectMapper;

    public JsonPathResolver(JsonNode root, Map<String, Object> queryParams) {
        this.root = root;
        this.queryParams = queryParams != null ? queryParams : Collections.emptyMap();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 获取单个字段值
     */
    public Object get(String source) {
        if (source == null || source.isEmpty()) {
            return null;
        }

        // 查询参数注入
        if (source.startsWith("$param.")) {
            String paramName = source.substring("$param.".length());
            return queryParams.get(paramName);
        }

        // 解析路径（简单实现）
        JsonNode node = findNode(source);
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }

        // 根据类型返回对应的值
        if (node.isObject()) {
            return convertValue(node);
        } else if (node.isArray()) {
            return convertValueAsList(node);
        } else if (node.isTextual()) {
            return node.asText();
        } else if (node.isNumber()) {
            return node.asDouble();
        } else if (node.isBoolean()) {
            return node.asBoolean();
        }

        return null;
    }

    /**
     * 数组展平（路径末尾为 []）
     * 例如：data[].signableOrderInfos[]
     */
    public List<Map<String, Object>> flatten(String source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }

        // 查询参数不参与展平
        if (source.startsWith("$param.")) {
            return Collections.emptyList();
        }

        // 必须以 [] 结尾
        if (!source.endsWith("[]")) {
            return Collections.emptyList();
        }

        // 去掉末尾的 []
        String path = source.substring(0, source.length() - 2); // "data[].items"

        // 找最后一个 [] 的位置（实际上应该找第一个，因为外层 [] 之后才是真正的字段）
        // 但我们的语法是 "data[].items[]"，最后一个 [] 是展平标记
        // 外层数组是 "data[]"，内层字段是 "items"

        // 找到第一个 [] 后面紧跟的内容（作为内层字段）
        int firstDoubleBracket = path.indexOf("[]");
        if (firstDoubleBracket < 0) {
            return Collections.emptyList();
        }

        // 外层数组路径："data"
        String outerPath = path.substring(0, firstDoubleBracket);
        // 内层字段名：从 "[]." 之后开始，即跳过 "[]."
        String innerField = path.substring(firstDoubleBracket + 2);

        // 修正：如果 innerField 前面有 "."，去掉它
        if (innerField.startsWith(".")) {
            innerField = innerField.substring(1);
        }

        // 直接使用 Jackson 的 path 方法更可靠
        JsonNode outerArray = root.path(outerPath);

        if (!outerArray.isArray()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> result = new ArrayList<>();

        for (JsonNode outerItem : outerArray) {
            if (!outerItem.isObject()) continue;

            JsonNode innerArray = outerItem.path(innerField);
            if (!innerArray.isArray()) continue;

            for (JsonNode innerItem : innerArray) {
                if (innerItem.isObject()) {
                    result.add(convertValue(innerItem));
                }
            }
        }

        return result;
    }

    /**
     * 带继承的数组展平
     * 用于展平内层数组，同时继承外层的字段
     *
     * 支持三种格式：
     * 1. 单层数组：`data[]` - 直接展平数组元素
     * 2. 对象路径数组：`data.personalContractDataList[]` - 沿路径找到数组后展平
     * 3. 嵌套数组：`data[].signableOrderInfos[]` - 展平内层数组，继承外层字段
     */
    public List<Map<String, Object>> flattenWithInheritance(String... paths) {
        if (paths == null || paths.length == 0) {
            return Collections.emptyList();
        }

        // 找出内层路径（以 [] 结尾的）
        String innerPath = null;
        List<String> outerPaths = new ArrayList<>();

        for (String path : paths) {
            if (path.endsWith("[]")) {
                innerPath = path;
            } else {
                outerPaths.add(path);
            }
        }

        if (innerPath == null) {
            return Collections.emptyList();
        }

        // 去掉末尾的 []
        String pathWithoutTrailingBrackets = innerPath.substring(0, innerPath.length() - 2);

        // 判断格式类型
        int firstBracketPos = pathWithoutTrailingBrackets.indexOf("[]");

        if (firstBracketPos < 0) {
            // 格式 1 或 2：没有中间的 []，说明是单层数组或对象路径数组
            // data[] → pathWithoutTrailingBrackets = "data"
            // data.personalContractDataList[] → pathWithoutTrailingBrackets = "data.personalContractDataList"
            return flattenSimpleArray(pathWithoutTrailingBrackets);
        }

        // 格式 3：嵌套数组 data[].signableOrderInfos[]
        return flattenNestedArray(pathWithoutTrailingBrackets, firstBracketPos, outerPaths);
    }

    /**
     * 展平单层数组或对象路径中的数组
     * 支持: `data[]` 或 `data.personalContractDataList[]`
     */
    private List<Map<String, Object>> flattenSimpleArray(String arrayPath) {
        JsonNode arrayNode = findNode(arrayPath);
        if (arrayNode == null || !arrayNode.isArray()) {
            log.debug("[flattenSimpleArray] path={} is not an array", arrayPath);
            return Collections.emptyList();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode item : arrayNode) {
            if (item.isObject()) {
                result.add(convertValue(item));
            }
        }
        log.debug("[flattenSimpleArray] path={} returned {} items", arrayPath, result.size());
        return result;
    }

    /**
     * 展平嵌套数组（外层数组元素包含内层数组）
     * 支持: `data[].signableOrderInfos[]`
     */
    private List<Map<String, Object>> flattenNestedArray(String pathWithoutTrailingBrackets,
                                                          int firstBracketPos,
                                                          List<String> outerPaths) {
        String outerBase = pathWithoutTrailingBrackets.substring(0, firstBracketPos);
        String innerField = pathWithoutTrailingBrackets.substring(firstBracketPos + 2);
        if (innerField.startsWith(".")) {
            innerField = innerField.substring(1);
        }

        // 获取外层数组
        JsonNode outerArray = findNode(outerBase);
        log.debug("[flattenNestedArray] outerBase={}, isArray={}", outerBase, outerArray != null && outerArray.isArray());
        if (outerArray == null || !outerArray.isArray()) {
            return Collections.emptyList();
        }

        // 提取外层字段
        Map<String, List<String>> outerFieldValues = new LinkedHashMap<>();
        if (outerPaths.isEmpty()) {
            // 自动从外层数组的第一个元素提取所有字段名作为外层字段
            if (outerArray.size() > 0) {
                JsonNode firstItem = outerArray.get(0);
                if (firstItem.isObject()) {
                    firstItem.fields().forEachRemaining(entry -> {
                        JsonNode value = entry.getValue();
                        if (value != null && !value.isArray() && !value.isObject()) {
                            String fieldName = entry.getKey();
                            List<String> values = extractFieldFromArray(outerArray, fieldName);
                            outerFieldValues.put(fieldName, values);
                        }
                    });
                }
            }
        } else {
            for (String outerPath : outerPaths) {
                String fieldName = extractFieldName(outerPath);
                List<String> values = extractFieldFromArray(outerArray, fieldName);
                outerFieldValues.put(fieldName, values);
            }
        }

        // 遍历并构建结果
        List<Map<String, Object>> result = new ArrayList<>();

        for (int i = 0; i < outerArray.size(); i++) {
            JsonNode outerItem = outerArray.get(i);
            if (!outerItem.isObject()) continue;

            JsonNode innerArray = outerItem.path(innerField);
            if (!innerArray.isArray()) continue;

            for (JsonNode innerItem : innerArray) {
                if (!innerItem.isObject()) continue;

                Map<String, Object> record = new LinkedHashMap<>();

                // 添加外层字段
                for (Map.Entry<String, List<String>> entry : outerFieldValues.entrySet()) {
                    if (i < entry.getValue().size()) {
                        record.put(entry.getKey(), entry.getValue().get(i));
                    }
                }

                // 添加内层字段
                record.putAll(convertValue(innerItem));

                result.add(record);
            }
        }

        return result;
    }

    /**
     * 多数组合并
     * 支持: "listA[].field,listB[].field"
     */
    public List<Map<String, Object>> merge(String... sources) {
        if (sources == null || sources.length == 0) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> result = new ArrayList<>();

        for (String source : sources) {
            // 解析：bills[].billCode → arrayPath = "bills", fieldName = "billCode"
            // 或者: bills[].fieldName[] → 需要去掉末尾的 []
            String withoutTrailing = source;

            // 如果以 [] 结尾（展平标记），去掉它
            if (source.endsWith("[]")) {
                withoutTrailing = source.substring(0, source.length() - 2);
            }

            // 找到第一个 [] 的位置
            int bracketPos = withoutTrailing.indexOf("[]");
            if (bracketPos < 0) {
                // 没有 []，尝试作为简单路径处理
                JsonNode node = findNode(source);
                if (node != null && !node.isNull() && !node.isMissingNode()) {
                    Map<String, Object> record = new LinkedHashMap<>();
                    record.put(source, getValueAsObject(node));
                    result.add(record);
                }
                continue;
            }

            String arrayPath = withoutTrailing.substring(0, bracketPos);
            String fieldName = withoutTrailing.substring(bracketPos + 2);
            // 去掉可能的点前缀
            if (fieldName.startsWith(".")) {
                fieldName = fieldName.substring(1);
            }

            JsonNode arrayNode = findNode(arrayPath);
            if (arrayNode == null || !arrayNode.isArray()) {
                continue;
            }

            for (JsonNode item : arrayNode) {
                if (!item.isObject()) continue;

                JsonNode fieldValue = item.path(fieldName);
                if (fieldValue.isNull() || fieldValue.isMissingNode()) continue;

                Map<String, Object> record = new LinkedHashMap<>();
                record.put(fieldName, getValueAsObject(fieldValue));
                result.add(record);
            }
        }

        return result;
    }

    /**
     * 将 JsonNode 转为通用对象
     */
    private Object getValueAsObject(JsonNode node) {
        if (node.isTextual()) {
            return node.asText();
        } else if (node.isNumber()) {
            return node.asDouble();
        } else if (node.isBoolean()) {
            return node.asBoolean();
        } else if (node.isObject()) {
            return convertValue(node);
        }
        return null;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 查找节点（支持简单路径和数组路径）
     */
    private JsonNode findNode(String path) {
        if (path == null || path.isEmpty()) {
            return root;
        }

        // 处理末尾的 [] - 表示数组
        if (path.endsWith("[]")) {
            String basePath = path.substring(0, path.length() - 2);
            JsonNode node = basePath.isEmpty() ? root : findNode(basePath);
            if (node != null && node.isArray()) {
                return node;
            }
            return objectMapper.nullNode();
        }

        // 处理数组路径：data[].field
        if (path.contains("[]")) {
            int bracketPos = path.indexOf("[]");
            String arrayPath = path.substring(0, bracketPos + 2);  // "data[]"
            String fieldPath = path.substring(bracketPos + 3);     // "signableOrderInfos"

            JsonNode arrayNode = findNode(arrayPath);
            if (arrayNode == null || !arrayNode.isArray()) {
                return objectMapper.nullNode();
            }

            // 返回数组第一个元素的 field（用于判断类型）
            if (fieldPath.isEmpty()) {
                return arrayNode;
            }

            JsonNode firstItem = arrayNode.get(0);
            if (firstItem == null || !firstItem.isObject()) {
                return objectMapper.nullNode();
            }

            return firstItem.path(fieldPath);
        }

        // 简单路径
        String[] parts = path.split("\\.");
        JsonNode current = root;

        for (String part : parts) {
            if (current == null || current.isNull() || current.isMissingNode()) {
                return objectMapper.nullNode();
            }
            current = current.path(part);
        }

        return current;
    }

    /**
     * 从数组中提取指定字段的值列表
     */
    private List<String> extractFieldFromArray(JsonNode array, String fieldName) {
        List<String> values = new ArrayList<>();

        if (array == null || !array.isArray()) {
            return values;
        }

        for (JsonNode item : array) {
            if (item.isObject()) {
                JsonNode fieldValue = item.path(fieldName);
                if (!fieldValue.isNull() && !fieldValue.isMissingNode()) {
                    values.add(fieldValue.asText());
                } else {
                    values.add(null);
                }
            }
        }

        return values;
    }

    /**
     * 从路径中提取字段名
     */
    private String extractFieldName(String path) {
        if (path.startsWith("$param.")) {
            return path.substring("$param.".length());
        }

        String cleaned = path.endsWith("[]") ? path.substring(0, path.length() - 2) : path;
        int lastDot = cleaned.lastIndexOf('.');
        int lastBracket = cleaned.lastIndexOf("[");

        int splitPos = Math.max(lastDot, lastBracket);
        if (splitPos >= 0 && splitPos < cleaned.length() - 1) {
            return cleaned.substring(splitPos + 1);
        }

        return cleaned;
    }

    /**
     * 将 JsonNode 转换为 Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return Collections.emptyMap();
        }

        if (!node.isObject()) {
            return Collections.emptyMap();
        }

        Map<String, Object> map = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value.isObject()) {
                map.put(entry.getKey(), convertValue(value));
            } else if (value.isArray()) {
                map.put(entry.getKey(), convertValueAsList(value));
            } else if (value.isTextual()) {
                map.put(entry.getKey(), value.asText());
            } else if (value.isNumber()) {
                map.put(entry.getKey(), value.asDouble());
            } else if (value.isBoolean()) {
                map.put(entry.getKey(), value.asBoolean());
            } else {
                map.put(entry.getKey(), null);
            }
        });

        return map;
    }

    /**
     * 将 JsonNode 数组转换为 List
     */
    @SuppressWarnings("unchecked")
    private List<Object> convertValueAsList(JsonNode arrayNode) {
        List<Object> list = new ArrayList<>();

        for (JsonNode item : arrayNode) {
            if (item.isObject()) {
                list.add(convertValue(item));
            } else if (item.isTextual()) {
                list.add(item.asText());
            } else if (item.isNumber()) {
                list.add(item.asDouble());
            } else if (item.isBoolean()) {
                list.add(item.asBoolean());
            } else {
                list.add(null);
            }
        }

        return list;
    }

    // 调试方法
    public String debugFindNode(String path) {
        JsonNode node = findNode(path);
        return node != null ? node.toString() : "null";
    }
}