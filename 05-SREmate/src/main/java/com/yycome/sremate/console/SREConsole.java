package com.yycome.sremate.console;

import com.yycome.sremate.domain.TraceSession;
import com.yycome.sremate.service.MetricsCollector;
import com.yycome.sremate.service.TracingService;
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
 * SRE值班客服命令行交互
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

    private final List<Message> conversationHistory = new ArrayList<>();
    private TraceSession currentSession;

    /** 保留的最大消息条数（user + assistant 各算 1 条，10 条 = 5 轮对话） */
    private static final int MAX_HISTORY = 10;

    /** 当前正在进行的流式订阅，用于 Ctrl+C 中断 */
    private final AtomicReference<Disposable> currentSubscription = new AtomicReference<>();
    /** 标记当前流式响应是否被用户主动中断 */
    private final AtomicBoolean interrupted = new AtomicBoolean(false);

    @Override
    public void run(String... args) throws Exception {
        // 安装JAnsi
        AnsiConsole.systemInstall();

        // 创建终端
        Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .build();

        // 创建行读取器
        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .parser(new org.jline.reader.impl.DefaultParser())
                .history(new DefaultHistory())
                .build();

        // 用 jline 原生信号注册 Ctrl+C 处理（raw 模式下 sun.misc.Signal 收不到 SIGINT）
        // 流式输出阶段：取消订阅；输入阶段：保留 jline 默认行为（抛 UserInterruptException）
        terminal.handle(Signal.INT, sig -> {
            Disposable sub = currentSubscription.get();
            if (sub != null && !sub.isDisposed()) {
                interrupted.set(true);
                sub.dispose();
            }
        });

        // 欢迎信息
        printBanner();

        while (true) {
            try {
                // 读取用户输入
                String input = reader.readLine(
                    Ansi.ansi().fg(Ansi.Color.BLUE).a("\n你: ").reset().toString()
                );

                // 检查退出命令
                if ("quit".equalsIgnoreCase(input) || "exit".equalsIgnoreCase(input)) {
                    // 结束当前会话
                    if (currentSession != null) {
                        tracingService.endSession(currentSession.getSessionId());
                    }

                    // 显示性能统计
                    System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).a("\n" + metricsCollector.getReportSummary()).reset());

                    System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).a("\n再见！感谢使用SRE值班客服Agent。").reset());
                    break;
                }

                // 处理特殊命令
                if ("stats".equalsIgnoreCase(input)) {
                    showStats();
                    continue;
                }

                if ("trace".equalsIgnoreCase(input)) {
                    showTrace();
                    continue;
                }

                // 跳过空输入
                if (input.trim().isEmpty()) {
                    continue;
                }

                // 开始新的追踪会话
                if (currentSession == null) {
                    currentSession = tracingService.startSession("user", input);
                }

                // 添加用户消息到历史
                conversationHistory.add(new UserMessage(input));

                // 调用Agent
                System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).a("\nSRE助手: ").reset());

                long startMs = System.currentTimeMillis();
                final long[] firstTokenMs = {-1};
                StringBuilder responseBuilder = new StringBuilder();
                interrupted.set(false);

                // 用 CountDownLatch 阻塞主线程，同时保留 Disposable 供 Ctrl+C 中断
                java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                Disposable sub = sreAgent.prompt()
                        .messages(conversationHistory)
                        .stream()
                        .content()
                        .doOnNext(chunk -> {
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
                    conversationHistory.remove(conversationHistory.size() - 1); // 移除本次未完成的 user 消息
                    continue;
                }

                long totalMs = System.currentTimeMillis() - startMs;
                long ttfbMs  = firstTokenMs[0] < 0 ? totalMs : firstTokenMs[0] - startMs;
                System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN)
                        .a(String.format("⏱ 首字节: %dms  总耗时: %dms", ttfbMs, totalMs)).reset());

                String response = responseBuilder.toString();

                // JSON 响应自动复制到剪贴板
                copyToClipboardIfJson(response);

                // 添加助手消息到历史，并裁剪超出部分
                conversationHistory.add(new AssistantMessage(response));
                if (conversationHistory.size() > MAX_HISTORY) {
                    conversationHistory.subList(0, conversationHistory.size() - MAX_HISTORY).clear();
                }

            } catch (UserInterruptException e) {
                // 处理Ctrl+C
                System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a("\n输入 'quit' 或 'exit' 退出").reset());
            } catch (EndOfFileException e) {
                // 处理Ctrl+D
                System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).a("\n再见！").reset());
                break;
            } catch (Exception e) {
                System.out.println(Ansi.ansi().fg(Ansi.Color.RED).a("\n错误: " + e.getMessage()).reset());
                e.printStackTrace();
            }
        }

        // 清理资源
        terminal.close();
        AnsiConsole.systemUninstall();
    }

    /**
     * 显示性能统计
     */
    private void showStats() {
        System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).a("\n" + metricsCollector.getReportSummary()).reset());
    }

    /**
     * 显示追踪链路
     */
    private void showTrace() {
        if (currentSession != null) {
            String traceChain = tracingService.visualizeTraceChain(currentSession.getSessionId());
            System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).a("\n" + traceChain).reset());
        } else {
            System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a("\n暂无追踪数据").reset());
        }
    }

    /**
     * 炫酷启动 Banner
     */
    private void printBanner() {
        System.out.println();
        // ASCII art "SREmate" — 渐变色（CYAN → WHITE → BLUE）
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

        // 分隔线 + 副标题
        System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN)
                .a("  ──────────────────────────────────────────────────────────────").reset());
        System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).bold()
                .a("      智能 SRE 值班助手  ·  v2.0  ·  Powered by Qwen-Turbo      ").reset());
        System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN)
                .a("  ──────────────────────────────────────────────────────────────").reset());
        System.out.println();

        // 命令列表
        System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).bold().a("  可用命令").reset()
                .fg(Ansi.Color.WHITE).a("  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·").reset());
        System.out.println(Ansi.ansi().fg(Ansi.Color.WHITE).bold().a("  ├─ stats ").reset()
                .fg(Ansi.Color.YELLOW).a(" 查看性能统计").reset());
        System.out.println(Ansi.ansi().fg(Ansi.Color.WHITE).bold().a("  ├─ trace ").reset()
                .fg(Ansi.Color.YELLOW).a(" 查看当前会话追踪链路").reset());
        System.out.println(Ansi.ansi().fg(Ansi.Color.WHITE).bold().a("  ├─ quit  ").reset()
                .fg(Ansi.Color.YELLOW).a(" 退出程序").reset());
        System.out.println(Ansi.ansi().fg(Ansi.Color.WHITE).bold().a("  └─ ↑ / ↓ ").reset()
                .fg(Ansi.Color.YELLOW).a(" 浏览历史命令  ·  Ctrl+C 取消当前输入").reset());
        System.out.println();
        System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).a("  请输入问题开始咨询...").reset());
        System.out.println();
    }

    /**
     * 若响应内容是 JSON（以 { 或 [ 开头），自动复制到系统剪贴板
     */
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
