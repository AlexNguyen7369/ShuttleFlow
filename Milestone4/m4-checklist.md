# Milestone 4 Checklist — Enterprise AI Functionalities

Source: `Milestone4/CMPE172_Milestone4_Enterprise_AI.pdf`
(reviewed 2026-10-28). The PDF is the grading authority; this file adds
sequencing and ShuttleFlow-specific design choices.

Starting state (2026-10-28): M3 is done. No AI code, no Spring AI dependency,
Spring Boot 3.3.4. `AppointmentService.book()` and `cancel()` are
`@Transactional`, take the `HttpSession`, and enforce RBAC through
`SessionAuth`. The M3 notification outbox (`NotificationService` →
`NotificationGateway` → `/mock-notifications`) can carry new message types.

Full build plan: `Milestone4/milestone4plan.md`.

## Sequential implementation plan

One item = one commit, with its tests, after `mvn test` passes.

1. [x] **Record M4 decisions** in `docs/decisions.md`: Gemini model name,
   Spring AI version, `ai` package, per-request tools carrying the session,
   code-enforced confirm-before-write (propose/confirm + turn number),
   reminder task, `SimpleVectorStore`, bonus = self-check.
2. [x] **Upgrade Spring Boot** 3.3.4 → latest 3.5.x (Spring AI's Boot 3 line
   needs 3.4+). No AI code yet; the full suite stays green.
3. [x] **Add Spring AI + Gemini.** Import `spring-ai-bom`, add the Google GenAI
   chat and embedding starters, and set only
   `spring.ai.google.genai.api-key=${GEMINI_API_KEY:}` plus the model. No
   `project-id` and no `location`. Add `GEMINI_API_KEY` to
   `docker-compose.yml` and a placeholder to `.env.example`. Test config sets
   a dummy key and mocks the model, so CI never calls Gemini. Never commit a
   real key.
4. [x] **Schema: reminder type.** Add `'APPOINTMENT_REMINDER'` to the
   `notifications.type` CHECK, to `NotificationRequest` and to
   `docs/schema-notes.md`. Seed one `BOOKED` appointment within 24h of boot.
5. [x] **Agent 1 read tools.** `BookingTools` (per request, holds the session):
   `findOpenSlots(from, to, sessionType)` and `listMyAppointments()` call
   `SlotService` / `AppointmentService` only. Results are sorted by start
   time and capped at 10.
6. [x] **Agent 1 write tools with confirm-before-write.**
   `proposeBooking` / `confirmBooking` and `proposeCancel` / `confirmCancel`.
   Propose stores a pending action with the turn number. Confirm runs only
   for a pending action from an earlier turn, and only through
   `appointmentService.book()` / `cancel()`. Conflicts come back as text
   ("Court is already booked.").
7. [x] **Required test: double-booking still blocked via the agent.** Two
   sessions each propose and then confirm the same slot. One booking wins, one
   gets "Court is already booked.", and the DB has one active appointment.
   Add a threaded variant that also runs on PostgreSQL when
   `SHUTTLEFLOW_PG_TEST_URL` is set. Also test that a same-turn confirm is
   refused and that a provider session is refused.
8. [x] **Agent 1 endpoint.** `ChatAgentService` (ChatClient + system prompt +
   `MessageChatMemoryAdvisor` keyed by a random UUID in the session, never the
   session id) and `POST /ai/chat {message}` → `{reply}`. 400 when the message
   is blank or too long, 401/403 from `SessionAuth`, 503 when the AI is
   unavailable. Test with a stubbed `ChatModel`.
9. [ ] **Smart behavior check (manual, real key).** Multi-step: "book the
   earliest open court this week" (find → propose → wait for "yes" →
   confirm). Taken slot: alternatives offered. Multi-turn: a follow-up
   ("actually make it 2pm") works. Tune the system prompt until all three
   pass. Save the transcripts for the report.
10. [x] **Knowledge source.** Hand-write
    `src/main/resources/knowledge/shuttleflow-policies.md` (hours, booking,
    cancellation, courts, coaches, providers, equipment, contact). Every rule
    must match the real code.
11. [x] **RAG.** `KnowledgeService` ingests the file into `SimpleVectorStore`
    on startup (behind a property), then `search()` / `answer()` ("answer only
    from context"). `POST /ai/ask {question}` → `{answer, sources}`. Add a
    `lookupPolicy` tool for Agent 1. Test with a stub embedding model.
12. [ ] **Agent 2: reminder agent.** Add the repository query
    `findDueForReminder` (BOOKED, starts within 24h, no `REMINDER:<id>` row
    yet). `ReminderAgent` has the LLM write a personalized reminder and queues
    it through `NotificationService` (key `REMINDER:<id>`, sent by the M3
    outbox). Add the `@Scheduled` job and the provider-only manual trigger
    `POST /ai/reminders/run` → `{scanned, queued, fallbacks}`. Tests: one row
    per due appointment, and a re-run adds none.
13. [ ] **Bonus: self-check (reflection).** Before a reminder is queued,
    verify that the text names the right court/coach and start time. If not,
    or if the LLM fails or returns 429, use the template message. Add
    `remindersQueued` and `reminderFallbacks` to `/metrics`. Tests: both
    fallback paths.
14. [ ] **Frontend chat panel.** A dependency-free chat box for `/ai/chat` and
    an "Ask policy" box for `/ai/ask`, for the demo.
15. [ ] **Docs.** README (AI setup, `GEMINI_API_KEY`, endpoints, demo
    steps), `docs/features/11-ai-chat-agent.md`, `12-reminder-agent.md`,
    `13-policy-qa.md`, and the CLAUDE.md package layout (`ai/`).
16. [ ] **Verify.** `mvn test`, `mvn clean package`, `docker compose up` with
    a real key: chat books after confirmation, `/ai/ask` answers grounded,
    `/ai/reminders/run` sends to `/mock-notifications`. CI green, reviewer
    agent PASS, and `git log -p | grep -i "AIza"` finds no leaked key.

## Design justification notes (for the report)

- **Tools wrap services, not SQL:** the agent gets exactly the powers of the
  REST API. Booking keeps the `FOR UPDATE` lock, the `UNIQUE (active_slot_id)`
  backstop and the 409 mapping. The LLM cannot bypass them.
- **Acts as the logged-in user:** the session is captured when the tools are
  built and is never a tool argument. RBAC comes from `SessionAuth`, the same
  as for HTTP.
- **Confirm-before-write in code:** a prompt alone can be ignored or
  jailbroken. The turn check guarantees a human message between propose and
  commit.
- **Agent 2 is proactive:** it is triggered by a timer, not a prompt. It
  produces an outbound action (an outbox row that is sent to the
  notification service), and the M3 retry and idempotency rules apply
  unchanged.
- **Self-check:** an LLM can produce a wrong time. Validating the text against
  the DB record before sending keeps customers from getting false
  information.
- **RAG:** answers cite retrieved policy sections. With no match, the
  assistant says it does not know instead of inventing a rule.

## Deliverables

- [ ] Milestone 4 report (PDF, mandatory): tools, how writes are guarded
      through the service layer, the two agents, the autonomous task, the
      knowledge source, and the bonus, with code snippets and chat
      transcripts. This becomes a section of the Final report.
- [ ] Source code: Agent 1, Agent 2, RAG, tools, the knowledge file, tests.
- [ ] Code-walkthrough video, **minimum 5 minutes**. Explain the code; do
      not just demo the app.
- [ ] Submission: `CMPE172_Milestone4_FirstName_LastName.zip` (report +
      code), plus the GitHub repo link and the video link.
- [ ] Check the General Rules in the Project Overview for submission,
      academic integrity, and video rules.

## Optional (more bonus)

- [ ] Streaming chat replies (`ChatClient.stream()` + SSE).
- [ ] MCP server exposing the read tools to external agents.
- [ ] Persistent conversation memory (JDBC chat memory in PostgreSQL).
