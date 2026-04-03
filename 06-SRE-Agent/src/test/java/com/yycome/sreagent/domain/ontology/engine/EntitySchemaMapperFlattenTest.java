package com.yycome.sreagent.domain.ontology.engine;

import com.yycome.sreagent.domain.ontology.model.OntologyAttribute;
import com.yycome.sreagent.domain.ontology.model.OntologyEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 EntitySchemaMapper 对不同 flattenPath 格式的支持
 */
class EntitySchemaMapperFlattenTest {

    private final EntitySchemaMapper mapper = new EntitySchemaMapper();

    @Test
    void testFlattenDirectArray() {
        // SubOrder 场景: { "data": [...] }
        String rawJson = """
            {
              "data": [
                { "orderNo": "S001", "status": "ACTIVE" },
                { "orderNo": "S002", "status": "PENDING" }
              ]
            }
            """;

        OntologyEntity entity = new OntologyEntity();
        entity.setFlattenPath("data[]");
        entity.setAttributes(List.of(
                attr("orderNo", "orderNo"),
                attr("status", "status")
        ));

        List<Map<String, Object>> result = mapper.map(entity, rawJson, Map.of());

        assertEquals(2, result.size());
        assertEquals("S001", result.get(0).get("orderNo"));
        assertEquals("ACTIVE", result.get(0).get("status"));
        assertEquals("S002", result.get(1).get("orderNo"));
    }

    @Test
    void testFlattenNestedPathArray() {
        // PersonalQuote 场景: { "data": { "personalContractDataList": [...] } }
        String rawJson = """
            {
              "code": 2000,
              "data": {
                "personalContractDataList": [
                  { "billCode": "B001", "personalContractPrice": "100.00" },
                  { "billCode": "B002", "personalContractPrice": "200.00" }
                ]
              }
            }
            """;

        OntologyEntity entity = new OntologyEntity();
        entity.setFlattenPath("data.personalContractDataList[]");
        entity.setAttributes(List.of(
                attr("billCode", "billCode"),
                attr("personalContractPrice", "personalContractPrice")
        ));

        List<Map<String, Object>> result = mapper.map(entity, rawJson, Map.of());

        assertEquals(2, result.size());
        assertEquals("B001", result.get(0).get("billCode"));
        assertEquals("100.00", result.get(0).get("personalContractPrice"));
        assertEquals("B002", result.get(1).get("billCode"));
    }

    @Test
    void testFlattenWithNestedFieldSource() {
        // PersonalQuote 嵌套字段: quoteInfo.fileUrl → quoteFileUrl
        String rawJson = """
            {
              "data": {
                "personalContractDataList": [
                  {
                    "billCode": "B001",
                    "quoteInfo": {
                      "fileUrl": "http://example.com/file.pdf",
                      "prevUrl": "http://example.com/preview"
                    }
                  }
                ]
              }
            }
            """;

        OntologyEntity entity = new OntologyEntity();
        entity.setFlattenPath("data.personalContractDataList[]");
        entity.setAttributes(List.of(
                attr("billCode", "billCode"),
                attr("quoteFileUrl", "quoteInfo.fileUrl"),
                attr("quotePrevUrl", "quoteInfo.prevUrl")
        ));

        List<Map<String, Object>> result = mapper.map(entity, rawJson, Map.of());

        assertEquals(1, result.size());
        assertEquals("B001", result.get(0).get("billCode"));
        assertEquals("http://example.com/file.pdf", result.get(0).get("quoteFileUrl"));
        assertEquals("http://example.com/preview", result.get(0).get("quotePrevUrl"));
    }

    @Test
    void testFlattenWithParamInjection() {
        // 参数注入: $param.projectOrderId
        String rawJson = """
            {
              "data": [
                { "orderNo": "S001" }
              ]
            }
            """;

        OntologyEntity entity = new OntologyEntity();
        entity.setFlattenPath("data[]");
        entity.setAttributes(List.of(
                attr("projectOrderId", "$param.projectOrderId"),
                attr("orderNo", "orderNo")
        ));

        Map<String, Object> queryParams = Map.of("projectOrderId", "825123110000002753");
        List<Map<String, Object>> result = mapper.map(entity, rawJson, queryParams);

        assertEquals(1, result.size());
        assertEquals("825123110000002753", result.get(0).get("projectOrderId"));
        assertEquals("S001", result.get(0).get("orderNo"));
    }

    private OntologyAttribute attr(String name, String source) {
        OntologyAttribute attr = new OntologyAttribute();
        attr.setName(name);
        attr.setSource(source);
        return attr;
    }
}
