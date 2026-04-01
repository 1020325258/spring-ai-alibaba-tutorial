package com.yycome.sreagent.config.node;

/**
 * SRE-Agent 节点名称枚举
 * 统一管理所有流程节点名称和前端展示标题
 */
public enum SREAgentNodeName {

    ROUTER("router", "意图识别"),
    QUERY_AGENT("queryAgent", "数据查询"),
    INVESTIGATE_AGENT("investigateAgent", "问题排查"),
    ADMIN("admin", "智能推荐"),
    TOOL_CALL("tool_call", "工具调用"),
    START("__START__", "开始"),
    END("__END__", "结束");

    private final String nodeName;
    private final String displayTitle;

    SREAgentNodeName(String nodeName, String displayTitle) {
        this.nodeName = nodeName;
        this.displayTitle = displayTitle;
    }

    public String nodeName() {
        return nodeName;
    }

    public String displayTitle() {
        return displayTitle;
    }

    public static SREAgentNodeName fromNodeName(String nodeName) {
        if (nodeName == null) {
            return null;
        }
        for (SREAgentNodeName name : values()) {
            if (name.nodeName.equals(nodeName)) {
                return name;
            }
        }
        return null;
    }

    public static String getDisplayTitleByNodeName(String nodeName) {
        SREAgentNodeName name = fromNodeName(nodeName);
        return name != null ? name.displayTitle : "";
    }
}