# ShuttleFlow (Head2Head)

[![CI](https://github.com/AlexNguyen7369/ShuttleFlow/actions/workflows/ci.yml/badge.svg)](https://github.com/AlexNguyen7369/ShuttleFlow/actions/workflows/ci.yml)

**ShuttleFlow** is a badminton platform that starts as a court and coach booking system and grows into full head-to-head match tracking — live scores, shot data, and player stats — with an AI layer on top.

## Vision

Badminton players and coaches currently juggle group chats and spreadsheets to book courts and lessons, and have no structured record of games played against each other. ShuttleFlow closes both gaps in stages:

1. **Book** — players browse and reserve court time or coaching sessions; providers (courts, coaches) publish availability.
2. **Track** — bookings extend into logged matches: scores, sets, and head-to-head records between players.
3. **Analyze** — shot-level data and computer vision turn logged matches into real stats (shot placement, rally length, win patterns).
4. **Assist** — a conversational agent books slots for you ("book me singles Saturday afternoon"), an autonomous agent fills cancelled slots from a waitlist, and a grounded Q&A assistant answers rules/facility questions from a small knowledge base.

This repo implements stage 1 plus the Milestone 4 AI layer (stage 4: a booking chat agent, a scheduled reminder agent and policy Q&A), built as the required project for CMPE 172 (Enterprise Software Platforms).

## Roadmap

| Milestone | Scope |
|---|---|
| **M1** | Read-only skeleton: layered Spring Boot app, schema, seed data, `GET /` and `GET /slots` |
| **M2** | Full booking flow: login, browse/filter/paginate slots, book/cancel appointments, provider slot management |
| **M3** | Mock booking-confirmation service, logging, health check, one operational metric |
| **M4** | Conversational + autonomous booking agents, RAG Q&A over facility/rules knowledge base |
| **Post-course** | Match tracking (`games`, `scores`, `shots` tables), head-to-head stats, ML/CV shot analysis |

## Tech stack

- **Backend:** Java 17, Spring Boot 3.5 (`spring-boot-starter-web` + `spring-boot-starter-jdbc`) — no ORM, raw SQL via `JdbcTemplate`
- **AI:** Spring AI 1.1.8 with the Gemini API free tier (`gemini-3.5-flash-lite` chat, `gemini-embedding-001` embeddings) and an in-memory `SimpleVectorStore`
- **Database:** PostgreSQL at runtime; H2 is test-only infrastructure with an in-memory test profile
- **Frontend:** dependency-free static HTML/CSS/JavaScript under `frontend/`
- **Architecture:** Front Controller (Spring's `DispatcherServlet`) routing to `@RestController` → `@Service` → `@Repository` → DTO layers

## Prerequisites

- JDK 17+
- Maven 3.9+
- PostgreSQL 14+ running locally (runtime database; tests use in-memory H2 and need no server)
- Optional: a free Gemini API key from https://aistudio.google.com/apikey for the AI features (tests never need it)

## Run

### With Docker (no local Java, Maven or PostgreSQL needed)

```bash
cp .env.example .env          # choose DB_USERNAME / DB_PASSWORD, add GEMINI_API_KEY; .env is never committed
docker compose up --build     # PostgreSQL first (healthchecked), then the app on :8080
curl localhost:8080/health    # {"status":"UP","database":"UP"}
```

The multi-stage `Dockerfile` builds the jar with Maven + JDK and ships only a
slim Java 17 runtime. Compose starts the app only after `pg_isready` passes,
and the app reaches the database at host `db`.

### Locally

One-time local database setup:

```bash
createuser shuttleflow
createdb -O shuttleflow shuttleflow
```

Then start the API and the browser client in two terminals:

```bash
export GEMINI_API_KEY=...                             # optional: enables the AI features
mvn spring-boot:run                                   # API on http://localhost:8080
python3 -m http.server 5173 --directory frontend      # UI on http://localhost:5173
```

Override the connection with `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`,
`DB_PASSWORD` (or `SPRING_DATASOURCE_URL`), and the allowed UI origins with
`SHUTTLEFLOW_CORS_ORIGINS`. `schema.sql` and `seed.sql` reload on every startup
(`spring.sql.init.mode=always`), so local data resets on restart. Seed slots are
dated relative to the boot day, so there is always bookable future data.

The seeded local test accounts use BCrypt hashes:

| Account | Role | Test password |
|---|---|---|
| `court.manager@shuttleflow.com` | PROVIDER | `court-manager-test` |
| `coach.kim@shuttleflow.com` | PROVIDER | `coach-kim-test` |
| `alex@shuttleflow.com` | CUSTOMER | `alex-test` |
| `jamie@shuttleflow.com` | CUSTOMER | `jamie-test` |

These passwords are for local/test fixtures only, not deployment credentials.

## Endpoints

| Method & path | Who | Success | Error statuses |
|---|---|---|---|
| `GET /` | anyone | `200` app name + open-slot count | — |
| `GET /slots` | anyone | `200` page of open future slots | `400` |
| `POST /auth/login` | anyone | `200` identity, session created | `400`, `401` |
| `POST /auth/provider/login` | provider | `200` identity + `providerId` | `400`, `401`, `403` |
| `GET /auth/session` | signed in | `200` current identity | `401` |
| `POST /auth/logout` | anyone | `204` session invalidated | — |
| `POST /appointments` | customer | `201` `BOOKED` appointment | `400`, `401`, `403`, `404`, `409` |
| `GET /appointments?view=upcoming\|history` | customer | `200` caller's appointments | `400`, `401`, `403` |
| `DELETE /appointments/{id}` | owning customer | `204` cancelled, slot reopened | `401`, `403`, `404`, `409` |
| `GET /provider/services` | provider | `200` provider's own services | `401`, `403` |
| `POST /provider/slots` | provider | `201` new open slot | `400`, `401`, `403`, `404`, `409` |
| `DELETE /provider/slots/{id}` | owning provider | `204` slot removed | `401`, `403`, `404`, `409` |
| `GET /provider/appointments` | provider | `200` bookings on own slots + customer name/email | `401`, `403` |
| `GET /health` | anyone | `200` `{"status":"UP","database":"UP"}` | `503` DOWN when the DB is unreachable |
| `GET /metrics` | anyone | `200` booking/notification counters + pending outbox count | — |
| `POST /mock-notifications` | notification boundary | `202` new key, `200` repeat key | `400` |
| `POST /ai/chat` `{message}` | customer | `200` `{reply}` from the booking agent | `400`, `401`, `403`, `503` |
| `POST /ai/ask` `{question}` | signed in | `200` `{answer, sources}` grounded in the policy file | `400`, `401`, `503` |
| `POST /ai/reminders/run` | provider | `200` `{scanned, queued, fallbacks}` | `401`, `403` |

Every error body is `{"error": "..."}` with no stack trace, SQL, or credential
detail (`ApiExceptionHandler`). Unknown routes keep their real `404`/`405`/`415`.

```bash
curl http://localhost:8080/
# {"appName":"ShuttleFlow","openSlotCount":7}

curl -c jar.txt -H 'Content-Type: application/json' \
     -d '{"email":"alex@shuttleflow.com","password":"alex-test"}' http://localhost:8080/auth/login
curl -b jar.txt -H 'Content-Type: application/json' -d '{"slotId":1}' http://localhost:8080/appointments
# 201 {"appointmentId":1,"slotId":1,...,"status":"BOOKED"}
```

### `GET /slots`

Open slots that have not started yet, ordered by `startTime` then `slotId`, 10
per page via SQL `LIMIT ? OFFSET ?`.

| Param | Default | Meaning |
|---|---|---|
| `page` | `1` | 1-based page number |
| `providerId` | — | Only slots for this provider (coach openings) |
| `sessionType` | — | `OPEN_PLAY` (court providers) or `COACHING` (coach providers) |
| `serviceId` | — | Only slots for this service |
| `date` | — | `yyyy-MM-dd`; only slots starting that day (today or later) |

Filters combine with each other and with `page`. A page past the last returns
`200` with `[]`. A bad `page`, non-positive ID, unknown `sessionType`, or a
malformed/past `date` returns `400`.

### Authentication and RBAC

Login checks the password against the stored BCrypt hash; unknown email and
wrong password return the identical `401 "Invalid email or password."` so
accounts cannot be enumerated. A successful login invalidates any previous
session and issues a new one (prevents session fixation), then stores an
immutable `UserSession` (user ID, role, provider ID; never a password) in it.
`SessionAuth.requireCustomer` / `requireProvider` turn a missing session into
`401` and the wrong role into `403`; ownership checks (`appointment.user_id`,
`slot.provider_id`) add `403` for other people's records.

### Booking transaction and concurrency

`AppointmentService.book` is one `@Transactional(isolation = READ_COMMITTED)` unit:

1. `SELECT ... FOR UPDATE` locks the slot row (pessimistic lock).
2. Validate it exists (`404`), has not started (`409`) and is `OPEN` (`409`).
3. Insert the appointment with `active_slot_id = slot_id`.
4. Flip the slot `OPEN → BOOKED` (compare-and-set update).
5. Commit — or roll back everything on any failure.

A competing booking for the same slot blocks on the row lock, then reads the
committed `BOOKED` status and gets `409 {"error":"Court is already booked."}`.
The `UNIQUE (active_slot_id)` key on `appointments` is the database backstop:
if any path ever skipped the lock, the second insert violates it and the
service translates that to the same `409`. Cancelling sets the appointment to
`CANCELLED` and `active_slot_id` to `NULL` (both NULL-distinct in PostgreSQL and
H2), so history is kept and the slot can be booked again. There is no retry:
the losing request's outcome is final because the slot is taken.

### Notifications, health and metrics (Milestone 3)

- **Commit-then-notify through an outbox.** Booking and cancelling insert a
  `notifications` row (`PENDING`, key `BOOKED:<id>` / `CANCELLED:<id>`) in the
  same transaction. After commit, a background listener sends one
  `NotificationRequest` DTO to the notification service
  (`shuttleflow.notification.base-url`, env `NOTIFICATION_BASE_URL`).
- **Fault handling.** Timeouts are connect 1s and read 2s. The gateway makes
  up to 3 attempts with backoff on timeout, refused connection or 5xx, and
  never retries a 4xx. A `@Scheduled` poller resends `PENDING` rows older than
  60s. A 4xx, or 5 failed rounds, moves the row to `FAILED`. The booking
  response never depends on the notification.
- **Observability.**
  - `GET /health` runs `SELECT 1`.
  - `GET /metrics` exposes the counters.
  - Logs carry ids only, never passwords, session IDs or message bodies.

See `docs/features/09-booking-notifications.md` (with the failure scenario) and
`Milestone3/distribution-diagram.svg`.

### AI features (Milestone 4)

- **Booking chat agent** (`POST /ai/chat`). Gemini calls tools that wrap the
  existing services: `findOpenSlots`, `listMyAppointments`, `proposeBooking` /
  `confirmBooking`, `proposeCancel` / `confirmCancel` and `lookupPolicy`. The
  tools are built per request with the caller's session, so the agent acts as
  the logged-in customer and keeps every RBAC, `FOR UPDATE` and
  `UNIQUE (active_slot_id)` rule. Writes are confirm-before-write in code: a
  confirm only runs for a proposal made just before the user's latest message,
  and an older proposal expires. Conversation memory
  is keyed by a random id in the session.
- **Reminder agent** (autonomous). Every 15 minutes it finds booked appointments
  starting within 24 hours without a reminder, has Gemini write one, and queues
  it in the M3 outbox (`REMINDER:<id>`), which sends it to the notification
  service. A self-check (bonus) rejects text that names the wrong court/coach or
  time and uses a template instead, as does any LLM error or rate limit.
- **Policy Q&A** (`POST /ai/ask`). `src/main/resources/knowledge/shuttleflow-policies.md`
  is split by section, embedded at startup into `SimpleVectorStore`, and answers
  use only the top 3 sections, which are returned as `sources`.
- **No key?** The app still starts; the AI endpoints return `503` and reminders
  use the template.

Demo: sign in as `alex@shuttleflow.com`, open **Assistant**, send "Book the
earliest open court this week", then "yes" to confirm; ask "Can I cancel a
booking?" in the policy box. Sign in as the court manager and press **Run
reminders now**; `GET /metrics` shows `remindersQueued`. Details:
`docs/features/11-ai-chat-agent.md`, `12-reminder-agent.md`, `13-policy-qa.md`.

### Browser client

`frontend/` is a dependency-free HTML/CSS/JS client: home, login (customer or
provider), browse/filter/paginate, a booking confirmation dialog, my bookings
(upcoming/history) with cancellation, the provider workspace (service
picker, create/remove availability, booked appointments, run reminders), and
the AI assistant (booking chat and policy questions). It calls the API
with `credentials: "include"`; `CorsConfig` allows its origin with cookies.

## Testing

```bash
mvn test                       # 132 tests on in-memory H2 (no server or Gemini key needed)
mvn clean package              # build target/shuttleflow-0.1.0.jar

# Also run the 8-thread same-slot race 10 times against real PostgreSQL:
createdb -O shuttleflow shuttleflow_test
SHUTTLEFLOW_PG_TEST_URL=jdbc:postgresql://localhost:5432/shuttleflow_test mvn test
```

| Test class | Covers |
|---|---|
| `BookingServiceUnitTest` | Service rules with mocked repositories: roles, ownership, past/booked slots, constraint-violation → `409` |
| `M2ServiceRulesTest` | Service rules against the real schema |
| `SlotControllerBrowseTest` | Filters, ordering, `LIMIT/OFFSET` paging, future-only, `400`s |
| `AuthControllerIntegrationTest` | Login equivalence, sessions, fixation, logout, CORS, framework errors |
| `M2EndpointIntegrationTest` | Every booking/provider route: `201/204/400/401/403/404/409` and DB effects |
| `SessionAuthTest`, `SeedPasswordHashTest` | Role guards; seed hashes are BCrypt |
| `ConcurrentBookingTest` | Two customers released by a latch on one slot: one `201`, one conflict |
| `PostgresConcurrentBookingTest` | 8 threads × 10 rounds on PostgreSQL: exactly one active booking each round |
| `NotificationIntegrationTest` | Book/cancel succeed with the notification service down; 409 writes no outbox row; poller retry and dead-letter |
| `NotificationGatewayTest` | Real HTTP stub: success, 4xx not retried, 503-then-success, give-up, hung-service timeout, refused port |
| `NotificationServiceTest` | Outbox key/message, status transitions, poller dispatch |
| `MockNotificationControllerTest` | `202` new key, `200` repeat stored once, `400` blank fields |
| `HealthControllerTest`, `MetricsControllerTest` | `/health` UP `200` / DOWN `503`; booking and conflict counters |
| `BookingToolsReadTest`, `BookingToolsWriteTest` | Agent tools: sorting/capping, errors as text, same-turn confirm refused, one confirm per proposal |
| `AgentDoubleBookingTest` | Required M4 test: two sessions propose and confirm one slot through the agent tools, one books, one gets "Court is already booked."; 8-thread race; provider refused (also on PostgreSQL) |
| `AiChatControllerTest` | `/ai/chat` with a stubbed model: tools offered, multi-turn memory, `400/401/403/503` |
| `KnowledgeServiceTest`, `AiAskControllerTest`, `PolicyFileTest` | RAG retrieval and grounding with offline embeddings; policy file matches the seeded prices |
| `ReminderAgentTest`, `ReminderSelfCheckTest` | One reminder per due appointment, none on re-run, template fallbacks, self-check, provider-only trigger, metrics |
| `GeminiConfigTest`, `ReminderSchemaTest` | AI beans start with a dummy key and no Vertex settings; reminder type and seeded due appointment |

GitHub Actions (`.github/workflows/ci.yml`) runs `mvn -B verify` on every push
and pull request, with a `postgres:16` service so the race test runs too.

## Project structure

```
src/main/java/com/shuttleflow/
  controller/   HTTP boundary (Auth, Slot, Appointment, Provider, Home, Health, Metrics, MockNotification) + ApiExceptionHandler
  service/      validation, RBAC/ownership, booking rules, transactions, outbox + NotificationGateway, health, metrics
  repository/   parameterized JdbcTemplate SQL constants + RowMappers (User, Slot, Appointment, Provider, Notification, Health)
  dto/          immutable request/response objects
  auth/         UserSession (identity in the HTTP session) + SessionAuth (401/403 guards)
  config/       PasswordConfig (BCrypt), CorsConfig (frontend origin + cookies)
  ai/           BookingTools (agent tools), ChatAgentService, ReminderAgent, KnowledgeService (RAG)
src/main/resources/
  application.properties   PostgreSQL datasource, sql.init, CORS origins, notification timeouts/retry/poller
  schema.sql               one portable schema for PostgreSQL and H2, incl. the double-booking UNIQUE key and notifications outbox
  seed.sql                 4 users, 2 providers, 3 services, 7 open future slots + 1 booked appointment 6h after boot
  knowledge/               shuttleflow-policies.md, the RAG knowledge source
src/test/                  unit, integration, and concurrency tests (H2 profile; optional PostgreSQL race)
frontend/                  index.html + src/app.js + src/styles.css
docs/                      decisions, schema notes, feature specs 01-10
Milestone1/                M1 walkthrough script (gitignored)
Milestone2/                assignment PDF, plan, checklist, evidence report, summary, walkthrough scripts
Milestone3/                assignment PDF, checklist, implementation plan, distribution diagram
Dockerfile, docker-compose.yml, .env.example   container build and one-command run
.github/workflows/ci.yml   CI: mvn -B verify with a PostgreSQL service
tools/dashboard/           local project dashboard (python3 tools/dashboard/server.py)
```

`games`, `scores`, and `shots` — the tables behind head-to-head tracking — are intentionally left out of the M1 schema; they land once the booking core is solid.

See the Milestone 1 report for the full ER diagram, wireframes, and architecture writeup.
