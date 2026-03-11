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
