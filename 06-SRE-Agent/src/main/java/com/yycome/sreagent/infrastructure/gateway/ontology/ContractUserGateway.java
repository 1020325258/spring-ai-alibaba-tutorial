package com.yycome.sreagent.infrastructure.gateway.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yycome.sreagent.domain.ontology.engine.EntityDataGateway;
import com.yycome.sreagent.domain.ontology.engine.EntityGatewayRegistry;
import com.yycome.sreagent.infrastructure.client.HttpEndpointClient;
import com.yycome.sreagent.infrastructure.util.JsonMappingUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * ContractUser 实体的数据网关
 * <p>
 * 通过 HTTP 接口查询签约人后进行后处理：
 * 1. 解密手机号
 * 2. 根据手机号查询 ucid
 * <p>
 * 返回属性遵循 domain-ontology.yaml 定义：
 * - contractCode: 合同编号（数据源未返回，设为 null）
 * - roleType: 用户类型：1-业主 2-代理人 3-公司代办人
 * - name: 姓名
 * - phone: 手机号（密文）
 * - phonePlain: 手机号（明文）
 * - ucid: 用户ID
 * - certificateNo: 证件号码（数据源未返回，设为 null）
 * - isSign: 是否签约人：1-是 0-否
 * - isAuth: 是否认证：1-是 0-否
 * - ctime: 创建时间
 * - mtime: 更新时间
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractUserGateway implements EntityDataGateway {

    private final HttpEndpointClient httpEndpointClient;
    private final ObjectMapper objectMapper;
    private final EntityGatewayRegistry registry;

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

        String contractCode = String.valueOf(value);

        try {
            String json = httpEndpointClient.callPredefinedEndpointRaw("sre-contract-user",
                    Map.of("contractCode", contractCode));
            if (json == null) {
                log.warn("[ContractUserGateway] 查询签约人失败, contractCode={}", contractCode);
                return Collections.emptyList();
            }

            List<Map<String, Object>> users = parseUsers(json, contractCode);
            if (users.isEmpty()) {
                return users;
            }

            // 后处理：解密手机号并查询 ucid
            return enrichUsers(users);
        } catch (Exception e) {
            log.warn("[ContractUserGateway] 查询签约人失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 解析签约人数据
     * <p>
     * 按 YAML 定义的属性组装返回
     */
    private List<Map<String, Object>> parseUsers(String json, String contractCode) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                return result;
            }

            for (JsonNode user : data) {
                Map<String, Object> item = JsonMappingUtils.newOrderedMap();
                item.put("contractCode", contractCode);
                item.put("roleType", JsonMappingUtils.getInt(user, "roleType"));
                item.put("name", JsonMappingUtils.getText(user, "name"));
                item.put("phone", JsonMappingUtils.getText(user, "phone"));
                item.put("phonePlain", null);
                item.put("ucid", null);
                item.put("certificateNo", null);
                item.put("isSign", JsonMappingUtils.getInt(user, "isSign"));
                item.put("isAuth", JsonMappingUtils.getInt(user, "isAuth"));
                item.put("ctime", JsonMappingUtils.formatDateTime(user, "ctime"));
                item.put("mtime", JsonMappingUtils.formatDateTime(user, "mtime"));
                result.add(item);
            }
        } catch (Exception e) {
            log.warn("[ContractUserGateway] 解析响应失败: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 后处理：解密手机号并查询 ucid
     */
    private List<Map<String, Object>> enrichUsers(List<Map<String, Object>> users) {
        for (Map<String, Object> user : users) {
            String encryptedPhone = (String) user.get("phone");
            String plainPhone = decryptPhone(encryptedPhone);
            user.put("phonePlain", plainPhone);
            user.put("ucid", "空");

            if (plainPhone != null && !plainPhone.isEmpty()) {
                String ucid = queryUcid(plainPhone);
                if (ucid != null) {
                    user.put("ucid", ucid);
                    log.info("[ContractUserGateway] 查询ucid成功, phone={}, ucid={}", plainPhone, ucid);
                } else {
                    log.warn("[ContractUserGateway] 未找到ucid, phone={}", plainPhone);
                }
            }
        }
        return users;
    }

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
}
