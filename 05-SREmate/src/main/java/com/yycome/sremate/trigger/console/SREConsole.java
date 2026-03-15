package com.yycome.sremate.trigger.console;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yycome.sremate.infrastructure.config.EnvironmentConfig;
import com.yycome.sremate.infrastructure.service.DirectOutputHolder;
import com.yycome.sremate.infrastructure.service.MetricsCollector;
import com.yycome.sremate.infrastructure.service.TracingService;
import com.yycome.sremate.trigger.console.command.*;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;
import org.jline.reader.*;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.jline.terminal.Terminal.Signal;

/**
 * SRE值班客服命令行交互（触发层）
 * 支持流式输出和追踪
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "sre.console.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class SREConsole implements CommandLineRunner {

    @Autowired
    private ChatClient sreAgent;

    @Autowired
    private TracingService tracingService;

    @Autowired
    private MetricsCollector metricsCollector;

    @Autowired
    private DirectOutputHolder directOutputHolder;

    @Autowired
    private EnvironmentConfig environmentConfig;

    @Autowired
    private CommandRegistry commandRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    private SlashCommandCompleter slashCompleter;

    // 注：关闭对话历史功能，每次请求独立处理，防止 LLM 模仿历史模式跳过工具调用
    // private final List<Message> conversationHistory = new ArrayList<>();
    // private static final int MAX_HISTORY = 10;

    /** 当前正在进行的流式订阅，用于 Ctrl+C 中断 */
    private final AtomicReference<Disposable> currentSubscription = new AtomicReference<>();
    /** 标记当前流式响应是否被用户主动中断 */
    private final AtomicBoolean interrupted = new AtomicBoolean(false);

    @Override
    public void run(String... args) throws Exception {
        AnsiConsole.systemInstall();

        Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .build();

        // 初始化命令注册
        initCommands();

        // 创建斜杠命令补全器
        slashCompleter = new SlashCommandCompleter(commandRegistry);

        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .parser(new org.jline.reader.impl.DefaultParser())
                .history(new DefaultHistory())
                .completer(slashCompleter)
                .build();

        terminal.handle(Signal.INT, sig -> {
            Disposable sub = currentSubscription.get();
            if (sub != null && !sub.isDisposed()) {
                interrupted.set(true);
                sub.dispose();
            }
        });

        printBanner();

        while (true) {
            try {
                String input = reader.readLine(
                    Ansi.ansi().fg(Ansi.Color.BLUE).a("\n[" + environmentConfig.getCurrentEnv() + "] 你: ").reset().toString()
                );

                // 重置提示标记（每次新输入时）
                slashCompleter.resetHints();

                // 处理斜杠命令
                if (input.startsWith("/")) {
                    String cmdName = input.substring(1).trim();
                    ConsoleCommand cmd = commandRegistry.getCommand(cmdName);
                    if (cmd != null) {
                        cmd.getAction().accept(new ConsoleCommand.CommandContext(
                                this::showStats,
                                this::showTrace,
                                () -> {
                                    System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).a("\n" + metricsCollector.getReportSummary()).reset());
                                    System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).a("\n再见！感谢使用SRE值班客服Agent。").reset());
                                }
                        ));
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

                if (input.trim().isEmpty()) {
                    continue;
                }

                System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).a("\nSRE助手: ").reset());

                long startMs = System.currentTimeMillis();
                final long[] firstTokenMs = {-1};
                StringBuilder responseBuilder = new StringBuilder();
                interrupted.set(false);
                directOutputHolder.clear();

                java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                // 标记是否已触发直接输出旁路
                java.util.concurrent.atomic.AtomicBoolean directOutputUsed = new java.util.concurrent.atomic.AtomicBoolean(false);
                // 记录工具调用耗时
                java.util.concurrent.atomic.AtomicLong totalToolDuration = new java.util.concurrent.atomic.AtomicLong(0);

                // 每次请求独立处理，不传入对话历史，防止 LLM 模仿历史模式跳过工具调用
                Disposable sub = sreAgent.prompt()
                        .user(input)
                        .stream()
                        .content()
                        .doOnNext(chunk -> {
                            // 不再在首个 token 到达时立即输出
                            // 改为收集所有工具结果，在流结束时聚合输出
                            if (firstTokenMs[0] < 0) {
                                firstTokenMs[0] = System.currentTimeMillis();
                            }
                            // 如果没有工具结果，正常输出 LLM 内容
                            if (!directOutputHolder.hasOutput()) {
                                System.out.print(chunk);
                                responseBuilder.append(chunk);
                            }
                        })
                        .doOnComplete(() -> {
                            // 流结束时，聚合输出所有工具结果
                            if (directOutputHolder.hasOutput()) {
                                directOutputUsed.set(true);
                                String aggregatedOutput = aggregateToolResults(directOutputHolder);
                                System.out.println(aggregatedOutput);
                                responseBuilder.append(aggregatedOutput);
                            }
                            System.out.println();
                            latch.countDown();
                        })
                        .doOnError(e -> latch.countDown())
                        .doOnCancel(latch::countDown)
                        .subscribe();
                currentSubscription.set(sub);
                latch.await();
                currentSubscription.set(null);

                if (interrupted.get()) {
                    System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a("\n[已中断]").reset());
                    continue;
                }

                long totalMs = System.currentTimeMillis() - startMs;
                long ttfbMs  = firstTokenMs[0] < 0 ? totalMs : firstTokenMs[0] - startMs;

                // 显示耗时信息
                if (directOutputUsed.get()) {
                    System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN)
                            .a(String.format("⏱ 首字节: %dms | 工具耗时: %dms | 总耗时: %dms",
                                    ttfbMs, totalToolDuration.get(), totalMs)).reset());
                    // 显示工具调用列表
                    System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN)
                            .a(formatToolSummary(directOutputHolder)).reset());
                } else {
                    System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN)
                            .a(String.format("⏱ 首字节: %dms | 总耗时: %dms", ttfbMs, totalMs)).reset());
                }

                String response = responseBuilder.toString();

                copyToClipboardIfJson(response);

                // 注：不再维护对话历史，每次请求独立处理

            } catch (UserInterruptException e) {
                System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a("\n输入 'quit' 或 'exit' 退出").reset());
            } catch (EndOfFileException e) {
                System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).a("\n再见！").reset());
                break;
            } catch (Exception e) {
                System.out.println(Ansi.ansi().fg(Ansi.Color.RED).a("\n错误: " + e.getMessage()).reset());
                e.printStackTrace();
            }
        }

        terminal.close();
        AnsiConsole.systemUninstall();
    }

    private void initCommands() {
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

        commandRegistry.register(new ConsoleCommand("env", "查看或切换环境", null, c -> {
            showEnvInfo();
        }));
    }

    private void showStats() {
        System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).a("\n" + metricsCollector.getReportSummary()).reset());
    }

    private void showTrace() {
        String traceOutput = tracingService.visualizeRecentTraces(20);
        System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).a("\n" + traceOutput).reset());
    }

    /**
     * 显示环境信息，并支持交互式切换环境
     */
    private void showEnvInfo() {
        System.out.println();
        System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).bold().a("  当前环境: ").reset()
                .fg(Ansi.Color.GREEN).a(environmentConfig.getCurrentEnvDescription()).reset());
        System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).a("  ──────────────────────────────").reset());

        var envs = environmentConfig.getAvailableEnvironments();
        int i = 1;
        for (var entry : envs.entrySet()) {
            String marker = entry.getKey().equals(environmentConfig.getCurrentEnv()) ? " ✓" : "";
            System.out.println(Ansi.ansi().fg(Ansi.Color.WHITE)
                    .a(String.format("  %d. %s%s", i++, entry.getValue(), marker)).reset());
        }

        System.out.println();
        System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a("  输入序号切换环境，或按 Enter 返回: ").reset());

        try {
            Terminal terminal = TerminalBuilder.builder().system(true).build();
            LineReader envReader = LineReaderBuilder.builder().terminal(terminal).build();
            String input = envReader.readLine("");

            if (input.trim().isEmpty()) {
                return;
            }

            try {
                int choice = Integer.parseInt(input.trim());
                var envList = new java.util.ArrayList<>(envs.keySet());
                if (choice >= 1 && choice <= envList.size()) {
                    String newEnv = envList.get(choice - 1);
                    if (environmentConfig.switchEnv(newEnv)) {
                        System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN)
                                .a("\n  ✓ 已切换到: " + environmentConfig.getCurrentEnvDescription()).reset());
                    }
                } else {
                    System.out.println(Ansi.ansi().fg(Ansi.Color.RED).a("\n  无效的序号").reset());
                }
            } catch (NumberFormatException e) {
                System.out.println(Ansi.ansi().fg(Ansi.Color.RED).a("\n  请输入数字序号").reset());
            }

            terminal.close();
        } catch (Exception e) {
            System.out.println(Ansi.ansi().fg(Ansi.Color.RED).a("\n  切换失败: " + e.getMessage()).reset());
        }
    }

    private void printBanner() {
        System.out.println();
        System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).bold()
                .a("  ███████╗██████╗ ███████╗███╗   ███╗ █████╗ ████████╗███████╗").reset());
        System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).bold()
                .a("  ██╔════╝██╔══██╗██╔════╝████╗ ████║██╔══██╗╚══██╔══╝██╔════╝").reset());
        System.out.println(Ansi.ansi().bold()
                .a("  ███████╗██████╔╝█████╗  ██╔████╔██║███████║   ██║   █████╗  ").reset());
        System.out.println(Ansi.ansi().bold()
                .a("  ╚════██║██╔══██╗██╔══╝  ██║╚██╔╝██║██╔══██║   ██║   ██╔══╝  ").reset());
        System.out.println(Ansi.ansi().fg(Ansi.Color.BLUE).bold()
                .a("  ███████║██║  ██║███████╗██║ ╚═╝ ██║██║  ██║   ██║   ███████╗").reset());
        System.out.println(Ansi.ansi().fg(Ansi.Color.BLUE).bold()
                .a("  ╚══════╝╚═╝  ╚═╝╚══════╝╚═╝     ╚═╝╚═╝  ╚═╝   ╚═╝   ╚══════╝").reset());
        System.out.println();

        System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN)
                .a("  ──────────────────────────────────────────────────────────────").reset());
        System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).bold()
                .a("      智能 SRE 值班助手  ·  v2.0  ·  Powered by Qwen-Turbo      ").reset());
        System.out.println(Ansi.ansi().fg(Ansi.Color.MAGENTA)
                .a("      当前环境: " + environmentConfig.getCurrentEnvDescription() + "      ").reset());
        System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN)
                .a("  ──────────────────────────────────────────────────────────────").reset());
        System.out.println();

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
        System.out.println(Ansi.ansi().fg(Ansi.Color.WHITE).bold().a("  ├─ /env   ").reset()
                .fg(Ansi.Color.YELLOW).a(" 查看或切换环境").reset());
        System.out.println(Ansi.ansi().fg(Ansi.Color.WHITE).bold().a("  └─ /quit  ").reset()
                .fg(Ansi.Color.YELLOW).a(" 退出程序").reset());
        System.out.println();
        System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).a("  输入 / 查看所有命令  ·  Tab 自动补全").reset());
        System.out.println();
    }

    private void copyToClipboardIfJson(String response) {
        String trimmed = response.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return;
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"pbcopy"});
            process.getOutputStream().write(trimmed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            process.getOutputStream().close();
            process.waitFor();
            System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a("✓ JSON 已复制到剪贴板").reset());
        } catch (Exception e) {
            // 剪贴板复制失败不影响主流程
        }
    }

    /**
     * 聚合多个工具调用结果
     * 策略：
     * 1. 如果只有一个结果，直接输出
     * 2. 如果是多个 ontologyQuery 结果，尝试按层级合并
     */
    private String aggregateToolResults(DirectOutputHolder holder) {
        List<DirectOutputHolder.ToolResult> results = holder.getResults();
        if (results.isEmpty()) {
            return "";
        }

        // 单个结果直接返回
        if (results.size() == 1) {
            return results.get(0).result;
        }

        // 多个结果：尝试聚合 ontologyQuery 结果
        try {
            return aggregateOntologyQueryResults(results);
        } catch (Exception e) {
            // 聚合失败，按原样输出每个结果
            StringBuilder sb = new StringBuilder();
            for (DirectOutputHolder.ToolResult r : results) {
                if (sb.length() > 0) sb.append("\n\n");
                sb.append(r.result);
            }
            return sb.toString();
        }
    }

    /**
     * 聚合多个 ontologyQuery 结果
     * 场景：先查 Order 获取合同列表，再查每个合同的签约单据和节点
     */
    @SuppressWarnings("unchecked")
    private String aggregateOntologyQueryResults(List<DirectOutputHolder.ToolResult> results) {
        if (results.isEmpty()) {
            return "";
        }

        // 解析所有结果
        List<Map<String, Object>> parsedResults = new ArrayList<>();
        Map<String, Object> baseResult = null; // Order 查询结果
        List<Map<String, Object>> contractResults = new ArrayList<>(); // Contract 查询结果

        for (DirectOutputHolder.ToolResult r : results) {
            try {
                Map<String, Object> parsed = objectMapper.readValue(r.result, Map.class);
                parsedResults.add(parsed);

                // 区分 Order 查询和 Contract 查询
                if ("Order".equals(parsed.get("queryEntity"))) {
                    baseResult = parsed;
                } else if ("Contract".equals(parsed.get("queryEntity"))) {
                    contractResults.add(parsed);
                }
            } catch (Exception e) {
                // 解析失败，跳过
            }
        }

        // 如果有 Order 结果，将 Contract 结果合并到对应合同
        if (baseResult != null && !contractResults.isEmpty()) {
            Object recordsObj = baseResult.get("records");
            if (recordsObj instanceof List) {
                List<Map<String, Object>> records = (List<Map<String, Object>>) recordsObj;

                // 构建合同编号到详细结果的映射
                Map<String, Map<String, Object>> contractDetails = new HashMap<>();
                for (Map<String, Object> cr : contractResults) {
                    Object crRecords = cr.get("records");
                    if (crRecords instanceof List && !((List<?>) crRecords).isEmpty()) {
                        // 从 Contract 结果中提取关联数据
                        extractContractRelations(cr, contractDetails);
                    }
                }

                // 将关联数据合并到 Order 结果的合同中
                for (Map<String, Object> contract : records) {
                    String contractCode = (String) contract.get("contractCode");
                    if (contractCode != null && contractDetails.containsKey(contractCode)) {
                        contract.putAll(contractDetails.get(contractCode));
                    }
                }
            }

            try {
                return objectMapper.writeValueAsString(baseResult);
            } catch (Exception e) {
                return baseResult.toString();
            }
        }

        // 无法聚合，按原样输出第一个结果
        try {
            return objectMapper.writeValueAsString(parsedResults.get(0));
        } catch (Exception e) {
            return results.get(0).result;
        }
    }

    /**
     * 从 Contract 查询结果中提取关联数据
     */
    @SuppressWarnings("unchecked")
    private void extractContractRelations(Map<String, Object> contractResult,
                                          Map<String, Map<String, Object>> contractDetails) {
        String queryValue = (String) contractResult.get("queryValue");
        if (queryValue == null) return;

        // 获取所有非基础字段作为关联数据
        Map<String, Object> details = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : contractResult.entrySet()) {
            String key = entry.getKey();
            // 跳过基础字段
            if ("queryEntity".equals(key) || "queryValue".equals(key) || "records".equals(key)) {
                continue;
            }
            // 保留关联数据
            details.put(key, entry.getValue());
        }

        // 合并到已有的详情中
        if (!details.isEmpty()) {
            contractDetails.merge(queryValue, details, (oldVal, newVal) -> {
                Map<String, Object> merged = new LinkedHashMap<>(oldVal);
                merged.putAll(newVal);
                return merged;
            });
        }
    }

    /**
     * 格式化工具调用摘要
     */
    private String formatToolSummary(DirectOutputHolder holder) {
        List<DirectOutputHolder.ToolResult> results = holder.getResults();
        if (results.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(" | 工具: ");

        Map<String, Long> toolCounts = new LinkedHashMap<>();
        for (DirectOutputHolder.ToolResult r : results) {
            toolCounts.merge(r.toolName, 1L, Long::sum);
        }

        boolean first = true;
        for (Map.Entry<String, Long> entry : toolCounts.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(entry.getKey());
            if (entry.getValue() > 1) {
                sb.append("(").append(entry.getValue()).append(")");
            }
            first = false;
        }

        return sb.toString();
    }
}
