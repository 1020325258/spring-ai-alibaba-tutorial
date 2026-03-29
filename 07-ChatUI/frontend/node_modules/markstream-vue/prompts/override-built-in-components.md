# Override Built-in Components

Use when you want an assistant to replace built-in Markstream renderers such as `image`, `link`, `code_block`, or `mermaid`.

## 中文模板

```text
请在这个仓库里覆盖 Markstream 的内置组件。
目标节点是：[填写 image / link / code_block / mermaid / d2 / infographic / inline_code / 其他]。
默认使用 scoped `setCustomComponents(customId, mapping)`，不要直接做全局覆盖，除非有明确理由。
如果只是 Mermaid / D2 / infographic 需要修改，请只覆盖对应 key，不要影响所有代码块。
保留必要 props（如 `node`、`loading`、`customId`、`isDark`），并说明这个 override 会影响哪些页面或组件。
最后运行最小可行验证。
```

## English template

```text
Override built-in Markstream components in this repository.
The target node types are: [fill in image / link / code_block / mermaid / d2 / infographic / inline_code / other].
Prefer scoped `setCustomComponents(customId, mapping)` instead of a global override unless there is a clear reason not to.
If only Mermaid, D2, or infographic behavior changes, override those specific keys instead of all code blocks.
Preserve required props such as `node`, `loading`, `customId`, and `isDark`, and explain which pages or surfaces the override affects.
Run the smallest useful validation step at the end.
```
