import express from "express";
import cors from "cors";
import dotenv from "dotenv";
import path from "node:path";
import { fileURLToPath } from "node:url";

dotenv.config();
const app = express();
const port = Number(process.env.PORT || 8787);
app.use(cors());
app.use(express.json({ limit: "1mb" }));

app.get("/api/health", (_req, res) => res.json({ ok: true, service: "K5 AI Coder" }));

app.post("/api/chat", async (req, res) => {
  const key = process.env.GEMINI_API_KEY;
  if (!key) return res.status(503).json({ error: "GEMINI_API_KEY belum diatur di file .env." });
  const { messages = [], mode = "chat" } = req.body || {};
  const system = "You are K5 AI Coder, a careful coding assistant. Help explain, write, review, and debug code. Ask for missing context when needed. Do not claim code was tested unless it was actually run. Mode: " + mode;
  try {
    const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${process.env.GEMINI_MODEL || "gemini-2.5-flash"}:generateContent?key=${encodeURIComponent(key)}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: system }] },
        contents: messages.map(m => ({ role: m.role === "assistant" ? "model" : "user", parts: [{ text: String(m.content || "") }] })),
        generationConfig: { temperature: 0.3 }
      })
    });
    const data = await response.json();
    if (!response.ok) return res.status(response.status).json({ error: data.error?.message || "AI provider request failed." });
    res.json({ text: data.candidates?.[0]?.content?.parts?.map(p => p.text || "").join("\n") || "Tidak ada jawaban teks." });
  } catch (e) {
    res.status(500).json({ error: e.message || "Server error." });
  }
});

app.get("/api/github/repos", async (_req, res) => {
  if (!process.env.GITHUB_TOKEN) return res.status(503).json({ error: "GITHUB_TOKEN belum diatur." });
  try {
    const r = await fetch("https://api.github.com/user/repos?sort=updated&per_page=50", {
      headers: { Authorization: `Bearer ${process.env.GITHUB_TOKEN}`, Accept: "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28" }
    });
    const data = await r.json();
    if (!r.ok) return res.status(r.status).json({ error: data.message || "GitHub API error." });
    res.json(data.map(x => ({ full_name: x.full_name, private: x.private, default_branch: x.default_branch, html_url: x.html_url })));
  } catch (e) { res.status(500).json({ error: e.message }); }
});

app.get("/api/github/file", async (req, res) => {
  if (!process.env.GITHUB_TOKEN) return res.status(503).json({ error: "GITHUB_TOKEN belum diatur." });
  const { repo, path: filePath, branch = "main" } = req.query;
  if (!repo || !filePath) return res.status(400).json({ error: "Parameter repo dan path diperlukan." });
  try {
    const url = `https://api.github.com/repos/${repo}/contents/${String(filePath).split("/").map(encodeURIComponent).join("/")}?ref=${encodeURIComponent(branch)}`;
    const r = await fetch(url, { headers: { Authorization: `Bearer ${process.env.GITHUB_TOKEN}`, Accept: "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28" } });
    const data = await r.json();
    if (!r.ok) return res.status(r.status).json({ error: data.message || "GitHub API error." });
    if (data.encoding !== "base64" || !data.content) return res.status(415).json({ error: "File bukan teks yang didukung." });
    res.json({ path: data.path, sha: data.sha, content: Buffer.from(data.content, "base64").toString("utf8") });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

app.put("/api/github/file", async (req, res) => {
  if (!process.env.GITHUB_TOKEN) return res.status(503).json({ error: "GITHUB_TOKEN belum diatur." });
  const { repo, path: filePath, branch = "main", content, sha, message = "Update via K5 AI Coder" } = req.body || {};
  if (!repo || !filePath || typeof content !== "string") return res.status(400).json({ error: "repo, path, dan content diperlukan." });
  try {
    const url = `https://api.github.com/repos/${repo}/contents/${String(filePath).split("/").map(encodeURIComponent).join("/")}`;
    const r = await fetch(url, {
      method: "PUT",
      headers: { Authorization: `Bearer ${process.env.GITHUB_TOKEN}`, Accept: "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28", "Content-Type": "application/json" },
      body: JSON.stringify({ message, content: Buffer.from(content, "utf8").toString("base64"), branch, ...(sha ? { sha } : {}) })
    });
    const data = await r.json();
    if (!r.ok) return res.status(r.status).json({ error: data.message || "GitHub write failed." });
    res.json({ ok: true, commit: data.commit?.html_url, sha: data.content?.sha });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const dist = path.resolve(__dirname, "../dist");
app.use(express.static(dist));
app.get("*", (req, res, next) => {
  if (req.path.startsWith("/api/")) return next();
  res.sendFile(path.join(dist, "index.html"), err => { if (err) next(); });
});
app.listen(port, () => console.log(`K5 AI Coder API listening on :${port}`));
