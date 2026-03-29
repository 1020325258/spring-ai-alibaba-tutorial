# Install Scenarios

## Package selection

| Host app | Package |
|----------|---------|
| Vue 3 / Nuxt 3 | `markstream-vue` |
| Vue 2.6 / 2.7 | `markstream-vue2` |
| React 18+ | `markstream-react` |
| Angular 20+ | `markstream-angular` |

## Peer selection

| Feature | Peers |
|---------|-------|
| Lightweight highlighted code blocks | `shiki`, `stream-markdown` |
| Monaco-powered code blocks | `stream-monaco` |
| Mermaid | `mermaid` |
| D2 | `@terrastruct/d2` |
| KaTeX math | `katex` |

## CSS checklist

- reset first
- Markstream CSS after reset
- in Tailwind or UnoCSS projects, keep Markstream CSS inside `@layer components`
- import KaTeX CSS when math is used
- when standalone node components are rendered directly, wrap them with `.markstream-vue`

## Input choice

- `content`: docs pages, static articles, low-frequency updates
- `nodes` + `final`: SSE, token streaming, AI chat, worker-preparsed content
