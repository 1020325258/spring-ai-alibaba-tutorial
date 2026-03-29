# Add A Trusted Custom Tag

Use when you want an assistant to add `thinking`-style custom tags to Markdown.

## 中文模板

```text
请为这个仓库增加一个可信的 Markdown 自定义标签：[填写 thinking / answer-box / 其他标签名]。
使用 `customHtmlTags` + scoped `setCustomComponents`，不要直接走全局覆盖。
如果标签内部还包含 Markdown，请在自定义组件里继续渲染 `node.content`，并保持流式输出友好。
如果需要嵌套标签，请保证重复和嵌套场景都能稳定工作。
只有在 `customHtmlTags` 明显不够时，才升级到 parser hooks。
最后给出最小验证方式。
```

## English template

```text
Add a trusted custom Markdown tag to this repository: [fill in thinking / answer-box / another tag].
Use `customHtmlTags` plus scoped `setCustomComponents`, not a global override.
If the tag body contains Markdown, render `node.content` inside a nested renderer and keep the implementation streaming-friendly.
Make repeated and nested tag cases stable.
Only move to parser hooks if `customHtmlTags` is clearly insufficient.
End with the smallest useful validation step.
```
