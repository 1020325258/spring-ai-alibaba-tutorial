package com.yycome.sreagent.config;

import com.alibaba.cloud.ai.agent.studio.loader.AgentLoader;
import com.alibaba.cloud.ai.graph.agent.Agent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * AgentLoader 实现：向 spring-ai-alibaba-studio 注册 SRE-Agent。
 * ExecutionController 依赖此 Bean 提供 Agent 列表和实例。
 */
@Component
public class SREAgentLoader implements AgentLoader {

    // studio 前端 NEXT_PUBLIC_APP_NAME 未配置时默认发送 "research_agent"
    private static final String STUDIO_DEFAULT_NAME = "research_agent";
    private static final String SRE_AGENT_NAME = "sre-agent";

    private final SREAgentGraph sreAgent;

    public SREAgentLoader(SREAgentGraph sreAgent) {
        this.sreAgent = sreAgent;
    }

    @Override
    public List<String> listAgents() {
        return List.of(SRE_AGENT_NAME);
    }

    @Override
    public Agent loadAgent(String name) {
        if (SRE_AGENT_NAME.equals(name) || STUDIO_DEFAULT_NAME.equals(name)) {
            return sreAgent;
        }
        throw new NoSuchElementException("Agent not found: " + name);
    }
}