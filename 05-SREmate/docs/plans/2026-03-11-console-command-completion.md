# Console Command Completion Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add slash command completion and `/tools` command to SRE Console terminal.

**Architecture:** Create a command registry system with JLine Completer integration. Commands are registered in a centralized registry, and a custom completer handles `/` prefix detection and Tab completion.

**Tech Stack:** JLine 3 (already in project), Spring AI @Tool annotations

---

## Task 1: Create ToolInfo class

**Files:**
- Create: `05-SREmate/src/main/java/com/yycome/sremate/trigger/console/command/ToolInfo.java`

**Step 1: Write ToolInfo class**

```java
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
```

**Step 2: Commit**

```bash
git add 05-SREmate/src/main/java/com/yycome/sremate/trigger/console/command/ToolInfo.java
git commit -m "feat: add ToolInfo class for /tools command display"
```

---

## Task 2: Create ConsoleCommand class

**Files:**
- Create: `05-SREmate/src/main/java/com/yycome/sremate/trigger/console/command/ConsoleCommand.java`

**Step 1: Write ConsoleCommand class**

```java
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
```

**Step 2: Commit**

```bash
git add 05-SREmate/src/main/java/com/yycome/sremate/trigger/console/command/ConsoleCommand.java
git commit -m "feat: add ConsoleCommand class for command definition"
```

---

## Task 3: Create CommandRegistry class

**Files:**
- Create: `05-SREmate/src/main/java/com/yycome/sremate/trigger/console/command/CommandRegistry.java`

**Step 1: Write CommandRegistry class**

```java
package com.yycome.sremate.trigger.console.command;

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
        tools.add(new ToolInfo("queryBudgetBillList", "报价单/报价", "826031111000001859报价单"));
        tools.add(new ToolInfo("queryContractsByOrderId", "订单号查合同", "826031111000001859合同数据"));
        tools.add(new ToolInfo("queryContractData", "C前缀合同号查询", "C1773208288511314合同数据"));
        tools.add(new ToolInfo("queryContractFormId", "版式/form_id", "C1773208288511314版式"));
        tools.add(new ToolInfo("queryContractConfig", "配置表", "826031111000001859配置表"));
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
```

**Step 2: Commit**

```bash
git add 05-SREmate/src/main/java/com/yycome/sremate/trigger/console/command/CommandRegistry.java
git commit -m "feat: add CommandRegistry for slash command management"
```

---

## Task 4: Create SlashCommandCompleter

**Files:**
- Create: `05-SREmate/src/main/java/com/yycome/sremate/trigger/console/command/SlashCommandCompleter.java`

**Step 1: Write SlashCommandCompleter class**

```java
package com.yycome.sremate.trigger.console.command;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.List;

/**
 * 斜杠命令补全器
 * 输入 / 时自动显示命令提示，Tab 键补全命令名
 */
public class SlashCommandCompleter implements Completer {

    private final CommandRegistry registry;
    private boolean hasShownHints = false;

    public SlashCommandCompleter(CommandRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        String buffer = line.line();

        // 输入 / 时显示命令提示
        if (buffer.equals("/") && !hasShownHints) {
            System.out.print(registry.formatHelpText());
            System.out.flush();
            hasShownHints = true;
        }

        // 补全以 / 开头的命令
        if (buffer.startsWith("/")) {
            String prefix = buffer.substring(1);
            for (String name : registry.getCommandNames()) {
                if (name.startsWith(prefix)) {
                    candidates.add(new Candidate("/" + name, "/" + name, null, null, null, null, true));
                }
            }
        }
    }

    public void resetHints() {
        hasShownHints = false;
    }
}
```

**Step 2: Commit**

```bash
git add 05-SREmate/src/main/java/com/yycome/sremate/trigger/console/command/SlashCommandCompleter.java
git commit -m "feat: add SlashCommandCompleter for / command completion"
```

---

## Task 5: Modify SREConsole to integrate commands

**Files:**
- Modify: `05-SREmate/src/main/java/com/yycome/sremate/trigger/console/SREConsole.java`

**Step 1: Add imports and fields**

在文件顶部添加 import：

```java
import com.yycome.sremate.trigger.console.command.*;
```

在类中添加字段：

```java
@Autowired
private CommandRegistry commandRegistry;

private SlashCommandCompleter slashCompleter;
```

**Step 2: Initialize commands and completer**

在 `run()` 方法中，`LineReaderBuilder` 构建之前添加：

```java
// 初始化命令注册
initCommands();

// 创建斜杠命令补全器
slashCompleter = new SlashCommandCompleter(commandRegistry);
```

在 `LineReaderBuilder` 中添加 completer：

```java
LineReader reader = LineReaderBuilder.builder()
        .terminal(terminal)
        .parser(new org.jline.reader.impl.DefaultParser())
        .history(new DefaultHistory())
        .completer(slashCompleter)  // 添加这行
        .build();
```

**Step 3: Add initCommands method**

在类中添加方法：

```java
private void initCommands() {
    ConsoleCommand.CommandContext ctx = new ConsoleCommand.CommandContext(
            this::showStats,
            this::showTrace,
            () -> {
                System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).a("\n" + metricsCollector.getReportSummary()).reset());
                System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).a("\n再见！感谢使用SRE值班客服Agent。").reset());
            }
    );

    commandRegistry.register(new ConsoleCommand("tools", "显示所有数据查询工具", null, c -> {
        System.out.println(commandRegistry.formatToolsText());
    }));

    commandRegistry.register(new ConsoleCommand("help", "显示帮助信息", null, c -> {
        System.out.println(commandRegistry.formatHelpText());
    }));

    commandRegistry.register(new ConsoleCommand("stats", "查看性能统计", new String[]{"stat"}, c -> {
        c.getShowStats().run();
    }));

    commandRegistry.register(new ConsoleCommand("trace", "查看最近工具调用记录", null, c -> {
        c.getShowTrace().run();
    }));

    commandRegistry.register(new ConsoleCommand("quit", "退出程序", new String[]{"exit", "q"}, c -> {
        c.getExit().run();
    }));
}
```

**Step 4: Modify input handling to support slash commands**

将现有的命令处理逻辑替换为：

```java
String input = reader.readLine(
    Ansi.ansi().fg(Ansi.Color.BLUE).a("\n你: ").reset().toString()
);

// 重置提示标记（每次新输入时）
slashCompleter.resetHints();

// 处理斜杠命令
if (input.startsWith("/")) {
    String cmdName = input.substring(1).trim();
    ConsoleCommand cmd = commandRegistry.getCommand(cmdName);
    if (cmd != null) {
        ConsoleCommand.CommandContext ctx = new ConsoleCommand.CommandContext(
                this::showStats,
                this::showTrace,
                () -> {
                    System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).a("\n" + metricsCollector.getReportSummary()).reset());
                    System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).a("\n再见！感谢使用SRE值班客服Agent。").reset());
                }
        );
        cmd.getAction().accept(ctx);
        if ("quit".equals(cmd.getName()) || "exit".equals(cmd.getName()) || "q".equals(cmdName)) {
            break;
        }
    } else {
        System.out.println(Ansi.ansi().fg(Ansi.Color.RED).a("\n未知命令: " + input + "，输入 /help 查看可用命令").reset());
    }
    continue;
}

// 兼容无斜杠的旧命令
if ("quit".equalsIgnoreCase(input) || "exit".equalsIgnoreCase(input)) {
    System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).a("\n" + metricsCollector.getReportSummary()).reset());
    System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).a("\n再见！感谢使用SRE值班客服Agent。").reset());
    break;
}

if ("stats".equalsIgnoreCase(input)) {
    showStats();
    continue;
}

if ("trace".equalsIgnoreCase(input)) {
    showTrace();
    continue;
}
```

**Step 5: Update banner to show slash commands**

修改 `printBanner()` 方法中的命令提示部分：

```java
System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).bold().a("  可用命令").reset()
        .fg(Ansi.Color.WHITE).a("  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·").reset());
System.out.println(Ansi.ansi().fg(Ansi.Color.WHITE).bold().a("  ├─ /tools ").reset()
        .fg(Ansi.Color.YELLOW).a(" 显示数据查询工具").reset());
System.out.println(Ansi.ansi().fg(Ansi.Color.WHITE).bold().a("  ├─ /help  ").reset()
        .fg(Ansi.Color.YELLOW).a(" 显示帮助信息").reset());
System.out.println(Ansi.ansi().fg(Ansi.Color.WHITE).bold().a("  ├─ /stats ").reset()
        .fg(Ansi.Color.YELLOW).a(" 查看性能统计").reset());
System.out.println(Ansi.ansi().fg(Ansi.Color.WHITE).bold().a("  ├─ /trace ").reset()
        .fg(Ansi.Color.YELLOW).a(" 查看最近工具调用记录").reset());
System.out.println(Ansi.ansi().fg(Ansi.Color.WHITE).bold().a("  └─ /quit  ").reset()
        .fg(Ansi.Color.YELLOW).a(" 退出程序").reset());
System.out.println();
System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).a("  输入 / 查看所有命令  ·  Tab 自动补全").reset());
```

**Step 6: Commit**

```bash
git add 05-SREmate/src/main/java/com/yycome/sremate/trigger/console/SREConsole.java
git commit -m "feat: integrate slash command completion in SREConsole"
```

---

## Task 6: Test the feature

**Step 1: Build and run the application**

```bash
cd 05-SREmate && mvn spring-boot:run
```

**Step 2: Manual test checklist**

1. 启动后检查 banner 是否显示斜杠命令列表
2. 输入 `/`，检查是否弹出命令提示框
3. 输入 `/t` 后按 Tab，检查是否补全为 `/tools`
4. 输入 `/tools`，检查是否显示工具列表（含触发关键词和查询示例）
5. 输入 `/help`，检查是否显示帮助信息
6. 输入 `/stats`，检查是否显示性能统计
7. 输入 `/quit`，检查是否正常退出

**Step 3: Run integration tests**

```bash
./05-SREmate/scripts/run-integration-tests.sh
```

---

## Summary

| Task | Description | Files |
|------|-------------|-------|
| 1 | Create ToolInfo | `ToolInfo.java` |
| 2 | Create ConsoleCommand | `ConsoleCommand.java` |
| 3 | Create CommandRegistry | `CommandRegistry.java` |
| 4 | Create SlashCommandCompleter | `SlashCommandCompleter.java` |
| 5 | Modify SREConsole | `SREConsole.java` |
| 6 | Test | Manual + Integration tests |
