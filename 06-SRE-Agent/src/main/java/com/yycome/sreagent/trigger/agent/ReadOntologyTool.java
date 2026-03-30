package com.yycome.sreagent.trigger.agent;

import com.yycome.sreagent.domain.ontology.model.OntologyEntity;
import com.yycome.sreagent.domain.ontology.model.OntologyRelation;
import com.yycome.sreagent.domain.ontology.service.EntityRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * 本体模型查询工具
 * 允许 Agent 查询当前系统支持的本体实体和关系
 */
@Component
public class ReadOntologyTool {

    private final Logger logger = LoggerFactory.getLogger(ReadOntologyTool.class);
    private final EntityRegistry entityRegistry;

    public ReadOntologyTool(EntityRegistry entityRegistry) {
        this.entityRegistry = entityRegistry;
    }

    @Tool(description = """
        Lists all available ontology entities in the current system.

        Usage:
        - Returns a summary table of all entity names, display names, aliases, and query entry fields
        - Use this when the user asks about available data models, entities, or what can be queried
        """)
    public String listEntities(ToolContext context) {
        logger.info("ReadOntologyTool: listEntities");

        var entities = entityRegistry.getOntology().getEntities();
        if (entities.isEmpty()) {
            return "当前系统未配置任何本体实体。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## 本体实体列表\n\n");
        sb.append("| 实体名 | 显示名 | 别名 | 查询入口 |\n");
        sb.append("|--------|--------|------|----------|\n");

        for (OntologyEntity entity : entities) {
            String aliases = entity.getAliases() != null
                ? String.join(", ", entity.getAliases())
                : "-";
            String entries = entity.getLookupStrategies() != null
                ? entity.getLookupStrategies().stream()
                    .map(s -> s.getField())
                    .collect(Collectors.joining(", "))
                : "-";

            sb.append(String.format("| %s | %s | %s | %s |\n",
                entity.getName(),
                entity.getDisplayName() != null ? entity.getDisplayName() : "-",
                aliases,
                entries));
        }

        sb.append(String.format("\n共 %d 个实体。", entities.size()));
        return sb.toString();
    }

    @Tool(description = """
        Lists all available ontology relations (edges) between entities.

        Usage:
        - Returns a summary table of all relations: from entity, to entity, via fields, and description
        - Use this when the user asks about entity relationships or how entities are connected
        """)
    public String listRelations(ToolContext context) {
        logger.info("ReadOntologyTool: listRelations");

        var relations = entityRegistry.getOntology().getRelations();
        if (relations.isEmpty()) {
            return "当前系统未配置任何本体关系。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## 本体关系列表\n\n");
        sb.append("| 源实体 | 目标实体 | 字段映射 | 描述 |\n");
        sb.append("|--------|----------|----------|------|\n");

        for (OntologyRelation rel : relations) {
            String via = rel.getVia() != null
                ? rel.getVia().entrySet().stream()
                    .map(e -> e.getKey() + "→" + e.getValue())
                    .collect(Collectors.joining(", "))
                : "-";
            String desc = rel.getDescription() != null ? rel.getDescription() : "-";

            sb.append(String.format("| %s | %s | %s | %s |\n",
                rel.getFrom(), rel.getTo(), via, desc));
        }

        sb.append(String.format("\n共 %d 条关系。", relations.size()));
        return sb.toString();
    }

    @Tool(description = """
        Shows detailed information for a specific ontology entity including its attributes.

        Usage:
        - The entity_name parameter must be one of the known entity names (e.g., Order, Contract, ContractNode)
        - Returns all attributes and their types/descriptions for the entity
        """)
    public String getEntityDetail(
            @ToolParam(description = "The name of the entity, e.g. Order, Contract, ContractNode") String entityName,
            ToolContext context) {
        logger.info("ReadOntologyTool: getEntityDetail for {}", entityName);

        try {
            OntologyEntity entity = entityRegistry.getEntity(entityName);

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("## 实体: %s\n\n", entity.getName()));

            if (entity.getDisplayName() != null) {
                sb.append(String.format("**显示名**: %s\n\n", entity.getDisplayName()));
            }
            if (entity.getDescription() != null) {
                sb.append(String.format("**描述**: %s\n\n", entity.getDescription()));
            }
            if (entity.getTable() != null) {
                sb.append(String.format("**数据表**: `%s`\n\n", entity.getTable()));
            }
            if (entity.getAliases() != null && !entity.getAliases().isEmpty()) {
                sb.append(String.format("**别名**: %s\n\n", String.join(", ", entity.getAliases())));
            }

            if (entity.getLookupStrategies() != null && !entity.getLookupStrategies().isEmpty()) {
                sb.append("**查询入口**:\n");
                for (var strategy : entity.getLookupStrategies()) {
                    sb.append(String.format("- 字段: `%s`, 正则: `%s`\n",
                        strategy.getField(), strategy.getPattern()));
                }
                sb.append("\n");
            }

            if (entity.getAttributes() != null && !entity.getAttributes().isEmpty()) {
                sb.append("**属性**:\n\n");
                sb.append("| 属性名 | 类型 | 描述 |\n");
                sb.append("|--------|------|------|\n");
                for (var attr : entity.getAttributes()) {
                    sb.append(String.format("| %s | %s | %s |\n",
                        attr.getName(),
                        attr.getType() != null ? attr.getType() : "-",
                        attr.getDescription() != null ? attr.getDescription() : "-"));
                }
            }

            return sb.toString();

        } catch (IllegalArgumentException e) {
            return "Error: " + e.getMessage();
        }
    }
}