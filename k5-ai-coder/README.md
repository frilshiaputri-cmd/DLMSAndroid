# K5 AI Coder

Responsive browser-based coding workspace with AI chat, code editor, code review/debug prompts, and optional GitHub file access.

## Requirements
- Node.js 20+
- Gemini API key (free quota/availability depends on Google account and current limits)
- Optional GitHub personal access token for private repository access and commits

## Run locally
1. Copy `.env.example` to `.env`.
2. Set `GEMINI_API_KEY`; optionally set `GITHUB_TOKEN`.
3. Run `npm install`
4. Run `npm run dev`
5. Open the Vite URL shown in the terminal.

The Express server keeps API credentials on the server; do not put keys in frontend code or commit `.env`.

## Build
`npm run build` then `npm start`.

This is an initial scaffold; test API credentials, provider quotas, and GitHub permissions before relying on it.
