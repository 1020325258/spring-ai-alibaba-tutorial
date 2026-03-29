---
name: markstream-vue2-vite
description: Integrate markstream-vue2 into a Vue 2 plus Vite app. Use when Codex needs Vite-friendly worker imports, `?worker` or `?worker&inline` setup for Mermaid or KaTeX, modern CSS ordering, or Vue 2 compatibility in a Vite-based repository.
---

# Markstream Vue 2 Vite

Use this skill when the host app is Vue 2 and the bundler is Vite.

## Workflow

1. Confirm the repo is Vue 2 with Vite-based tooling.
2. Install `markstream-vue2` plus only the requested optional peers.
3. Import `markstream-vue2/index.css` after resets.
4. Use Vite worker imports when the repo needs bundled workers.
   - `markstream-vue2/workers/... ?worker` or `?worker&inline` patterns are allowed here.
5. Keep Composition API decisions explicit for Vue 2.6 repos.
6. Validate with the smallest useful Vite dev or build command.

## Default Decisions

- Prefer bundled workers over CDN workers in Vite-based Vue 2 repos.
- Keep UnoCSS or Tailwind resets before Markstream CSS.
- Use the generic `markstream-vue2` skill only when the bundler-specific choice does not matter.

## Useful Doc Targets

- `docs/guide/vue2-quick-start.md`
- `docs/guide/vue2-installation.md`
- `docs/guide/tailwind.md`
- `docs/guide/troubleshooting.md`
