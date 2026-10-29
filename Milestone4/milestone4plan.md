# Milestone 4 Implementation Plan

Companion to `Milestone4/m4-checklist.md` (the checklist tracks what is done;
this file says how to build it). Written 2026-10-28. Nothing here is built yet.

Source: `Milestone4/CMPE172_Milestone4_Enterprise_AI.pdf`. The PDF is the
grading authority.

## Design summary

- **One AI package, same layers.** New code lives in `com.shuttleflow.ai`
  (agents, tools, knowledge) and is reached through one new
  `AiController`. Tools call the existing services. They never touch
  repositories or SQL, so every write keeps the `FOR UPDATE` lock, the
  `UNIQUE (active_slot_id)` backstop and the `SessionAuth` RBAC guards.
- **The agent is the logged-in user.** `AppointmentService.book()` and
  `cancel()` already take the `HttpSession`. The chat endpoint builds a
  per-request `BookingTools` object that holds that session. The model can
  never name a user id, so it cannot act as someone else.
- **Confirm-before-write is enforced in code, not only in the prompt.** Writes
  are split into `proposeX` and `confirmX` tools. `propose` stores a pending
  action in the session, tagged with the current chat turn number.
  `confirm` refuses unless the pending action came from an earlier turn. A
  model that proposes and confirms in the same turn is therefore rejected.
  The user must send a second message.
- **Agent 2 reuses the M3 outbox.** The reminder agent writes an
  `APPOINTMENT_REMINDER` row through `NotificationService`. The existing
  after-commit listener, poller, retry and dead-letter logic send it. The
  idempotency key `REMINDER:<appointmentId>` makes each reminder go out once.
- **RAG with the in-memory `SimpleVectorStore`.** There is no extra container.
  A hand-written policy/FAQ markdown file is split, embedded with Gemini
  embeddings and loaded at startup.
- **Bonus: self-check (reflection) on Agent 2.** Before a reminder is queued,
  code checks that the LLM text names the right court/coach and start time.
  If it fails, the agent falls back to the M3 template message, so a wrong
  time never reaches a customer. This is cheap, testable and fits the
  scenario.
- **Secrets.** The key is read only from `GEMINI_API_KEY`. Only the `api-key`
  property is set: no `project-id` and no `location` (those switch Spring AI
  to Vertex AI mode). `.env.example` gets a placeholder.

### Request flow (Agent 1)

```
POST /ai/chat {message}   (session cookie)
AiController → ChatAgentService.chat(message, session)
  ├─ sessionAuth.requireCustomer(session)
  ├─ turn = ++session["ai.turn"]; conversationId = session["ai.conversationId"] (random UUID)
  ├─ ChatClient.prompt()
  │     .system(SYSTEM_PROMPT)                       rules: propose first, confirm only on user "yes"
  │     .advisors(MessageChatMemoryAdvisor(conversationId))   multi-turn memory
  │     .tools(new BookingTools(session, turn, ...))          per-request, carries the user
  │     .user(message).call().content()
  └─ ChatResponseDto {reply}

BookingTools (@Tool methods) → SlotService / AppointmentService / KnowledgeService
  findOpenSlots(from, to, sessionType)   read
  listMyAppointments()                   read
  proposeBooking(slotId)                 stores Pending(BOOK, slotId, turn), returns summary
  confirmBooking()                       Pending from an earlier turn? → appointmentService.book(...)
  proposeCancel(appointmentId)           stores Pending(CANCEL, id, turn), returns summary
  confirmCancel()                        Pending from an earlier turn? → appointmentService.cancel(...)
  lookupPolicy(question)                 RAG search, for "what is the cancellation policy?"
```

Tool errors go back to the model as text, not as HTTP errors.
`BookingConflictException` becomes `"Court is already booked."`. The system
prompt then tells the model to call `findOpenSlots` and offer the nearest
alternatives.

### Flow (Agent 2)

```
@Scheduled(fixedDelay = shuttleflow.ai.reminder.poll-ms)   and   POST /ai/reminders/run (provider only)
ReminderAgent.run()
  ├─ appointmentRepository.findDueForReminder(now, now + 24h)
  │     BOOKED, start in window, NOT EXISTS notifications with key 'REMINDER:'||appointment_id
  ├─ for each: text = ChatClient "write a short friendly reminder for {name, court/coach, time}"
  ├─ selfCheck(text, appointment) ? text : templateMessage      (bonus: reflection)
  └─ notificationService.enqueueReminder(...)   own @Transactional per appointment → outbox → gateway → mock
returns ReminderRunDto {scanned, queued, fallbacks}
```

An LLM error (429 rate limit, timeout) on one appointment uses the template
and moves on. The job never throws out of the scheduler.

### Flow (RAG)

```
startup: KnowledgeService reads classpath:knowledge/shuttleflow-policies.md
         → TokenTextSplitter → SimpleVectorStore.add(docs)   (embeddings via Gemini)
POST /ai/ask {question}
  → vectorStore.similaritySearch(topK = 4)
  → ChatClient: "Answer only from the context; say you don't know otherwise." + context + question
  → AskResponseDto {answer, sources[]}
```

## Steps

### Step 1 — Dependencies and config

- Spring AI's Boot 3 line needs Spring Boot 3.4+, so raise
  `spring-boot-starter-parent` from 3.3.4 to the latest 3.5.x. Import
  `spring-ai-bom` at the latest 1.x release; Spring AI 2.x targets Boot 4. Run
  `mvn test` right after the bump, before any AI code.
- Add the Google GenAI chat starter (`spring-ai-starter-model-google-genai`),
  the matching embedding starter, and `spring-ai-advisors-vector-store` if
  `QuestionAnswerAdvisor` is used. Check each artifact name against the
  Spring AI reference docs for the chosen version.
- `application.properties`:

```properties
spring.ai.google.genai.api-key=${GEMINI_API_KEY:}
spring.ai.google.genai.chat.options.model=${GEMINI_MODEL:<latest Gemini flash model>}
# Do NOT set project-id or location: that switches Spring AI to Vertex AI and rejects the free key.
shuttleflow.ai.reminder.poll-ms=900000
shuttleflow.ai.reminder.window-hours=24
shuttleflow.ai.knowledge.ingest-on-startup=true
```

- Pick the model from the Gemini API docs on the day of implementation. Record
  the choice in `docs/decisions.md`.
- `docker-compose.yml` `app.environment`: `GEMINI_API_KEY: ${GEMINI_API_KEY}`.
  `.env.example`: `GEMINI_API_KEY=replace-me`.
- Tests: `src/test/resources/application.properties` sets a dummy key, turns
  ingestion off, and sets a very large reminder `poll-ms`. Tests use
  `@MockitoBean ChatModel` / `EmbeddingModel` (or a stub `ChatClient`), so CI
  never calls Gemini.

### Step 2 — Schema

- `notifications.type` CHECK: add `'APPOINTMENT_REMINDER'`. Mirror the change
  in `NotificationRequest` and `docs/schema-notes.md`.
- No new tables. Chat memory is in-memory
  (`InMemoryChatMemoryRepository`), and the vector store is in-memory.
- `seed.sql`: make sure one `BOOKED` appointment starts within 24h of boot
  (relative time, like the existing slot seeds), so the reminder demo always
  has work.

### Step 3 — Agent 1 tools (`ai/BookingTools`)

- Not a Spring bean. Build one per request with `HttpSession`, the turn
  number and the services.
- `findOpenSlots` calls `SlotService.getAvailableSlots(...)`. It sorts by
  start time and caps results at 10, so "earliest slot this week" needs just
  one tool call followed by `proposeBooking`.
- `proposeBooking(slotId)` checks that the slot is in the open list and
  stores `PendingAction`. It returns a summary like "Court 2, Sat 10:00-11:00,
  confirm?".
- `confirmBooking()` runs only if there is a pending BOOK whose turn is less
  than the current turn. It calls `appointmentService.book(new
  BookingRequest(slotId, null), session)` and then clears the pending action.
  It catches `BookingConflictException` → "Court is already booked.",
  `ConflictException`, `NotFoundException` and `ForbiddenException`, and
  returns their messages as text.
- Cancel works the same way, through `appointmentService.cancel`.
- Provider sessions: `requireCustomer` already rejects them. `/ai/chat`
  returns 403 for providers, and the provider agent is out of scope.

### Step 4 — Agent 1 service + endpoint

- `ChatAgentService` holds a `ChatClient` built once from
  `ChatClient.Builder`, the system prompt and a `MessageChatMemoryAdvisor`.
- Store the conversation id as a random UUID in the session, never the
  session id itself (M3 rule: session ids are logins).
- `AiController`: `POST /ai/chat` takes `ChatRequest {message}` (400 when
  blank or over 1000 chars) and returns `ChatResponseDto {reply}`.
- `ApiExceptionHandler`: map Spring AI / HTTP client failures to
  `503 {"error":"AI assistant is unavailable."}`. Never expose the key or the
  raw provider error.

### Step 5 — Agent 2 (`ai/ReminderAgent`)

- Add the repository query `findDueForReminder(from, to)`. It joins
  `users`, `availability_slots`, `providers` and `services`, and filters with
  `NOT EXISTS (... idempotency_key = 'REMINDER:' || a.appointment_id)` so
  appointments that already have a reminder are skipped. Use `?` parameters.
- `NotificationService.enqueueReminder(appointment, recipient, message)` uses
  key `REMINDER:<id>`. A duplicate key (the job and the manual trigger
  racing) is caught and skipped.
- `@Scheduled` method plus `POST /ai/reminders/run`
  (`sessionAuth.requireProvider`) → `ReminderRunDto`.
- Log `Reminder queued appointmentId=… llm=true|false` with ids only, and add
  metric counters `remindersQueued` and `reminderFallbacks`.

### Step 6 — RAG

- `src/main/resources/knowledge/shuttleflow-policies.md`: hand-written,
  about 10-20 short sections covering opening hours, how booking works,
  cancellation (only upcoming bookings; past ones cannot be cancelled), court
  rules, coach sessions, what a provider can do, equipment and contact. Every
  rule must match the real code.
- `KnowledgeService` ingests the file at `ApplicationReadyEvent` when the
  ingest property is on. It has `search(question)` and `answer(question)`.
- `POST /ai/ask` is open to any logged-in user and returns
  `AskResponseDto {answer, sources}`.

### Step 7 — Tests (required one first)

1. **Required:** the agent's book tool goes through the transactional path.
   Two customer sessions each get a `BookingTools` for the same slot. Each
   proposes on turn 1 and confirms on turn 2. One gets a booking, the other
   gets "Court is already booked." The DB holds exactly one active
   appointment and the slot is `BOOKED`. Add a concurrent variant (two
   threads, latch) that also runs against PostgreSQL when
   `SHUTTLEFLOW_PG_TEST_URL` is set.
2. `confirmBooking()` in the same turn as the propose → refused, no row.
3. A provider session → book tool refused (RBAC kept).
4. `/ai/chat` with a stubbed `ChatModel` returns a reply. A blank message → 400.
5. Reminder agent: a due appointment → one `APPOINTMENT_REMINDER` PENDING row.
   A second run → no new row. If the self-check fails or the LLM throws, the
   template message is used.
6. Knowledge: a stub embedding model plus ingestion of a small text returns
   the right section for a matching question.

### Step 8 — Frontend (demo aid)

- Add a small chat panel to `frontend/` (textarea, message log, calls
  `/ai/chat` with the cookie), plus an "Ask policy" box for `/ai/ask`. Keep it
  dependency-free like the rest.

### Step 9 — Docs, report, video

- README: AI setup (`GEMINI_API_KEY`), endpoints, demo script.
  `docs/decisions.md`: M4 decisions. `docs/features/11-ai-chat-agent.md`,
  `12-reminder-agent.md`, `13-policy-qa.md`. CLAUDE.md package layout: add
  `ai/`.
- Report, video and zip, as listed in the checklist deliverables.

## Risks

- **Free-tier rate limits (429).** Keep reminder batches small. A 429 falls
  back to the template (Agent 2) or returns 503 (Agent 1).
- **Boot upgrade.** 3.3 → 3.5 could break a test (for example `@MockBean`
  deprecation). Do it as its own commit, before any AI code.
- **Model hallucinating ids.** Tools validate every id against service
  results, and confirm only acts on the stored pending action, never on an
  id the model passes in.
