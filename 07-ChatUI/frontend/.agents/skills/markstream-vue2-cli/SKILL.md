---
name: markstream-vue2-cli
description: Integrate markstream-vue2 into a Vue 2 Vue CLI or Webpack 4 app. Use when Codex needs Webpack 4-friendly setup, CDN worker fallbacks for Mermaid or KaTeX, `dist/index.css` imports, Vue 2 composition-api shims, or safer code block defaults that avoid fragile Monaco worker setups.
---

# Markstream Vue 2 CLI

Use this skill when the host app is Vue 2 on Vue CLI or another Webpack 4-style stack.

## Workflow

1. Confirm the repo uses Vue 2 plus Vue CLI or Webpack 4-era tooling.
2. Install `markstream-vue2` and only the peers required for the requested features.
3. Import `markstream-vue2/dist/index.css` in the app shell.
4. Avoid Vite-style `?worker` imports.
   - Use `createKaTeXWorkerFromCDN(...)` and `createMermaidWorkerFromCDN(...)` when workers are needed.
5. Prefer stable code block defaults over brittle Monaco wiring.
   - `MarkdownCodeBlockNode` plus `stream-markdown` is safer than Monaco in Webpack 4-era repos.
6. Validate with the smallest useful local dev or build command.

## Default Decisions

- Treat Monaco and worker-heavy setups as opt-in and fragile on Webpack 4.
- Prefer CDN workers over bundler workers for Mermaid and KaTeX.
- Keep the Vue 2 composition-api shim explicit when the repo is on Vue 2.6.

## Useful Doc Targets

- `docs/guide/vue2-quick-start.md`
- `docs/guide/vue2-installation.md`
- `docs/guide/troubleshooting.md`
