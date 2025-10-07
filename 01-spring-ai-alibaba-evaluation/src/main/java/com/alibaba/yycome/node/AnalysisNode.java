package com.alibaba.yycome.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.yycome.model.dto.EvaluationResult;
import io.vertx.ext.auth.User;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.converter.BeanOutputConverter;

import java.util.HashMap;
import java.util.Map;

public class AnalysisNode implements NodeAction {

    public ChatClient analysisAgent;

    private final BeanOutputConverter<EvaluationResult> converter;

    public AnalysisNode(ChatClient analysisAgent) {
        this.analysisAgent = analysisAgent;
        this.converter = new BeanOutputConverter<>(EvaluationResult.class);
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        System.out.println("执行了 analysis");
        Map<String, Object> result = new HashMap<>();
        String input = """
                SQL更新流程如下：
                1. **连接与认证**：连接器接收客户端连接，并进行认证和授权。
                2. **SQL解析与优化**：对SQL语句进行词法分析和语法分析，优化器决定使用哪个索引，并生成执行计划。
                3. **执行更新操作**：
                   - 判断要更新的数据所在的数据页是否在内存（BufferPool）中；若不在，则从磁盘加载到BufferPool。
                   - 执行器在BufferPool中更新该行数据。
                4. **日志写入（两阶段提交）**：
                   - InnoDB存储引擎将更新操作记录到 **redo log**，并将其状态设为 **prepare**。
                   - 执行器生成 **binlog** 并将其写入磁盘。
                   - 事务提交，InnoDB将 redo log 的状态改为 **commit**，表示事务已成功提交。
                5. **持久化**：后台IO线程不定期将BufferPool中的脏页（已修改但未写入磁盘的数据）刷入磁盘，完成最终持久化。
                该流程确保了事务的原子性、一致性、隔离性和持久性（ACID），并通过 redo log 和 binlog 的两阶段提交机制保证了主从复制和崩溃恢复的一致性。
                """;
        UserMessage userMessage = new UserMessage(input);
        String content = analysisAgent.prompt().messages(userMessage).call().content();
        result.put("analysis_content", content);

//        EvaluationResult convert = converter.convert(content);
//        System.out.println("反序列化后结果：" + convert.toString());

        return result;
    }
}
