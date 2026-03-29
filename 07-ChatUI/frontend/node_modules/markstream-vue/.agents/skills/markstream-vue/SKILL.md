---
name: markstream-vue
description: Integrate markstream-vue into a Vue 3 app. Use when Codex needs to add the Vue 3 renderer, import CSS in the right order, choose between `content` and `nodes`, enable optional peers like Mermaid, KaTeX, D2, Monaco, or Shiki, or wire scoped custom components in a non-Nuxt Vue repository.
---

# Markstream Vue 3

Use this skill when the host app is plain Vue 3, typically Vite-based, and not Nuxt.

## Workflow

1. Confirm the repo is Vue 3 and not Nuxt.
2. Install `markstream-vue` plus only the optional peers required by the requested features.
3. Import `markstream-vue/index.css` after resets.
   - In Tailwind or UnoCSS projects, keep Markstream styles inside `@layer components`.
4. Start with `<MarkdownRender :content="markdown" />`.
   - Switch to `nodes` plus `final` only for streaming, SSE, or high-frequency updates.
5. Use `custom-id` plus scoped `setCustomComponents(...)` for overrides.
6. Validate with the smallest useful dev, build, or typecheck command.

## Default Decisions

- Vue 3 apps default to `content`.
- Prefer local component registration unless the repo already uses a shared plugin entry.
- If the host is actually Nuxt, leave SSR-specific setup to `markstream-nuxt`.

## Useful Doc Targets

- `docs/guide/quick-start.md`
- `docs/guide/installation.md`
- `docs/guide/usage.md`
- `docs/guide/component-overrides.md`
