# SREmate - 智能 SRE 值班助手

基于 Spring AI + Qwen 的智能运维助手，为 SRE 团队提供高效的值班支持。

## 项目亮点

### 1. 极速响应 - 性能数十倍提升

传统 Agent 架构：用户提问 → LLM 调用工具 → 工具返回数据 → LLM 归纳输出

SREmate 优化架构：用户提问 → LLM 识别意图 → 工具返回数据 → **直接输出**

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   用户提问   │ ──▶ │  LLM 意图识别 │ ──▶ │  工具查询数据 │
└─────────────┘     └─────────────┘     └─────────────┘
                                                │
                                                ▼
                                        ┌─────────────┐
                                        │  直接输出结果 │  ← 绕过 LLM 二次处理
                                        └─────────────┘
```

**实现原理：** 通过 `DirectOutputHolder` 机制，数据查询类工具的结果绕过 LLM 归纳，直接流式输出给用户，节省 LLM 处理时间和 Token 消耗。

### 2. 清晰的模型定位 - 仅做意图识别

| 职责 | 执行方 | 说明 |
|------|--------|------|
| 意图识别 | LLM | 理解用户需求，选择合适的工具 |
| 数据查询 | 工具层 | 聚合多数据源，执行业务逻辑 |
| 结果输出 | 系统层 | 直接输出，无需 LLM 归纳 |

**优势：**
- 避免让 LLM 做数据处理等非擅长任务
- 工具层可组合多步操作（查库 + 调接口），一次完成
- 模型专注意图理解，响应更快、更准确

### 3. 输出字段控制 - 精准返回

通过 `responseFields` 配置，过滤接口返回字段，只输出用户关心的数据：

```yaml
responseFields:
  decorateBudgetList:
    - billType
    - billCode
    - statusDesc
```

**效果：** 原始返回 50+ 字段 → 过滤后仅 3 字段，减少干扰，提升可读性。

### 4. 完善的集成测试 - 基于 Claude Code 开发

- 测试验证**工具调用行为**，而非输出内容
- 不受业务数据变化影响，测试稳定可靠
- 提供丰富的断言方法：`assertToolCalled()`、`assertToolNotCalled()`、`assertAllToolsSuccess()`

```java
@Test
void contractCodePrefix_shouldCallQueryContractData() {
    ask("C1767173898135504的合同数据");
    assertToolCalled("queryContractData");
    assertAllToolsSuccess();
}
```

### 5. YAML 驱动的接口配置

新增接口无需修改 Java 代码，只需在 YAML 中追加配置：

```yaml
- id: sign-order-list
  name: 查询可签约子单列表
  urlTemplate: "http://service.${env}.ttb.test.ke.com/api/..."
  method: GET
  parameters:
    - name: projectOrderId
      type: string
      required: true
```

### 6. 分层精炼日志

| 层级 | 内容 | 示例 |
|------|------|------|
| AOP 层 | 入口：工具名 + 参数 | `[TOOL] queryContractData(contractCode=C..., dataType=ALL)` |
| 工具层 | 结果：耗时 + 摘要 | `[TOOL] queryContractData → 50ms, 5 rows` |

**优势：** 无重复日志，关键信息一目了然。

### 7. 多环境支持

命令行一键切换测试环境：

```
/env    # 查看当前环境，输入序号切换
```

支持环境：`nrs-escrow`（测试）、`offline-beta`（基准）

---

## 技术栈

- **框架：** Spring AI + Spring Boot 3.x
- **模型：** Qwen-Turbo（通义千问）
- **终端：** JLine（支持 Tab 补全、历史记录）
- **测试：** JUnit 5 + Spring Boot Test

## 快速开始

```bash
# 配置 API Key
export AI_DASHSCOPE_API_KEY=your_api_key_here

# 运行集成测试
./05-SREmate/scripts/run-integration-tests.sh

# 启动交互式命令行
cd 05-SREmate && mvn spring-boot:run
```

## 使用指南

```
╔══════════════════════════════════════════════════════════════╗
      智能 SRE 值班助手  ·  v2.0  ·  Powered by Qwen-Turbo
      当前环境: nrs-escrow 测试环境
╚══════════════════════════════════════════════════════════════╝

可用命令：
  /tools  显示数据查询工具
  /help   显示帮助信息
  /stats  查看性能统计
  /trace  查看最近工具调用记录
  /env    查看或切换环境
  /quit   退出程序

[nrs-escrow] 你: C1767173898135504的合同数据
```

## 项目结构

```
05-SREmate/
├── src/main/
│   ├── java/com/yycome/sremate/
│   │   ├── tools/           # @Tool 工具类
│   │   ├── config/          # Spring 配置
│   │   ├── aspect/          # AOP 切面
│   │   └── trigger/         # 触发层（Console、Agent）
│   └── resources/
│       ├── endpoints/       # HTTP 接口模板（YAML）
│       ├── prompts/         # LLM 系统提示词
│       └── skills/          # SRE 知识库
└── src/test/                # 集成测试
```

详细开发规范见 [CLAUDE.md](./CLAUDE.md)

## 许可证

MIT License
