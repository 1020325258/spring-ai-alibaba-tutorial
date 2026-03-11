package com.yycome.sremate.trigger.console;

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
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

import java.util.ArrayList;
import java.util.List;
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
    private CommandRegistry commandRegistry;

    private SlashCommandCompleter slashCompleter;

    private final List<Message> conversationHistory = new ArrayList<>();

    /** 保留的最大消息条数（user + assistant 各算 1 条，10 条 = 5 轮对话） */
    private static final int MAX_HISTORY = 10;

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
                    Ansi.ansi().fg(Ansi.Color.BLUE).a("\n你: ").reset().toString()
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

                conversationHistory.add(new UserMessage(input));

                System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).a("\nSRE助手: ").reset());

                long startMs = System.currentTimeMillis();
                final long[] firstTokenMs = {-1};
                StringBuilder responseBuilder = new StringBuilder();
                interrupted.set(false);
                directOutputHolder.clear();

                java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                // 标记是否已触发直接输出旁路
                java.util.concurrent.atomic.AtomicBoolean directOutputUsed = new java.util.concurrent.atomic.AtomicBoolean(false);

                Disposable sub = sreAgent.prompt()
                        .messages(conversationHistory)
                        .stream()
                        .content()
                        .doOnNext(chunk -> {
                            // 首个 token 到达时，工具调用已全部完成。
                            // 若 DirectOutputHolder 有值，说明这是数据查询请求，直接输出结果，跳过 LLM 归纳。
                            if (firstTokenMs[0] < 0 && directOutputHolder.hasOutput()) {
                                firstTokenMs[0] = System.currentTimeMillis();
                                directOutputUsed.set(true);
                                String directOutput = directOutputHolder.getAndClear();
                                System.out.println(directOutput);
                                responseBuilder.append(directOutput);
                                Disposable current = currentSubscription.get();
                                if (current != null) current.dispose();
                                return;
                            }
                            // 已走直接输出路径，忽略后续 LLM token
                            if (directOutputUsed.get()) return;

                            if (firstTokenMs[0] < 0) {
                                firstTokenMs[0] = System.currentTimeMillis();
                            }
                            System.out.print(chunk);
                            responseBuilder.append(chunk);
                        })
                        .doOnComplete(() -> {
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
                    conversationHistory.remove(conversationHistory.size() - 1);
                    continue;
                }

                long totalMs = System.currentTimeMillis() - startMs;
                long ttfbMs  = firstTokenMs[0] < 0 ? totalMs : firstTokenMs[0] - startMs;
                System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN)
                        .a(String.format("⏱ 首字节: %dms  总耗时: %dms", ttfbMs, totalMs)).reset());

                String response = responseBuilder.toString();

                copyToClipboardIfJson(response);

                // 数据查询结果不写入对话历史，防止 LLM 下次直接复读历史数据而跳过实时工具调用
                String historyContent = directOutputUsed.get()
                        ? "[已调用工具查询并直接输出数据，结果不保留在上下文中]"
                        : response;
                conversationHistory.add(new AssistantMessage(historyContent));
                if (conversationHistory.size() > MAX_HISTORY) {
                    conversationHistory.subList(0, conversationHistory.size() - MAX_HISTORY).clear();
                }

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
    }

    private void showStats() {
        System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).a("\n" + metricsCollector.getReportSummary()).reset());
    }

    private void showTrace() {
        String traceOutput = tracingService.visualizeRecentTraces(20);
        System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).a("\n" + traceOutput).reset());
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
}
