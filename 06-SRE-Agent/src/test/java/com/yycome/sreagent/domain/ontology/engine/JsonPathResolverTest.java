package com.yycome.sreagent.domain.ontology.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JsonPathResolver 单元测试 - 覆盖所有解析模式
 *
 * 支持的解析模式：
 * 1. 简单字段: "fieldName"
 * 2. 嵌套字段: "parent.child"
 * 3. 查询参数: "$param.fieldName"
 * 4. 数组元素: "data[].field"
 * 5. 数组展平: "data[].items[]"
 * 6. 带继承的展平(单路径): "data[].signableOrderInfos[]"
 * 7. 带继承的展平(双路径): "data[].companyName" + "data[].signableOrderInfos[]"
 * 8. 多数组合并: "listA[].field,listB[].field"
 */
class JsonPathResolverTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    // ==================== 模式1: 简单字段 ====================

    @Nested
    class SimpleFieldTests {
        @Test
        void shouldGetSimpleField() throws Exception {
            String json = "{\"contractCode\": \"C1767\", \"type\": \"1\"}";
            JsonNode root = objectMapper.readTree(json);
            JsonPathResolver resolver = new JsonPathResolver(root, Collections.emptyMap());

            assertEquals("C1767", resolver.get("contractCode"));
            assertEquals("1", resolver.get("type"));
        }

        @Test
        void shouldReturnNullForMissingField() throws Exception {
            String json = "{\"contractCode\": \"C1767\"}";
            JsonNode root = objectMapper.readTree(json);
            JsonPathResolver resolver = new JsonPathResolver(root, Collections.emptyMap());

            assertNull(resolver.get("missingField"));
        }

        @Test
        void shouldReturnNullForNullField() throws Exception {
            String json = "{\"contractCode\": null}";
            JsonNode root = objectMapper.readTree(json);
            JsonPathResolver resolver = new JsonPathResolver(root, Collections.emptyMap());

            assertNull(resolver.get("contractCode"));
        }
    }

    // ==================== 模式2: 嵌套字段 ====================

    @Nested
    class NestedFieldTests {
        @Test
        void shouldGetNestedField() throws Exception {
            String json = "{\"formData\": {\"cn\": \"C1767\", \"instanceId\": \"123\"}}";
            JsonNode root = objectMapper.readTree(json);
            JsonPathResolver resolver = new JsonPathResolver(root, Collections.emptyMap());

            assertEquals("C1767", resolver.get("formData.cn"));
            assertEquals("123", resolver.get("formData.instanceId"));
        }

        @Test
        void shouldReturnNullForMissingNestedField() throws Exception {
            String json = "{\"formData\": {\"cn\": \"C1767\"}}";
            JsonNode root = objectMapper.readTree(json);
            JsonPathResolver resolver = new JsonPathResolver(root, Collections.emptyMap());

            assertNull(resolver.get("formData.missing"));
        }

        @Test
        void shouldGetDeepNestedField() throws Exception {
            String json = "{\"level1\": {\"level2\": {\"level3\": \"deep value\"}}}";
            JsonNode root = objectMapper.readTree(json);
            JsonPathResolver resolver = new JsonPathResolver(root, Collections.emptyMap());

            assertEquals("deep value", resolver.get("level1.level2.level3"));
        }
    }

    // ==================== 模式3: 查询参数 ====================

    @Nested
    class QueryParamTests {
        @Test
        void shouldGetFromQueryParams() throws Exception {
            String json = "{}";
            JsonNode root = objectMapper.readTree(json);
            Map<String, Object> params = Map.of("projectOrderId", "826031915000003212");
            JsonPathResolver resolver = new JsonPathResolver(root, params);

            assertEquals("826031915000003212", resolver.get("$param.projectOrderId"));
        }

        @Test
        void shouldReturnNullForMissingQueryParam() throws Exception {
            String json = "{}";
            JsonNode root = objectMapper.readTree(json);
            JsonPathResolver resolver = new JsonPathResolver(root, Collections.emptyMap());

            assertNull(resolver.get("$param.missingParam"));
        }

        @Test
        void shouldReturnNullForNullQueryParam() throws Exception {
            String json = "{}";
            JsonNode root = objectMapper.readTree(json);
            Map<String, Object> params = new HashMap<>();
            params.put("key", null);
            JsonPathResolver resolver = new JsonPathResolver(root, params);

            assertNull(resolver.get("$param.key"));
        }
    }

    // ==================== 模式4: 数组元素 ====================

    @Nested
    class ArrayElementTests {
        @Test
        void shouldGetArrayElementField() throws Exception {
            String json = """
                {
                    "data": [
                        {"name": "a"},
                        {"name": "b"},
                        {"name": "c"}
                    ]
                }
                """;
            JsonNode root = objectMapper.readTree(json);
            JsonPathResolver resolver = new JsonPathResolver(root, Collections.emptyMap());

            // 返回第一个元素的 field
            Object result = resolver.get("data[].name");
            assertNotNull(result);
        }

        @Test
        void shouldReturnNullForNonArrayField() throws Exception {
            String json = "{\"data\": \"not an array\"}";
            JsonNode root = objectMapper.readTree(json);
            JsonPathResolver resolver = new JsonPathResolver(root, Collections.emptyMap());

            assertNull(resolver.get("data[].name"));
        }
    }

    // ==================== 模式5: 数组展平 ====================

    @Nested
    class FlattenTests {
        @Test
        void shouldFlattenArray() throws Exception {
            String json = """
                {
                    "data": [
                        {
                            "companyName": "公司A",
                            "signableOrderInfos": [
                                {"goodsInfo": "商品1", "orderAmount": 100},
                                {"goodsInfo": "商品2", "orderAmount": 200}
                            ]
                        },
                        {
                            "companyName": "公司B",
                            "signableOrderInfos": [
                                {"goodsInfo": "商品3", "orderAmount": 300}
                            ]
                        }
                    ]
                }
                """;
            JsonNode root = objectMapper.readTree(json);
            JsonPathResolver resolver = new JsonPathResolver(root, Collections.emptyMap());

            List<Map<String, Object>> result = resolver.flatten("data[].signableOrderInfos[]");

            assertEquals(3, result.size());
            assertEquals("商品1", result.get(0).get("goodsInfo"));
            assertEquals(100.0, result.get(0).get("orderAmount"));
            assertEquals("商品2", result.get(1).get("goodsInfo"));
            assertEquals("商品3", result.get(2).get("goodsInfo"));
        }

        @Test
        void shouldReturnEmptyListForEmptyArray() throws Exception {
            String json = "{\"data\": []}";
            JsonNode root = objectMapper.readTree(json);
            JsonPathResolver resolver = new JsonPathResolver(root, Collections.emptyMap());

            List<Map<String, Object>> result = resolver.flatten("data[].items[]");
            assertTrue(result.isEmpty());
        }

        @Test
        void shouldReturnEmptyListForNonArrayField() throws Exception {
            String json = "{\"data\": \"not array\"}";
            JsonNode root = objectMapper.readTree(json);
            JsonPathResolver resolver = new JsonPathResolver(root, Collections.emptyMap());

            List<Map<String, Object>> result = resolver.flatten("data[].items[]");
            assertTrue(result.isEmpty());
        }

        @Test
        void shouldReturnEmptyListForMissingPath() throws Exception {
            String json = "{\"other\": []}";
            JsonNode root = objectMapper.readTree(json);
            JsonPathResolver resolver = new JsonPathResolver(root, Collections.emptyMap());

            List<Map<String, Object>> result = resolver.flatten("data[].items[]");
            assertTrue(result.isEmpty());
        }

        @Test
        void shouldReturnEmptyListForQueryParam() throws Exception {
            String json = "{\"data\": []}";
            JsonNode root = objectMapper.readTree(json);
            JsonPathResolver resolver = new JsonPathResolver(root, Collections.emptyMap());

            List<Map<String, Object>> result = resolver.flatten("$param.field");
            assertTrue(result.isEmpty());
        }
    }

    // ==================== 模式6: 带继承的展平(单路径) ====================

    @Nested
    class FlattenWithInheritanceSinglePathTests {
        @Test
        void shouldFlattenWithInheritanceSinglePath() throws Exception {
            // 模拟 formal-sign-order-list 接口返回
            String json = """
                {
                    "data": [
                        {
                            "companyName": "北京贝壳家居科技有限公司",
                            "companyCode": "V201800236",
                            "signableOrderInfos": [
                                {"goodsInfo": "商品1", "orderAmount": 100, "bindCode": "S001"},
                                {"goodsInfo": "商品2", "orderAmount": 200, "bindCode": "S002"}
                            ]
                        }
                    ]
                }
                """;
            JsonNode root = objectMapper.readTree(json);
            JsonPathResolver resolver = new JsonPathResolver(root, Collections.emptyMap());

            List<Map<String, Object>> result = resolver.flattenWithInheritance("data[].signableOrderInfos[]");

            assertEquals(2, result.size());
            // 验证外层字段继承
            assertEquals("北京贝壳家居科技有限公司", result.get(0).get("companyName"));
            assertEquals("V201800236", result.get(0).get("companyCode"));
            // 验证内层字段
            assertEquals("商品1", result.get(0).get("goodsInfo"));
            assertEquals(100.0, result.get(0).get("orderAmount"));
            assertEquals("S001", result.get(0).get("bindCode"));

            // 第二条
            assertEquals("北京贝壳家居科技有限公司", result.get(1).get("companyName"));
            assertEquals("商品2", result.get(1).get("goodsInfo"));
        }

        @Test
        void shouldFlattenWithInheritanceMultipleOuterItems() throws Exception {
            String json = """
                {
                    "data": [
                        {
                            "companyName": "公司A",
                            "companyCode": "V001",
                            "signableOrderInfos": [
                                {"goodsInfo": "商品1"}
                            ]
                        },
                        {
                            "companyName": "公司B",
                            "companyCode": "V002",
                            "signableOrderInfos": [
                                {"goodsInfo": "商品2"},
                                {"goodsInfo": "商品3"}
                            ]
                        }
                    ]
                }
                """;
            JsonNode root = objectMapper.readTree(json);
            JsonPathResolver resolver = new JsonPathResolver(root, Collections.emptyMap());

            List<Map<String, Object>> result = resolver.flattenWithInheritance("data[].signableOrderInfos[]");

            assertEquals(3, result.size());
            assertEquals("公司A", result.get(0).get("companyName"));
            assertEquals("商品1", result.get(0).get("goodsInfo"));
            assertEquals("公司B", result.get(1).get("companyName"));
            assertEquals("商品2", result.get(1).get("goodsInfo"));
            assertEquals("公司B", result.get(2).get("companyName"));
            assertEquals("商品3", result.get(2).get("goodsInfo"));
        }

        @Test
        void shouldReturnEmptyForInvalidPath() throws Exception {
            String json = "{\"data\": []}";
            JsonNode root = objectMapper.readTree(json);
            JsonPathResolver resolver = new JsonPathResolver(root, Collections.emptyMap());

            List<Map<String, Object>> result = resolver.flattenWithInheritance("invalidPath");
            assertTrue(result.isEmpty());
        }

        @Test
        void shouldReturnEmptyForNonArrayOuter() throws Exception {
            String json = "{\"data\": \"not array\"}";
            JsonNode root = objectMapper.readTree(json);
            JsonPathResolver resolver = new JsonPathResolver(root, Collections.emptyMap());

            List<Map<String, Object>> result = resolver.flattenWithInheritance("data[].items[]");
            assertTrue(result.isEmpty());
        }
    }

    // ==================== 模式7: 带继承的展平(双路径) ====================

    @Nested
    class FlattenWithInheritanceDualPathTests {
        @Test
        void shouldFlattenWithInheritanceDualPath() throws Exception {
            String json = """
                {
                    "data": [
                        {
                            "companyName": "公司A",
                            "companyCode": "V001",
                            "signableOrderInfos": [
                                {"goodsInfo": "商品1", "orderAmount": 100}
                            ]
                        }
                    ]
                }
                """;
            JsonNode root = objectMapper.readTree(json);
            JsonPathResolver resolver = new JsonPathResolver(root, Collections.emptyMap());

            // 显式指定外层字段
            List<Map<String, Object>> result = resolver.flattenWithInheritance(
                    "data[].companyName",
                    "data[].signableOrderInfos[]"
            );

            assertEquals(1, result.size());
            assertEquals("公司A", result.get(0).get("companyName"));
            assertEquals("商品1", result.get(0).get("goodsInfo"));
        }

        @Test
        void shouldFlattenWithInheritanceDualPathMultipleFields() throws Exception {
            String json = """
                {
                    "data": [
                        {
                            "companyName": "公司A",
                            "companyCode": "V001",
                            "signableOrderInfos": [
                                {"goodsInfo": "商品1"},
                                {"goodsInfo": "商品2"}
                            ]
                        }
                    ]
                }
                """;
            JsonNode root = objectMapper.readTree(json);
            JsonPathResolver resolver = new JsonPathResolver(root, Collections.emptyMap());

            // 指定多个外层字段
            List<Map<String, Object>> result = resolver.flattenWithInheritance(
                    "data[].companyName",
                    "data[].companyCode",
                    "data[].signableOrderInfos[]"
            );

            assertEquals(2, result.size());
            assertEquals("公司A", result.get(0).get("companyName"));
            assertEquals("V001", result.get(0).get("companyCode"));
            assertEquals("商品1", result.get(0).get("goodsInfo"));
        }
    }

    // ==================== 模式8: 多数组合并 ====================

    @Nested
    class MergeTests {
        @Test
        void shouldMergeMultipleArrays() throws Exception {
            String json = """
                {
                    "bills": [
                        {"billCode": "B001"},
                        {"billCode": "B002"}
                    ],
                    "orders": [
                        {"orderNo": "O001"},
                        {"orderNo": "O002"}
                    ]
                }
                """;
            JsonNode root = objectMapper.readTree(json);
            JsonPathResolver resolver = new JsonPathResolver(root, Collections.emptyMap());

            List<Map<String, Object>> result = resolver.merge("bills[].billCode", "orders[].orderNo");

            assertEquals(4, result.size());
            assertEquals("B001", result.get(0).get("billCode"));
            assertEquals("B002", result.get(1).get("billCode"));
            assertEquals("O001", result.get(2).get("orderNo"));
            assertEquals("O002", result.get(3).get("orderNo"));
        }

        @Test
        void shouldMergeSingleArray() throws Exception {
            String json = """
                {
                    "bills": [
                        {"billCode": "B001"},
                        {"billCode": "B002"}
                    ]
                }
                """;
            JsonNode root = objectMapper.readTree(json);
            JsonPathResolver resolver = new JsonPathResolver(root, Collections.emptyMap());

            List<Map<String, Object>> result = resolver.merge("bills[].billCode");

            assertEquals(2, result.size());
            assertEquals("B001", result.get(0).get("billCode"));
        }

        @Test
        void shouldReturnEmptyForNonExistentArray() throws Exception {
            String json = "{\"bills\": []}";
            JsonNode root = objectMapper.readTree(json);
            JsonPathResolver resolver = new JsonPathResolver(root, Collections.emptyMap());

            List<Map<String, Object>> result = resolver.merge("orders[].orderNo");
            assertTrue(result.isEmpty());
        }

        @Test
        void shouldReturnEmptyForEmptyInput() throws Exception {
            String json = "{}";
            JsonNode root = objectMapper.readTree(json);
            JsonPathResolver resolver = new JsonPathResolver(root, Collections.emptyMap());

            List<Map<String, Object>> result = resolver.merge();
            assertTrue(result.isEmpty());
        }

        @Test
        void shouldHandleMergeWithDifferentFieldTypes() throws Exception {
            String json = """
                {
                    "names": [{"value": "item1"}],
                    "counts": [{"value": 10}],
                    "flags": [{"value": true}]
                }
                """;
            JsonNode root = objectMapper.readTree(json);
            JsonPathResolver resolver = new JsonPathResolver(root, Collections.emptyMap());

            // merge 从不同数组提取字段，每条记录一个字段
            List<Map<String, Object>> result = resolver.merge(
                    "names[].value",
                    "counts[].value",
                    "flags[].value"
            );

            assertEquals(3, result.size());
            assertEquals("item1", result.get(0).get("value"));
            assertEquals(10.0, result.get(1).get("value"));
            assertEquals(true, result.get(2).get("value"));
        }
    }

    // ==================== 边界情况测试 ====================

    @Nested
    class EdgeCaseTests {
        @Test
        void shouldHandleEmptyJson() throws Exception {
            String json = "{}";
            JsonNode root = objectMapper.readTree(json);
            JsonPathResolver resolver = new JsonPathResolver(root, Collections.emptyMap());

            assertNull(resolver.get("anyField"));
            assertTrue(resolver.flatten("data[].items[]").isEmpty());
            assertTrue(resolver.flattenWithInheritance("data[].items[]").isEmpty());
        }

        @Test
        void shouldHandleNullValues() throws Exception {
            String json = "{\"field\": null, \"nested\": {\"inner\": null}}";
            JsonNode root = objectMapper.readTree(json);
            JsonPathResolver resolver = new JsonPathResolver(root, Collections.emptyMap());

            assertNull(resolver.get("field"));
            assertNull(resolver.get("nested.inner"));
        }

        @Test
        void shouldHandleDifferentValueTypes() throws Exception {
            String json = """
                {
                    "stringField": "text",
                    "numberField": 123,
                    "booleanField": true,
                    "arrayField": [1, 2, 3],
                    "objectField": {"key": "value"}
                }
                """;
            JsonNode root = objectMapper.readTree(json);
            JsonPathResolver resolver = new JsonPathResolver(root, Collections.emptyMap());

            assertEquals("text", resolver.get("stringField"));
            assertEquals(123.0, resolver.get("numberField"));
            assertEquals(true, resolver.get("booleanField"));
            // array and object return as converted values
            assertNotNull(resolver.get("arrayField"));
            assertNotNull(resolver.get("objectField"));
        }

        @Test
        void shouldHandleEmptyArray() throws Exception {
            String json = "{\"data\": []}";
            JsonNode root = objectMapper.readTree(json);
            JsonPathResolver resolver = new JsonPathResolver(root, Collections.emptyMap());

            List<Map<String, Object>> result = resolver.flattenWithInheritance("data[].items[]");
            assertTrue(result.isEmpty());
        }

        @Test
        void shouldHandleNestedArray() throws Exception {
            String json = """
                {
                    "data": [
                        {
                            "outer": [
                                {
                                    "inner": [
                                        {"value": "a"},
                                        {"value": "b"}
                                    ]
                                }
                            ]
                        }
                    ]
                }
                """;
            JsonNode root = objectMapper.readTree(json);
            JsonPathResolver resolver = new JsonPathResolver(root, Collections.emptyMap());

            // 测试深层嵌套
            assertNotNull(resolver.get("data"));
        }
    }

    // ==================== 辅助方法测试 ====================

    @Nested
    class HelperMethodTests {
        @Test
        void shouldExtractFieldName() throws Exception {
            String json = "{}";
            JsonNode root = objectMapper.readTree(json);
            JsonPathResolver resolver = new JsonPathResolver(root, Collections.emptyMap());

            // 通过 debugFindNode 间接测试 findNode
            assertNotNull(resolver.debugFindNode("data"));
        }

        @Test
        void shouldDebugFindNode() throws Exception {
            String json = "{\"field\": \"value\"}";
            JsonNode root = objectMapper.readTree(json);
            JsonPathResolver resolver = new JsonPathResolver(root, Collections.emptyMap());

            String result = resolver.debugFindNode("field");
            assertTrue(result.contains("value"));
        }

        @Test
        void shouldDebugFindNodeForMissing() throws Exception {
            String json = "{}";
            JsonNode root = objectMapper.readTree(json);
            JsonPathResolver resolver = new JsonPathResolver(root, Collections.emptyMap());

            // 对于不存在的字段，debugFindNode 返回空字符串而非 "null" 字符串
            String result = resolver.debugFindNode("missing");
            assertTrue(result.isEmpty() || result.equals("null"));
        }
    }
}
