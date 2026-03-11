package com.yycome.sremate.trigger.console.command;

/**
 * 工具信息，用于 /tools 命令展示
 */
public class ToolInfo {
    private final String name;
    private final String keywords;
    private final String example;

    public ToolInfo(String name, String keywords, String example) {
        this.name = name;
        this.keywords = keywords;
        this.example = example;
    }

    public String getName() { return name; }
    public String getKeywords() { return keywords; }
    public String getExample() { return example; }
}
