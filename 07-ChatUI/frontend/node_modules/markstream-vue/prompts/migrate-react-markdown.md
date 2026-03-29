# Migrate From react-markdown

Use when you want an assistant to audit and migrate a React codebase from `react-markdown` to `markstream-react`.

## 中文模板

```text
请把这个 React 项目从 react-markdown 迁移到 markstream-react。
先审计所有 react-markdown 用法，并区分：直接替换、带自定义渲染器、plugin-heavy、security-heavy。
先完成最安全的 renderer swap，再把 HTML tag 风格的自定义渲染迁到 node-type + scoped `setCustomComponents`。
如果存在 rehype / remark / allow-list / skipHtml / urlTransform 之类没有 1:1 对应的行为，请明确列出，不要假装完全等价。
只有在项目确实是流式输出场景时，再建议升级到 `nodes`。
最后运行最小可行验证并列出剩余 TODO。
```

## English template

```text
Migrate this React project from react-markdown to markstream-react.
Audit every react-markdown usage and classify each one as direct swap, renderer-custom, plugin-heavy, or security-heavy.
Do the safest renderer swap first, then convert HTML-tag-style custom renderers into node-type overrides with scoped `setCustomComponents`.
If the codebase depends on rehype, remark, allow-lists, skipHtml, or urlTransform behavior that has no 1:1 match, call that out explicitly instead of claiming parity.
Only recommend moving to `nodes` if the app is actually streaming content.
Run the smallest useful validation step and list remaining TODOs.
```
