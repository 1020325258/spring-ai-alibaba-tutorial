package com.alibaba.yycome.config;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

@Service
public class ScoreService {

    @Tool(description = "获取指定同学的分数")
    public String getScore(String name) {
        switch (name) {
            case "张三":
                return "99.5";
            case "李四":
                return "95.0";
            default:
                return "没有记录" + name + "的分数";
        }
    }

}
