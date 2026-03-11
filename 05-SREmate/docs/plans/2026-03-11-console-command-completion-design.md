# SRE Console 斜杠命令补全功能设计

**设计日期**：2026-03-11

---

## 1. 需求概述

在 SRE Console 终端中实现：
1. `/tools` 命令：展示所有可用的数据查询工具，包含触发关键词和查询示例
2. `/` 自动提示：输入 `/` 时自动弹出可用命令列表
3. Tab 补全：按 Tab 键自动补全命令名

---

## 2. 功能设计

### 2.1 斜杠命令列表

| 命令 | 描述 | 行为 |
|------|------|------|
| `/tools` | 显示所有数据查询工具 | 打印工具表格 |
| `/help` | 显示帮助信息 | 打印命令列表和用法 |
| `/stats` | 查看性能统计 | 同现有 `stats` 命令 |
| `/trace` | 查看最近工具调用记录 | 同现有 `trace` 命令 |
| `/quit` | 退出程序 | 同现有 `quit` 命令 |

### 2.2 /tools 命令输出格式

```
数据查询工具列表
─────────────────────────────────────────────────────────────────────────
工具名称                    触发关键词           查询示例
─────────────────────────────────────────────────────────────────────────
queryBudgetBillList         报价单/报价          826031111000001859报价单
queryContractsByOrderId     订单号查合同         826031111000001859合同数据
queryContractData           C前缀合同号查询      C1773208288511314合同数据
queryContractFormId         版式/form_id        C1773208288511314版式
queryContractConfig         配置表              826031111000001859配置表
querySubOrderInfo           子单/S单            826031111000001859子单
─────────────────────────────────────────────────────────────────────────
共 6 个工具
```

### 2.3 斜杠命令提示框

当用户输入 `/` 时，自动弹出提示：

```
可用命令:
  /tools   显示所有数据查询工具
  /help    显示帮助信息
  /stats   查看性能统计
  /trace   查看最近工具调用记录
  /quit    退出程序
```

---

## 3. 技术设计

### 3.1 类结构

```
trigger/console/
├── SREConsole.java              # 修改：添加 Completer，处理斜杠命令
└── command/
    ├── ConsoleCommand.java      # 新增：命令定义
    ├── CommandRegistry.java     # 新增：命令注册表
    └── ToolInfo.java            # 新增：工具信息（名称、关键词、示例）
```

### 3.2 核心类设计

**ConsoleCommand.java**
```java
public class ConsoleCommand {
    private final String name;           // 命令名（不含斜杠）
    private final String description;    // 描述
    private final String[] aliases;      // 别名
    private final Consumer<Void> action; // 执行动作
}
```

**CommandRegistry.java**
```java
public class CommandRegistry {
    private final Map<String, ConsoleCommand> commands;

    public List<String> getCommandNames();    // 获取所有命令名
    public ConsoleCommand getCommand(String name);
    public String formatHelpText();           // 格式化帮助文本
}
```

**ToolInfo.java**
```java
public class ToolInfo {
    private final String name;           // 工具名称
    private final String keywords;       // 触发关键词
    private final String example;        // 查询示例
}
```

### 3.3 SREConsole 修改点

1. **添加 Completer**：在 LineReaderBuilder 中注册自定义 Completer
2. **命令处理**：将现有的 `stats`、`trace`、`quit` 改为支持斜杠前缀
3. **新增命令**：处理 `/tools` 和 `/help` 命令
4. **提示框显示**：输入 `/` 时显示命令提示框

### 3.4 补全实现

使用 JLine 的 `Candidate` 和 `Completer` 接口：

```java
public class SlashCommandCompleter implements Completer {
    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        if (line.line().startsWith("/")) {
            // 显示命令提示框
            // 添加补全候选项
        }
    }
}
```

---

## 4. 文件变更清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `SREConsole.java` | 修改 | 添加 Completer，处理斜杠命令 |
| `command/ConsoleCommand.java` | 新增 | 命令定义类 |
| `command/CommandRegistry.java` | 新增 | 命令注册表 |
| `command/ToolInfo.java` | 新增 | 工具信息类 |

---

## 5. 测试计划

1. **单元测试**：验证 CommandRegistry 的命令注册和获取
2. **集成测试**：手动测试终端交互
   - 输入 `/` 是否弹出提示框
   - Tab 键是否正确补全
   - `/tools` 是否正确显示工具列表
   - `/help` 是否显示帮助信息
