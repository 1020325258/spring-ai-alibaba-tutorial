package com.yycome.sremate.trigger.console.command;

import java.util.function.Consumer;

/**
 * 控制台命令定义
 */
public class ConsoleCommand {
    private final String name;
    private final String description;
    private final String[] aliases;
    private final Consumer<CommandContext> action;

    public ConsoleCommand(String name, String description, String[] aliases, Consumer<CommandContext> action) {
        this.name = name;
        this.description = description;
        this.aliases = aliases != null ? aliases : new String[0];
        this.action = action;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public String[] getAliases() { return aliases; }
    public Consumer<CommandContext> getAction() { return action; }

    /**
     * 命令执行上下文
     */
    public static class CommandContext {
        private final Runnable showStats;
        private final Runnable showTrace;
        private final Runnable exit;

        public CommandContext(Runnable showStats, Runnable showTrace, Runnable exit) {
            this.showStats = showStats;
            this.showTrace = showTrace;
            this.exit = exit;
        }

        public Runnable getShowStats() { return showStats; }
        public Runnable getShowTrace() { return showTrace; }
        public Runnable getExit() { return exit; }
    }
}
