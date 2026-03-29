---
name: markstream-angular
description: Integrate markstream-angular into an Angular app. Use when Codex needs standalone component imports, signal-based examples, CSS wiring, custom HTML tags or customComponents setup, or optional peer integration in an Angular repository.
---

# Markstream Angular

Use this skill when the host app is Angular and the task is to adopt the Angular package cleanly.

## Workflow

1. Confirm the repo is Angular.
2. Install `markstream-angular` plus only the requested optional peers.
3. Import `markstream-angular/index.css` from the app shell.
   - Add `katex/dist/katex.min.css` when math is enabled.
4. Prefer standalone Angular integration.
   - Use `MarkstreamAngularComponent` in `imports` and keep examples signal-friendly.
5. Start with `[content]`.
   - Use `[final]`, `[codeBlockStream]`, and other streaming inputs only when the UI actually streams.
6. Use `[customHtmlTags]` and `[customComponents]` for trusted tag workflows.
7. Validate with the smallest useful Angular dev or build command.

## Default Decisions

- Standalone Angular first, NgModule-era patterns only when the repo still depends on them.
- Treat streaming flags as opt-in.
- Keep optional peers minimal and explicit.

## Useful Doc Targets

- `docs/guide/angular-quick-start.md`
- `docs/guide/angular-installation.md`
- `docs/guide/playground.md`
- `docs/guide/troubleshooting.md`
