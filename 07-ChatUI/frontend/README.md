# ChatUI Frontend

Vue 3 + Vite frontend for the ChatUI demo. Uses [markstream-vue](https://github.com/...) for streaming Markdown rendering.

## Dev

```bash
npm install
npm run dev       # http://localhost:5173  (proxies /api → localhost:8090)
```

## Build

```bash
npm run build     # outputs to ../src/main/resources/static/
```

## Backend contract

```
POST /api/chat/stream
Content-Type: application/json
{ "message": "用户输入" }

Response: text/event-stream
data: chunk1
data: chunk2
data: [DONE]
```

## Test

```bash
npm test
```
