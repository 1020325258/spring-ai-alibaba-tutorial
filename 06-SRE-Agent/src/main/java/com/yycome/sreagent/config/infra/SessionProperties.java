package com.yycome.sreagent.config.infra;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "sreagent.session")
public class SessionProperties {

    /** 保留最近对话轮次数（每轮 = user + assistant 各一条消息） */
    private int maxRecentTurns = 5;

    /** 意图识别置信度阈值（低于此值路由到 admin） */
    private double confidenceThreshold = 0.6;
}
