package com.alibaba.yycome.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.yycome.entity.Plan;
import com.alibaba.yycome.entity.SearchResult;
import com.alibaba.yycome.enums.StateKeyEnum;
import com.alibaba.yycome.service.McpService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.*;
import java.util.stream.Collectors;

public class SearchNode implements NodeAction {

    private final Logger logger = LoggerFactory.getLogger(SearchNode.class);

    private ChatClient searchAgent;

    private McpService mcpService;

    public SearchNode(ChatClient searchAgent, McpService mcpService) {
        this.searchAgent = searchAgent;
        this.mcpService = mcpService;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {

        Map<String, Object> result = new HashMap<>();

        // 获取节点执行计划
        Plan plan = state.value(StateKeyEnum.PLAN.getKey(), Plan.class).orElse(null);

        if (plan == null || CollectionUtils.isEmpty(plan.getSteps())) {
            // todo 异常情况判断
            return Map.of();
        }

        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(
                "重要说明：不要在正文中插入行内引用（inline citations）。\n" +
                        "请在文末单独添加 References（参考文献） 部分，并使用 链接引用格式（link reference format）。\n" +
                        "在每个引用之间留出一个空行，以保持良好的可读性。"));
        for (Plan.Step step : plan.getSteps()) {
            if (!Plan.StepType.RESEARCH.equals(step.getStepType())) {
                continue;
            }
            String title = step.getTitle();
            List<SearchResult> searchResults = mcpService.query(title);
            messages.add(new UserMessage(
                    "搜索结果：" + searchResults.stream().map(r -> {
                        return String.format("标题: %s\n内容: %s\n链接: %s\n", r.getTitle(), r.getContent(), r.getLink());
                    }).collect(Collectors.joining("\n\n"))));
        }
        String searchContent = searchAgent.prompt().messages(messages)
                .call()
                .content();
        logger.info("SearchNode 输出结果: {}", searchContent);
        result.put(StateKeyEnum.SEARCH_CONTENT.getKey(), searchContent);
        return result;
    }
}
