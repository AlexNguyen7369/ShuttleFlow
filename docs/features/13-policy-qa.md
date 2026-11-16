# Feature 13: Policy Q&A (RAG)
Endpoint: POST /ai/ask `{ "question": "..." }` → `{ "answer": "...", "sources": ["Cancellation", ...] }`
Status: implemented (2026-11-16)

Knowledge source: `src/main/resources/knowledge/shuttleflow-policies.md`, hand-written
(hours, courts and coaches, services and prices, booking, cancellation, reminders,
providers, equipment, contact). Rules the app enforces are marked as such.

## Acceptance criteria
- At startup the file is split into one document per `##` section, embedded with Gemini (`gemini-embedding-001`) and stored in an in-memory `SimpleVectorStore` (`shuttleflow.ai.knowledge.load-on-startup`)
- If embedding fails at startup (no key), the app still starts and the next question retries the load
- A question retrieves the top 3 sections; the model answers only from them and says it does not know otherwise
- `sources` lists the titles of the sections used
- Signed-in users only: logged out → `401`; blank or more than 500 characters → `400`; model unavailable → `503`
- The same search backs the chat agent's `lookupPolicy` tool
- `PolicyFileTest` fails if a seeded service name or price no longer matches the file
