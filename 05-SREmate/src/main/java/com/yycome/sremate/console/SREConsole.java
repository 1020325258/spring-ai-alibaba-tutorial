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

import java.util.ArrayList;
import java.util.List;

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

        // 欢迎信息
        System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).a("╔════════════════════════════════════════════╗").reset());
        System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).a("║      SRE值班客服Agent v2.0                  ║").reset());
        System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).a("║      支持流式输出、追踪、缓存               ║").reset());
        System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).a("╚════════════════════════════════════════════╝").reset());
        System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).a("\n输入问题开始咨询，输入 'quit' 或 'exit' 退出").reset());
        System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a("特殊命令:\n").reset());
        System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a("  stats - 查看性能统计\n").reset());
        System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a("  trace - 查看当前会话追踪链路\n").reset());
        System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a("  使用 ↑↓ 键查看历史命令，Ctrl+C 取消当前输入\n").reset());

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

                // 调用Agent（流式输出）
                System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).a("\nSRE助手: ").reset());

                StringBuilder responseBuilder = new StringBuilder();

                sreAgent.prompt()
                        .messages(conversationHistory)
                        .stream()
                        .content()
                        .doOnNext(chunk -> {
                            // 实时输出每个chunk
                            System.out.print(chunk);
                            responseBuilder.append(chunk);
                        })
                        .doOnComplete(() -> {
                            // 输出完成，换行
                            System.out.println();
                        })
                        .blockLast();

                String response = responseBuilder.toString();

                // 添加助手消息到历史
                conversationHistory.add(new AssistantMessage(response));

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
}
