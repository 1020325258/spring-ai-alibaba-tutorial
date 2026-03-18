package com.yycome.sremate.domain.ontology.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.yycome.sremate.domain.ontology.model.OntologyEntity;
import com.yycome.sremate.domain.ontology.model.OntologyModel;
import com.yycome.sremate.domain.ontology.model.OntologyRelation;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 本体注册表，负责加载和校验本体 YAML，并提供路径规划和摘要生成能力
 */
@Slf4j
@Component
public class EntityRegistry {

    private final Resource ontologyResource;
    private OntologyModel ontology;

    public EntityRegistry(@Value("classpath:ontology/domain-ontology.yaml") Resource ontologyResource) {
        this.ontologyResource = ontologyResource;
    }

    @PostConstruct
    public void load() throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.findAndRegisterModules();
        ontology = mapper.readValue(ontologyResource.getInputStream(), OntologyModel.class);
        validate();
        log.info("[EntityRegistry] 本体加载完成：{} 个实体，{} 条关系",
            ontology.getEntities().size(), ontology.getRelations().size());
    }

    /** Schema 自洽性校验：所有 relation 的 from/to 必须在 entities 中存在 */
    private void validate() {
        Set<String> entityNames = ontology.getEntities().stream()
            .map(OntologyEntity::getName)
            .collect(Collectors.toSet());

        for (OntologyRelation rel : ontology.getRelations()) {
            if (!entityNames.contains(rel.getFrom())) {
                throw new IllegalStateException(
                    "本体校验失败：relation 引用了不存在的 entity '" + rel.getFrom() + "'");
            }
            if (!entityNames.contains(rel.getTo())) {
                throw new IllegalStateException(
                    "本体校验失败：relation 引用了不存在的 entity '" + rel.getTo() + "'");
            }
        }
    }

    /** 返回本体模型（供可视化 API 使用） */
    public OntologyModel getOntology() {
        return ontology;
    }

    /**
     * 图遍历：返回从 from 到 to 的所有可达路径（节点名称列表）
     * 使用 BFS，避免循环
     */
    public List<List<String>> findPaths(String from, String to) {
        List<List<String>> result = new ArrayList<>();
        Queue<List<String>> queue = new LinkedList<>();
        queue.add(new ArrayList<>(List.of(from)));

        while (!queue.isEmpty()) {
            List<String> path = queue.poll();
            String current = path.get(path.size() - 1);

            if (current.equals(to)) {
                result.add(path);
                continue;
            }

            // 防止无限循环
            if (path.size() > 6) continue;

            for (OntologyRelation rel : ontology.getRelations()) {
                if (rel.getFrom().equals(current) && !path.contains(rel.getTo())) {
                    List<String> newPath = new ArrayList<>(path);
                    newPath.add(rel.getTo());
                    queue.add(newPath);
                }
            }
        }
        return result;
    }

    /**
     * 生成注入 system prompt 的本体摘要
     */
    public String getSummaryForPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("【数据模型关系】\n");
        for (OntologyRelation rel : ontology.getRelations()) {
            String via = rel.getVia() != null
                ? rel.getVia().getOrDefault("source_field", "?") + " → " +
                  rel.getVia().getOrDefault("target_field", "?")
                : "";
            String desc = rel.getDescription() != null ? "（" + rel.getDescription() + "）" : "";
            sb.append(String.format("- %s -[%s]-> %s  via: %s  [%s域]%s\n",
                rel.getFrom(), Character.toLowerCase(rel.getTo().charAt(0)) + rel.getTo().substring(1) + "s", rel.getTo(), via, rel.getDomain(), desc));
        }
        return sb.toString();
    }

    /**
     * BFS 找从 from 到 to 的最短 relation 链。
     * 返回 relation 列表（按跳顺序），找不到返回 null。
     */
    public List<OntologyRelation> findRelationPath(String from, String to) {
        if (from.equals(to)) return Collections.emptyList();

        // BFS：队列中存储"到达当前节点所经过的 relation 链"
        Queue<List<OntologyRelation>> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        visited.add(from);

        // 初始：从 from 出发的所有出边各自作为一条路径入队
        for (OntologyRelation rel : getOutgoingRelations(from)) {
            List<OntologyRelation> path = new ArrayList<>();
            path.add(rel);
            queue.add(path);
            if (rel.getTo().equals(to)) return path;
            visited.add(rel.getTo());
        }

        while (!queue.isEmpty()) {
            List<OntologyRelation> currentPath = queue.poll();
            String currentNode = currentPath.get(currentPath.size() - 1).getTo();

            for (OntologyRelation rel : getOutgoingRelations(currentNode)) {
                if (visited.contains(rel.getTo())) continue;
                visited.add(rel.getTo());

                List<OntologyRelation> newPath = new ArrayList<>(currentPath);
                newPath.add(rel);
                if (rel.getTo().equals(to)) return newPath;
                queue.add(newPath);
            }
        }
        return null; // 不可达
    }

    /**
     * 获取某实体的所有出边关系
     */
    public List<OntologyRelation> getOutgoingRelations(String entityName) {
        return ontology.getRelations().stream()
            .filter(r -> r.getFrom().equals(entityName))
            .toList();
    }

    /**
     * 按名称查找实体
     */
    public OntologyEntity getEntity(String name) {
        return ontology.getEntities().stream()
            .filter(e -> e.getName().equals(name))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("未知实体: " + name +
                "，可用实体: " + ontology.getEntities().stream()
                    .map(OntologyEntity::getName).toList()));
    }
}
