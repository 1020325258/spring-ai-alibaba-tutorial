---
name: markstream-install
description: Install and wire markstream-vue, markstream-react, markstream-vue2, or markstream-angular into an existing repository. Use when Codex needs to choose the right package, install the smallest peer-dependency set, fix CSS/reset order, decide between `content` and `nodes`, or add a minimal working renderer example.
---

# Markstream Install

Use this skill when the task is "add markstream to an app" or "fix a broken markstream install".

Read [references/scenarios.md](references/scenarios.md) before making dependency choices.

## Workflow

1. Detect the target framework and CSS stack.
   - Check `package.json`, app entry files, Tailwind or UnoCSS config, and whether the repo is SSR or streaming-focused.
   - Choose the package that matches the host app: `markstream-vue`, `markstream-vue2`, `markstream-react`, or `markstream-angular`.
2. Install the smallest peer set that matches the requested features.
   - Add peers only for features the user actually needs: Monaco, Mermaid, D2, KaTeX, or Shiki.
   - Do not install every optional peer by default.
3. Fix CSS order.
   - Put reset styles before Markstream styles.
   - In Tailwind or UnoCSS projects, place `markstream-*/index.css` inside `@layer components`.
   - Import `katex/dist/katex.min.css` when math is enabled.
4. Add the smallest working render example.
   - Use `content` for static or low-frequency rendering.
   - Use `nodes` plus `final` when the app receives streaming updates.
5. Keep customization scoped.
   - If the task requires overrides, prefer `customId` / `custom-id` plus scoped `setCustomComponents(...)`.
6. Validate.
   - Run the smallest relevant build, typecheck, test, or docs build command.
   - Report which peers were installed, where CSS lives, and whether the repo should later adopt `nodes`.

## Default Decisions

- Prefer the minimal peer set over "install everything".
- Prefer `content` unless the app is clearly SSE, chat, token-streaming, or worker-preparsed.
- Treat CSS order as a first-class part of installation, not a later cleanup.
- When the request includes SSR, explicitly gate browser-only peers behind client-only boundaries.

## Useful Doc Targets

- `docs/guide/installation.md`
- `docs/guide/usage.md`
- `docs/guide/troubleshooting.md`
- `docs/guide/component-overrides.md`
