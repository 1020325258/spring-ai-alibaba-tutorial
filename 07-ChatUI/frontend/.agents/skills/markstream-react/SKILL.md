---
name: markstream-react
description: Integrate markstream-react into a React 18+ or Next app. Use when Codex needs to add the React renderer, import CSS correctly, choose between `content` and `nodes`, keep Next client boundaries safe, convert renderer overrides, or prepare a repo for `react-markdown` migration.
---

# Markstream React

Use this skill when the host app is React or Next and the task is to wire Markstream safely.

## Workflow

1. Confirm the repo is React, Next, or another React-based host.
2. Install `markstream-react` plus only the requested optional peers.
3. Import `markstream-react/index.css` from the app shell or client entry.
4. Start with `content`.
   - Move to `nodes` plus `final` only when the UI receives streaming or high-frequency updates.
5. Respect SSR boundaries in Next.
   - Prefer `use client`, dynamic imports with `ssr: false`, or other client-only boundaries when browser-only peers are involved.
6. Use scoped Markstream overrides before custom parser work.
7. Validate with the smallest useful dev, build, or typecheck command.

## Default Decisions

- Renderer wiring first, migration cleanup second.
- If the repo already uses `react-markdown`, pair this skill with `markstream-migration`.
- Prefer the smallest client-only boundary that solves the SSR issue.

## Useful Doc Targets

- `docs/guide/react-quick-start.md`
- `docs/guide/react-installation.md`
- `docs/guide/react-markdown-migration.md`
- `docs/guide/component-overrides.md`
