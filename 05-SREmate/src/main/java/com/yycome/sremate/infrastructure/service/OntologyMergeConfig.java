package com.yycome.sremate.infrastructure.service;

import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 本体论合并配置
 * 定义实体间的关联关系，用于智能合并多工具返回的数据
 *
 * 配置格式：
 * - entityType: 实体类型标识
 * - primaryKey: 主键字段名（用于关联）
 * - relations: 关联的从表配置
 */
@Getter
@Component
public class OntologyMergeConfig {

    /**
     * 主表配置：key=主表类型, value=关联配置
     */
    private final Map<String, EntityConfig> entityConfigs = new LinkedHashMap<>();

    public OntologyMergeConfig() {
        initContractConfig();
        // 未来可扩展：initOrderConfig(), initQuotationConfig() 等
    }

    /**
     * 初始化合同本体配置
     */
    private void initContractConfig() {
        EntityConfig contractConfig = new EntityConfig(
                "contract",
                List.of("contract_code", "contractCode"),  // 关联键（兼容多种命名）
                List.of(
                        new RelationConfig("contract_quotation_relation", "contract_quotation_relation", RelationType.ONE_TO_MANY),
                        new RelationConfig("contract_node", "contract_node", RelationType.ONE_TO_MANY),
                        new RelationConfig("contract_field", "contract_field", RelationType.ONE_TO_ONE)
                )
        );
        entityConfigs.put("contract", contractConfig);
    }

    /**
     * 根据数据内容检测结果类型
     */
    public String detectEntityType(String json) {
        String lower = json.toLowerCase();

        // contract_quotation_relation 特征
        if (lower.contains("\"bill_code\"") || lower.contains("\"billcode\"")) {
            return "contract_quotation_relation";
        }
        // contract_node 特征
        if (lower.contains("\"node_type\"") || lower.contains("\"fire_time\"")) {
            return "contract_node";
        }
        // contract_field 特征
        if (lower.contains("\"_shardtable\"") || lower.contains("\"_shardTable\"")) {
            return "contract_field";
        }
        // contract 主表特征
        if (lower.contains("\"platform_instance_id\"") || lower.contains("\"platforminstanceid\"")) {
            return "contract";
        }

        // 默认
        return "unknown";
    }

    /**
     * 判断是否为主表类型
     */
    public boolean isPrimaryEntity(String type) {
        return entityConfigs.containsKey(type);
    }

    /**
     * 获取主表配置
     */
    public EntityConfig getPrimaryConfig(String type) {
        return entityConfigs.get(type);
    }

    /**
     * 实体配置
     */
    @Getter
    public static class EntityConfig {
        private final String entityType;
        private final List<String> primaryKeys;  // 支持多种命名风格
        private final List<RelationConfig> relations;

        public EntityConfig(String entityType, List<String> primaryKeys, List<RelationConfig> relations) {
            this.entityType = entityType;
            this.primaryKeys = primaryKeys;
            this.relations = relations;
        }

        /**
         * 从数据中提取主键值
         */
        public String extractPrimaryKey(Map<String, Object> data) {
            for (String key : primaryKeys) {
                Object value = data.get(key);
                if (value != null) {
                    return String.valueOf(value);
                }
            }
            return null;
        }

        /**
         * 获取关联配置
         */
        public Optional<RelationConfig> getRelation(String entityType) {
            return relations.stream()
                    .filter(r -> r.getEntityType().equals(entityType))
                    .findFirst();
        }
    }

    /**
     * 关联配置
     */
    @Getter
    public static class RelationConfig {
        private final String entityType;       // 从表类型
        private final String nestedFieldName;  // 嵌套字段名
        private final RelationType relationType;

        public RelationConfig(String entityType, String nestedFieldName, RelationType relationType) {
            this.entityType = entityType;
            this.nestedFieldName = nestedFieldName;
            this.relationType = relationType;
        }
    }

    /**
     * 关联类型
     */
    public enum RelationType {
        ONE_TO_ONE,   // 一对一：嵌套为对象
        ONE_TO_MANY   // 一对多：嵌套为数组
    }
}
