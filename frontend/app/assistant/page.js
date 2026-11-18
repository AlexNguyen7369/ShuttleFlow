"use client";

import { useEffect, useRef, useState } from "react";
import { post } from "../../lib/api";
import { useAuth } from "../auth";

export default function Assistant() {
  const { user } = useAuth();
  const [lines, setLines] = useState([]);
  const [message, setMessage] = useState("");
  const [sending, setSending] = useState(false);
  const [question, setQuestion] = useState("");
  const [answer, setAnswer] = useState(null);
  const log = useRef(null);

  useEffect(() => { log.current?.scrollTo(0, log.current.scrollHeight); }, [lines]);

  async function sendChat(event) {
    event.preventDefault();
    const text = message.trim();
    if (!text) return;
    if (user?.role !== "CUSTOMER") {
      setLines((l) => [...l, { who: "assistant", text: "Please sign in with a customer account to book by chat." }]);
      return;
    }
    setMessage("");
    setLines((l) => [...l, { who: "user", text }]);
    setSending(true);
    try {
      const { reply } = await post("/ai/chat", { message: text });
      setLines((l) => [...l, { who: "assistant", text: reply }]);
    } catch (err) {
      setLines((l) => [...l, { who: "assistant", text: err.message }]);
    } finally {
      setSending(false);
    }
  }

  async function ask(event) {
    event.preventDefault();
    setAnswer({ text: "Thinking…", sources: [] });
    try {
      const result = await post("/ai/ask", { question });
      setAnswer({ text: result.answer, sources: result.sources });
    } catch (err) {
      setAnswer({ text: err.status === 401 ? "Please sign in to ask a question." : err.message, sources: [] });
    }
  }

  return (
    <section className="view">
      <div className="section-head">
        <div><p className="eyebrow">AI assistant</p><h2>Book by chat or ask a question</h2></div>
      </div>
      <div className="provider-grid">
        <div className="card">
          <h3>Booking assistant</h3>
          <p className="muted small">Customers only. It always asks you to confirm before it books or cancels.</p>
          <div ref={log} className="chat-log" aria-live="polite">
            {lines.map((line, i) => (
              <p key={i} className={`chat-${line.who}`}><b>{line.who === "user" ? "You" : "Assistant"}:</b> {line.text}</p>
            ))}
            {sending && <p className="muted">Assistant is typing…</p>}
          </div>
          <form className="chat-form" onSubmit={sendChat}>
            <input id="chat-input" maxLength={1000} required aria-label="Message" value={message}
              placeholder="e.g. Book the earliest open court this week" onChange={(e) => setMessage(e.target.value)} />
            <button className="button" disabled={sending}>Send</button>
          </form>
        </div>
        <div className="card">
          <h3>Ask about our policies</h3>
          <form onSubmit={ask}>
            <input id="ask-input" maxLength={500} required aria-label="Question" value={question}
              placeholder="e.g. Can I cancel a booking?" onChange={(e) => setQuestion(e.target.value)} />
            <button className="button">Ask</button>
          </form>
          {answer && <p className="answer">{answer.text}</p>}
          {answer?.sources?.length > 0 && <p className="muted small">Sources: {answer.sources.join(", ")}</p>}
        </div>
      </div>
    </section>
  );
}
