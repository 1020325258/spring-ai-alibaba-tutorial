## Context

当前本体模型支持合同（Contract）、合同节点（ContractNode）、签约单据（ContractQuotationRelation）等实体，但缺少合同签约人信息。用户需要查询合同的签约人（业主、代理人、代办人）信息。

## Goals / Non-Goals

**Goals:**
- 新增 ContractUser 实体定义，支持通过 contractCode 查询签约人
- 建立 Contract → ContractUser 的关联关系
- 实现 ContractUserGateway 数据访问层

**Non-Goals:**
- 不修改现有实体的查询逻辑
- 不添加新的 HTTP 接口（使用数据库直连）

## Decisions

### 1. 实体定义

在 `domain-ontology.yaml` 中新增 ContractUser 实体：

```yaml
- name: ContractUser
  displayName: "合同签约人"
  aliases: ["签约人", "合同用户", "业主", "代理人", "代办人"]
  description: "合同签约人信息，包含业主、代理人、公司代办人"
  table: contract_user
  lookupStrategies:
    - field: contractCode
      pattern: "^C\\d+"
  attributes:
    - { name: contractCode,  type: string, description: "合同编号" }
    - { name: roleType,      type: enum,   description: "用户类型：1-业主 2-代理人 3-公司代办人" }
    - { name: name,          type: string, description: "姓名" }
    - { name: phone,         type: string, description: "手机号" }
    - { name: certificateNo, type: string, description: "证件号码" }
    - { name: ctime,         type: string, description: "创建时间" }
    - { name: mtime,         type: string, description: "更新时间" }
```

**Rationale**: 遵循现有实体定义模式，使用 `table` 指定数据库表，`lookupStrategies` 定义查询方式。

### 2. 关联关系

```yaml
- from: Contract
  to: ContractUser
  domain: contract
  description: "合同的签约人信息"
  via: { source_field: contractCode, target_field: contractCode }
```

**Rationale**: ContractUser 是 Contract 的子实体，通过 contractCode 关联，与 ContractNode、ContractQuotationRelation 等实体的关联方式一致。

### 3. 数据访问层实现

创建 `ContractUserGateway` 类，实现 `EntityDataGateway` 接口：

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractUserGateway implements EntityDataGateway {

    private final JdbcTemplate jdbcTemplate;
    private final EntityGatewayRegistry registry;

    @PostConstruct
    public void init() { registry.register(this); }

    @Override
    public String getEntityName() { return "ContractUser"; }

    @Override
    public List<Map<String, Object>> queryByField(String fieldName, Object value) {
        if (value == null) {
            log.warn("[ContractUserGateway] {} 的值为 null，无法查询", fieldName);
            return Collections.emptyList();
        }

        String sql = """
            SELECT contract_code, role_type, name, phone, certificate_no, ctime, mtime
            FROM contract_user
            WHERE contract_code = ? AND del_status = 0
            """;

        try {
            return jdbcTemplate.queryForList(sql, value.toString());
        } catch (Exception e) {
            log.warn("[ContractUserGateway] 查询失败", e);
            return Collections.emptyList();
        }
    }
}
```

**Rationale**:
- 使用 `JdbcTemplate` 直连数据库（参考 ContractFieldGateway 的实现模式）
- 过滤 `del_status = 0` 排除已删除记录
- 字段映射：数据库下划线命名 → Java 驼峰命名由 Spring 自动处理

### 4. QueryScope 枚举扩展

在 `QueryScope` 枚举中添加：

```java
CONTRACT_USER("ContractUser", "展开到合同签约人"),
```

**Rationale**: 与现有实体保持一致，支持 `queryScope=ContractUser` 查询。

## Risks / Trade-offs

| 风险 | 缓解措施 |
|------|---------|
| 数据库连接池资源消耗 | ContractUser 数据量小，单次查询快速返回 |
| 字段映射不一致 | 使用 Spring 自动映射，测试验证 |
| 敏感信息（证件号、手机号）暴露 | 后续可添加脱敏逻辑，当前按原样返回 |
