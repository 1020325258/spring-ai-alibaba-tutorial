---
name: markstream-vue2
description: Integrate markstream-vue2 into a Vue 2.6 or 2.7 app. Use when Codex needs Vue 2-compatible setup, `@vue/composition-api` decisions, CSS wiring, optional peer setup, or scoped Markstream overrides in a Vue 2 repository that is not specifically Vue CLI / Webpack 4 constrained.
---

# Markstream Vue 2

Use this skill when the host app is Vue 2 and not specifically a Vue CLI / Webpack 4 edge case.

## Workflow

1. Confirm the repo is Vue 2.6 or 2.7.
2. Install `markstream-vue2`.
   - Add `@vue/composition-api` only when the repo is Vue 2.6 and uses Composition API patterns.
3. Import `markstream-vue2/index.css` after resets.
4. Start with `<MarkdownRender :content="markdown" />`.
5. Use scoped custom component mappings when the task needs overrides or trusted tags.
6. Validate with the smallest useful dev or build command.

## Default Decisions

- Vue 2.7 can use built-in Composition API support.
- Vue 2.6 needs `@vue/composition-api` only when the codebase actually relies on Composition API.
- If the repo is Vue CLI / Webpack 4, prefer `markstream-vue2-cli`.
- If the repo is Vue 2 plus Vite worker imports, prefer `markstream-vue2-vite`.

## Useful Doc Targets

- `docs/guide/vue2-quick-start.md`
- `docs/guide/vue2-installation.md`
- `docs/guide/vue2-components.md`
- `docs/guide/troubleshooting.md`
