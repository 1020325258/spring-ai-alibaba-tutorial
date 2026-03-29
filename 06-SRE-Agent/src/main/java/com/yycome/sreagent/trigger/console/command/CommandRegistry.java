package com.yycome.sreagent.trigger.console.command;

import org.fusesource.jansi.Ansi;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 命令注册表，管理所有斜杠命令
 */
@Component
public class CommandRegistry {

    private final Map<String, ConsoleCommand> commands = new LinkedHashMap<>();
    private final List<ToolInfo> tools = new ArrayList<>();

    public CommandRegistry() {
        initTools();
    }

    private void initTools() {
        tools.add(new ToolInfo("ontologyQuery", "合同/订单/报价单/版式/配置表", "C1773208288511314合同数据"));
        tools.add(new ToolInfo("querySubOrderInfo", "子单/S单", "826031111000001859子单"));
    }

    public void register(ConsoleCommand command) {
        commands.put(command.getName(), command);
        for (String alias : command.getAliases()) {
            commands.put(alias, command);
        }
    }

    public ConsoleCommand getCommand(String name) {
        return commands.get(name);
    }

    public List<String> getCommandNames() {
        return commands.values().stream()
                .map(ConsoleCommand::getName)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 格式化命令帮助文本（用于 /help 和 / 提示）
     */
    public String formatHelpText() {
        StringBuilder sb = new StringBuilder();
        sb.append(Ansi.ansi().fg(Ansi.Color.YELLOW).bold().a("\n可用命令:\n").reset());
        for (ConsoleCommand cmd : commands.values().stream()
                .collect(Collectors.toMap(ConsoleCommand::getName, c -> c, (a, b) -> a))
                .values()) {
            sb.append(Ansi.ansi().fg(Ansi.Color.WHITE).bold().a("  /" + cmd.getName()).reset());
            sb.append(Ansi.ansi().fg(Ansi.Color.YELLOW).a("   " + cmd.getDescription() + "\n").reset());
        }
        return sb.toString();
    }

    /**
     * 格式化工具列表（用于 /tools 命令）
     */
    public String formatToolsText() {
        StringBuilder sb = new StringBuilder();
        sb.append(Ansi.ansi().fg(Ansi.Color.CYAN).bold().a("\n数据查询工具列表\n").reset());
        sb.append(Ansi.ansi().fg(Ansi.Color.CYAN).a("─".repeat(73) + "\n").reset());
        sb.append(String.format("%-28s %-20s %s\n", "工具名称", "触发关键词", "查询示例"));
        sb.append(Ansi.ansi().fg(Ansi.Color.CYAN).a("─".repeat(73) + "\n").reset());

        for (ToolInfo tool : tools) {
            sb.append(String.format("%-28s %-20s %s\n", tool.getName(), tool.getKeywords(), tool.getExample()));
        }

        sb.append(Ansi.ansi().fg(Ansi.Color.CYAN).a("─".repeat(73) + "\n").reset());
        sb.append(Ansi.ansi().fg(Ansi.Color.YELLOW).a("共 " + tools.size() + " 个工具\n").reset());
        return sb.toString();
    }
}
