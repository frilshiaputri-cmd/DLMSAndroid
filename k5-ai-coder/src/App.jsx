import { useState } from "react";
import CodeMirror from "@uiw/react-codemirror";
import { javascript } from "@codemirror/lang-javascript";
import { oneDark } from "@codemirror/theme-one-dark";
import { Bot, Bug, Code2, Github, Send, Sparkles, Save, MessageSquare, PanelLeftClose, PanelLeftOpen } from "lucide-react";

const starter = `function greet(name) {
  return `Hello, ${name}!`;
}

console.log(greet("K5"));
`;

export default function App() {
  const [code, setCode] = useState(starter);
  const [prompt, setPrompt] = useState("");
  const [chat, setChat] = useState([{ role: "assistant", content: "Halo! Saya K5 AI Coder. Kirim pertanyaan, minta bantuan debugging, atau minta review kode." }]);
  const [mode, setMode] = useState("chat");
  const [busy, setBusy] = useState(false);
  const [sidebar, setSidebar] = useState(true);
  const [repo, setRepo] = useState("");
  const [filePath, setFilePath] = useState("README.md");
  const [branch, setBranch] = useState("main");
  const [sha, setSha] = useState("");
  const [notice, setNotice] = useState("");

  async function sendMessage() {
    const value = prompt.trim();
    if (!value || busy) return;
    const next = [...chat, { role: "user", content: value }];
    setChat(next); setPrompt(""); setBusy(true); setNotice("");
    try {
      const messages = [...next, { role: "user", content: "Current editor code:\n\n" + code }].map(m => ({ role: m.role, content: m.content }));
      const r = await fetch("/api/chat", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ messages, mode }) });
      const data = await r.json();
      if (!r.ok) throw new Error(data.error || "Permintaan AI gagal");
      setChat(v => [...v, { role: "assistant", content: data.text }]);
    } catch (e) {
      setChat(v => [...v, { role: "assistant", content: "Gagal: " + e.message + "\nPastikan server berjalan dan GEMINI_API_KEY sudah diatur." }]);
    } finally { setBusy(false); }
  }

  async function loadFile() {
    setNotice("Memuat file...");
    try {
      const q = new URLSearchParams({ repo, path: filePath, branch });
      const r = await fetch("/api/github/file?" + q);
      const data = await r.json();
      if (!r.ok) throw new Error(data.error || "Tidak dapat memuat file");
      setCode(data.content); setSha(data.sha); setNotice("File berhasil dimuat.");
    } catch (e) { setNotice(e.message); }
  }

  async function saveFile() {
    setNotice("Menyimpan ke GitHub...");
    try {
      const r = await fetch("/api/github/file", { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ repo, path: filePath, branch, content: code, sha, message: "Update via K5 AI Coder" }) });
      const data = await r.json();
      if (!r.ok) throw new Error(data.error || "Gagal menyimpan");
      setSha(data.sha || ""); setNotice("Tersimpan ke GitHub. " + (data.commit || ""));
    } catch (e) { setNotice(e.message); }
  }

  return <div className="app-shell">
    <header className="topbar">
      <button className="icon-btn" onClick={() => setSidebar(!sidebar)} aria-label="Toggle sidebar">{sidebar ? <PanelLeftClose /> : <PanelLeftOpen />}</button>
      <div className="brand-mark"><Code2 size={20}/></div><div className="brand">K5 <span>AI Coder</span><small>CODING WORKSPACE</small></div>
      <div className="top-spacer" /><span className="status-dot" /> <span className="status-text">Workspace ready</span>
    </header>
    <div className="layout">
      {sidebar && <aside className="sidebar">
        <div className="section-label">WORKSPACE</div>
        <button className="nav-item active"><MessageSquare size={17}/> AI Chat</button>
        <button className="nav-item" onClick={() => setMode("debug")}><Bug size={17}/> Debug code</button>
        <button className="nav-item" onClick={() => setMode("review")}><Sparkles size={17}/> Code review</button>
        <div className="section-label gap-top">GITHUB FILES</div>
        <label>Repository (owner/name)</label><input value={repo} onChange={e=>setRepo(e.target.value)} placeholder="username/repository"/>
        <label>Branch</label><input value={branch} onChange={e=>setBranch(e.target.value)} placeholder="main"/>
        <label>File path</label><input value={filePath} onChange={e=>setFilePath(e.target.value)} placeholder="src/App.jsx"/>
        <button className="side-action" onClick={loadFile}><Github size={16}/> Load file</button>
        <button className="side-action" onClick={saveFile}><Save size={16}/> Save file</button>
        <p className="notice">{notice || "GitHub access requires GITHUB_TOKEN on the server."}</p>
        <div className="sidebar-footer">K5-HUB · AI TOOLS<br/><span>Keep API keys private.</span></div>
      </aside>}
      <main className="main-area">
        <section className="editor-panel">
          <div className="panel-head"><div className="file-tab"><Code2 size={15}/> <span>scratch.js</span><i /></div><span className="muted">JavaScript</span><button className="mini-button" onClick={()=>setCode(starter)}>Reset</button></div>
          <div className="editor"><CodeMirror value={code} height="100%" theme={oneDark} extensions={[javascript()]} onChange={setCode} basicSetup={{ lineNumbers:true, foldGutter:true, highlightActiveLine:true }} /></div>
          <div className="editor-foot"><span>● JavaScript</span><span>{code.split("\n").length} lines · {code.length} chars</span></div>
        </section>
        <section className="chat-panel">
          <div className="chat-heading"><div className="ai-icon"><Bot size={19}/></div><div><strong>K5 Assistant</strong><small>Ask, build, debug, improve</small></div><div className="top-spacer"/><span className="model-pill">Gemini</span></div>
          <div className="mode-tabs">{[["chat","Chat"],["debug","Debug"],["review","Review"]].map(([key,label])=><button key={key} className={mode===key?"selected":""} onClick={()=>setMode(key)}>{label}</button>)}</div>
          <div className="messages">{chat.map((m,i)=><div key={i} className={"message "+m.role}><div className="message-avatar">{m.role==="assistant"?<Bot size={15}/>:<span>U</span>}</div><div className="message-body"><b>{m.role==="assistant"?"K5 Assistant":"You"}</b><p>{m.content}</p></div></div>)}{busy&&<div className="thinking"><span/> <span/> <span/> AI sedang berpikir…</div>}</div>
          <div className="composer-wrap"><div className="suggestions"><button onClick={()=>setPrompt("Jelaskan kode ini dan sarankan perbaikan.")}>Explain code</button><button onClick={()=>{setMode("debug");setPrompt("Cari bug pada kode ini dan berikan perbaikannya.");}}>Find bugs</button><button onClick={()=>{setMode("review");setPrompt("Review kode ini untuk kualitas, keamanan, dan performa.");}}>Review code</button></div><div className="composer"><textarea value={prompt} onChange={e=>setPrompt(e.target.value)} onKeyDown={e=>{if(e.key==="Enter"&&!e.shiftKey){e.preventDefault();sendMessage();}}} placeholder="Tanya K5 AI tentang kode kamu..." rows={3}/><button className="send-btn" onClick={sendMessage} disabled={busy||!prompt.trim()} aria-label="Send"><Send size={17}/></button></div><div className="composer-note">AI bisa membuat kesalahan. Periksa kode sebelum menjalankannya.</div></div>
        </section>
      </main>
    </div>
  </div>;
}
