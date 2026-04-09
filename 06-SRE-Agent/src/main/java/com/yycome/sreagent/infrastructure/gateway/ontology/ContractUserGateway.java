package com.yycome.sreagent.infrastructure.gateway.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yycome.sreagent.domain.ontology.engine.EntityDataGateway;
import com.yycome.sreagent.domain.ontology.engine.EntityGatewayRegistry;
import com.yycome.sreagent.infrastructure.client.HttpEndpointClient;
import com.yycome.sreagent.infrastructure.dao.ContractDao;
import com.yycome.sreagent.infrastructure.util.DateTimeUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * ContractUser 实体的数据网关
 * 查询签约人后进行后处理：
 * 1. 解密手机号
 * 2. 根据手机号查询 ucid
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractUserGateway implements EntityDataGateway {

    private final ContractDao contractDao;
    private final EntityGatewayRegistry registry;
    private final HttpEndpointClient httpEndpointClient;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        registry.register(this);
    }

    @Override
    public String getEntityName() {
        return "ContractUser";
    }

    @Override
    public List<Map<String, Object>> queryByField(String fieldName, Object value) {
        log.debug("[ContractUserGateway] queryByField: {} = {}", fieldName, value);
        if (!"contractCode".equals(fieldName)) {
            throw new IllegalArgumentException("ContractUser 不支持字段: " + fieldName);
        }
        if (value == null) {
            log.warn("[ContractUserGateway] {} 的值为 null，无法查询", fieldName);
            return List.of();
        }
        // ContractDao.fetchUsers 已经过滤 del_status = 0
        List<Map<String, Object>> users = contractDao.fetchUsers(value.toString());
        if (users.isEmpty()) {
            return users;
        }

        // 后处理：解密手机号并查询 ucid
        return enrichUsers(users);
    }

    /**
     * 后处理：解密手机号并查询 ucid
     */
    private List<Map<String, Object>> enrichUsers(List<Map<String, Object>> users) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> user : users) {
            // 数据库字段（下划线）→ 驼峰映射
            Map<String, Object> enriched = new LinkedHashMap<>();
            enriched.put("contractCode", null); // 数据库未返回
            enriched.put("roleType", user.get("role_type"));
            enriched.put("name", user.get("name"));
            enriched.put("phone", user.get("phone")); // 密文
            enriched.put("certificateNo", null); // 数据库未返回
            enriched.put("isSign", user.get("is_sign"));
            enriched.put("isAuth", user.get("is_auth"));
            enriched.put("ctime", DateTimeUtil.format(user.get("ctime")));
            enriched.put("mtime", DateTimeUtil.format(user.get("mtime")));

            // 解密手机号
            String encryptedPhone = getString(user, "phone");
            String plainPhone = decryptPhone(encryptedPhone);
            enriched.put("phonePlain", plainPhone);
            enriched.put("ucid", "空");
            // 查询 ucid
            if (plainPhone != null && !plainPhone.isEmpty()) {
                String ucid = queryUcid(plainPhone);
                if (ucid != null) {
                    enriched.put("ucid", ucid);
                    log.info("[ContractUserGateway] 查询ucid成功, phone={}, ucid={}", plainPhone, ucid);
                } else {
                    log.warn("[ContractUserGateway] 未找到ucid, phone={}", plainPhone);
                }
            }

            result.add(enriched);
        }
        return result;
    }

    /**
     * 解密手机号
     */
    private String decryptPhone(String encryptedPhone) {
        if (encryptedPhone == null || encryptedPhone.isEmpty()) {
            return null;
        }
        try {
            String response = httpEndpointClient.callPredefinedEndpointRaw("sre-decrypt",
                    Map.of("text", encryptedPhone));
            if (response == null) {
                log.warn("[ContractUserGateway] 解密接口无响应, phone={}", encryptedPhone);
                return null;
            }
            log.debug("[ContractUserGateway] 解密接口响应: {}", response);
            JsonNode root = objectMapper.readTree(response);
            // 响应格式：{"data":"18303623350","code":2000,...}
            if (root.has("data")) {
                return root.get("data").asText(null);
            }
            log.warn("[ContractUserGateway] 解密响应格式异常: {}", response);
            return null;
        } catch (Exception e) {
            log.warn("[ContractUserGateway] 解密手机号失败, phone={}", encryptedPhone, e);
            return null;
        }
    }

    /**
     * 根据手机号查询 ucid
     */
    private String queryUcid(String phone) {
        if (phone == null || phone.isEmpty()) {
            return null;
        }
        try {
            String response = httpEndpointClient.callPredefinedEndpointRaw("user-phone-query",
                    Map.of("bizId", phone));
            if (response == null) {
                log.warn("[ContractUserGateway] 查询ucid接口无响应, phone={}", phone);
                return null;
            }
            log.debug("[ContractUserGateway] 查询ucid接口响应: {}", response);
            JsonNode root = objectMapper.readTree(response);
            // 响应格式：{"data":{"list":[{"userId":2000000423046865,"phone":"18303623350"}],"total":7},"code":2000,...}
            JsonNode list = root.path("data").path("list");
            if (list.isArray() && list.size() > 0) {
                JsonNode first = list.get(0);
                if (first.has("userId")) {
                    return first.get("userId").asText(null);
                }
            }
            log.debug("[ContractUserGateway] 未找到ucid, phone={}", phone);
            return null;
        } catch (Exception e) {
            log.warn("[ContractUserGateway] 查询ucid失败, phone={}", phone, e);
            return null;
        }
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }
}
