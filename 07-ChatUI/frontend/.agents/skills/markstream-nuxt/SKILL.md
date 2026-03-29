---
name: markstream-nuxt
description: Integrate markstream-vue into a Nuxt 3 or Nuxt 4 app. Use when Codex needs client-only boundaries, SSR-safe setup, browser-only peer gating, worker-aware initialization, or a safe `MarkdownRender` integration inside pages, components, or Nuxt plugins.
---

# Markstream Nuxt

Use this skill when the host app is Nuxt and SSR boundaries matter.

## Workflow

1. Confirm the repo is Nuxt 3 or 4.
2. Install `markstream-vue` plus only the optional peers required by the requested features.
3. Keep browser-only peers behind client-only boundaries.
   - Prefer `<client-only>` wrappers, `.client` plugins, or guarded setup paths.
4. Import `markstream-vue/index.css` from a client-safe app shell or plugin.
5. Start with `content`, and move to `nodes` plus `final` only when the UI is streaming.
6. Validate with the smallest relevant Nuxt dev, build, or typecheck command.

## Default Decisions

- SSR safety comes before feature completeness.
- Avoid import-time access to browser globals from server code paths.
- Treat Monaco, Mermaid workers, and similar heavy peers as client-only unless the repo already has a proven SSR pattern.

## Useful Doc Targets

- `docs/nuxt-ssr.md`
- `docs/guide/installation.md`
- `docs/guide/usage.md`
- `docs/guide/troubleshooting.md`
