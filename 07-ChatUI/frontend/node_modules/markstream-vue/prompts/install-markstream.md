# Install Markstream

Use when you want an AI coding assistant to add Markstream to an existing repository with the smallest safe dependency set.

## 中文模板

```text
请把 Markstream 接入到这个仓库。
先识别当前框架和版本，然后选择正确的包：markstream-vue、markstream-vue2、markstream-react 或 markstream-angular。
只安装满足这些能力所需的最小 peer 依赖：[填写 Monaco / Mermaid / D2 / KaTeX / Shiki]。
结合现有 CSS 技术栈处理样式顺序：[填写 Tailwind / UnoCSS / reset / design system]。
补一个最小可运行示例，并说明这里应该用 `content` 还是 `nodes`。
如果需要做组件覆盖，请默认使用带 `custom-id` 的 scoped 方式。
最后运行最小可行验证，并汇报安装了哪些 peers、CSS 放在哪里、还有哪些人工确认点。
```

## English template

```text
Add Markstream to this repository.
Detect the current framework and version first, then choose the correct package: markstream-vue, markstream-vue2, markstream-react, or markstream-angular.
Install only the smallest peer-dependency set needed for these features: [fill in Monaco / Mermaid / D2 / KaTeX / Shiki].
Handle CSS order safely with the current stack: [fill in Tailwind / UnoCSS / reset / design system].
Add one minimal working example and explain whether this repo should use `content` or `nodes`.
If renderer overrides are needed, keep them scoped with `custom-id`.
Run the smallest useful validation step and report installed peers, CSS placement, and remaining manual checks.
```
