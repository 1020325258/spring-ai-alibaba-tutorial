---
name: markstream-migration
description: Audit and migrate existing Markdown rendering to Markstream. Use when Codex needs to replace another renderer, classify direct vs custom vs plugin-heavy usage, preserve behavior during adoption, migrate custom renderers into scoped Markstream overrides, or decide when `nodes` streaming is worth adopting.
---

# Markstream Migration

Use this skill when a repo already renders Markdown and the task is to adopt Markstream safely.

Read [references/adoption-checklist.md](references/adoption-checklist.md) before changing code.

## Workflow

1. Audit the repo's current renderer usage.
   - Search for markdown renderers, plugin chains, raw HTML handling, security props, and custom renderers.
   - List every call site that will be touched.
2. Classify the migration.
   - `direct`: simple string-in renderer swap.
   - `renderer-custom`: custom renderers but limited parser work.
   - `plugin-heavy`: remark, rehype, markdown-it, or other transform-heavy pipelines.
   - `security-heavy`: allow or deny lists, URL rewriting, sanitization, or raw HTML policies.
3. Swap the renderer first.
   - Introduce the correct Markstream package and CSS.
   - Preserve user-visible behavior before adding richer Markstream-only features.
4. Migrate custom renderers.
   - Convert tag-based renderers into node-type overrides with scoped `setCustomComponents`.
   - For trusted tag-like content, prefer `customHtmlTags`.
5. Review gaps honestly.
   - Do not claim 1:1 parity where none exists.
   - Call out parser, plugin, security, or HTML behavior that still needs manual review.
6. Treat streaming as a second pass unless clearly required now.
   - Move to `nodes` only when the app receives streaming or high-frequency updates.
7. Validate and summarize.
   - Run the smallest relevant tests or build.
   - Report direct mappings, TODOs, and remaining verification work.

## Default Decisions

- Renderer swap first, streaming optimization second.
- Preserve safety over feature parity when HTML or security rules are involved.
- Prefer explicit TODOs over vague claims.
- Recommend against migration when the current stack depends heavily on transforms that Markstream does not mirror directly.

## Useful Doc Targets

- `docs/guide/react-markdown-migration.md`
- `docs/guide/react-markdown-migration-cookbook.md`
- `docs/guide/installation.md`
- `docs/guide/component-overrides.md`
- `docs/guide/advanced.md`
