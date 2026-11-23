# ShuttleFlow

Badminton court and coach booking system (CMPE 172 project). Requirements: `docs/CMPE 172 Milestone 1.pdf`; distilled in `docs/decisions.md` and `docs/schema-notes.md`.

## Stack

- Java 17, Spring Boot 3.5.16 (`spring-boot-starter-web`, `spring-boot-starter-jdbc`), Maven
- SQL via `JdbcTemplate` — no ORM
- PostgreSQL at runtime (`application.properties`); in-memory H2 (PostgreSQL mode) for tests only. One `schema.sql` serves both, so keep SQL standard/portable
- Frontend: Next.js 16 (App Router, JavaScript) in `frontend/` (`npm run dev` on :5173); calls `/api/*`, which it rewrites to the API on :8080
- Auth: signed JWT (HS256, nimbus-jose-jwt) in an `HttpOnly`, `SameSite=Lax` cookie `SF_AUTH`; no HTTP session. Services take the caller (`UserSession`), never the token

## Hard rules

- **No ORM.** No JPA/Hibernate/Spring Data repositories (Spring Data Redis is only the `GET /slots` cache client). Raw SQL through `JdbcTemplate` only.
- **Layering is strict:** `controller` → `service` → `repository` → DB. Controllers hold no logic; only repositories touch SQL; controllers return DTOs, never rows/entities.
- **Double-booking is prevented by the database:** `UNIQUE (active_slot_id)` on `appointments` stays (it equals `slot_id` while `BOOKED`, `NULL` once `CANCELLED`, so history and rebooking work). Booking also locks the slot with `SELECT ... FOR UPDATE`. The service layer catches the constraint violation and returns "Court is already booked." Never replace it with a check-then-insert in Java.
- **Pagination uses SQL `LIMIT` + `OFFSET`, 10 per page.**
- Keep the schema aligned with the spec tables (`users`, `providers`, `services`, `availability_slots`, `appointments`) and their constraints; don't rename them.
- Roles are `CUSTOMER` and `PROVIDER` in the DB.
- Use parameterized queries (`?`) — never concatenate user input into SQL.
- Never store or log plaintext passwords; seed hashes are placeholders only.
- Don't commit `data/`, `target/`, `context.md`, `definitions.md`, `video_script.md` (already gitignored).

## Package layout

This is a single repository: the Maven project, the docs and the Claude workflow files all live at the repo root. Run Maven and start Claude Code from the repo root.

```
CLAUDE.md, current_progress.md          project rules and progress log
.claude/                                agents/ (reviewer, test-writer) and skills/add-endpoint
docs/                                   requirements PDF + decisions.md, schema-notes.md, features/
pom.xml
src/main/java/com/shuttleflow/
├── ShuttleflowApplication.java         entry point
├── controller/                         @RestController — HTTP in, DTO out (Home, Slot, Auth, Appointment, Provider, Health, Metrics, MockNotification, Ai) + ApiExceptionHandler
├── service/                            @Service — business rules, RBAC, transactions (Home, Slot, Auth, Appointment, Provider, Notification outbox + Gateway, Health, Metrics) + exceptions
├── repository/                         @Repository — JdbcTemplate + RowMapper (Slot, User, Appointment, Provider, Notification, Health)
├── dto/                                immutable request/response types
├── ai/                                 M4: BookingTools (agent tools), ChatAgentService, ReminderAgent, KnowledgeService (RAG)
├── auth/                               JwtService + JwtAuthFilter (login token), UserSession (caller identity), SessionAuth (401/403 guards)
└── config/                             PasswordConfig (BCrypt), CorsConfig, CacheConfig (Redis slot cache), DatabaseInitializer
src/main/resources/
├── application.properties              datasource + sql.init + Gemini (api-key only)
├── schema.sql                          DDL, drops and recreates tables on every boot
├── seed.sql                            sample data
└── knowledge/shuttleflow-policies.md   RAG knowledge source
src/test/java/com/shuttleflow/          tests mirroring the main package
frontend/                               Next.js app: app/ pages, lib/api.js, next.config.mjs (/api rewrite)
```

## Build and test

Run from the repo root:

```bash
mvn spring-boot:run        # start on http://localhost:8080 (needs local PostgreSQL db "shuttleflow")
mvn clean package          # build the jar into target/
mvn test                   # run tests (src/test/java/com/shuttleflow/)
```

Requires JDK 17+, Maven 3.9+ and PostgreSQL (tests need no server; set `SHUTTLEFLOW_PG_TEST_URL` to also run the PostgreSQL race test). Smoke check: `curl localhost:8080/` and `curl localhost:8080/slots`.

`schema.sql` and `seed.sql` reload on every startup (`spring.sql.init.mode=always`), so local data resets on restart. Hosted (Render) runs with `SQL_INIT_MODE=never` and `DB_INIT_IF_EMPTY=true`, so `DatabaseInitializer` seeds only an empty database.

Deployment: Next.js on Vercel (Root Directory `frontend`, `API_URL` = the API URL), the API on Render from `render.yaml` + `Dockerfile`, PostgreSQL on Neon. See README "Deploy".

## Conventions

- Package root `com.shuttleflow`; one class per file; classes named by role (`XxxController`, `XxxService`, `XxxRepository`, `XxxDto`).
- DTOs: immutable — final fields, constructor + getters, no setters, no Lombok.
- Constructor injection; no field `@Autowired`.
- SQL lives as constants in the repository class; map rows with `RowMapper`.
- Database naming: `snake_case` tables/columns, `<table>_id` primary keys, statuses as uppercase strings guarded by `CHECK`.
- JSON fields are camelCase (`slotId`, `providerName`, `startTime`).
- Enforce data rules in the schema (constraints) as well as in code.
- Add tests under `src/test/java/com/shuttleflow/` mirroring the main package.

## Commits (one per small feature)

- Commit after every small, self-contained addition. One checklist item (e.g. one item in `Milestone3/m3-checklist.md`) is one commit. Don't batch several items into one commit.
- Each commit includes the feature's tests, and `mvn test` must pass first. Tick the matching checklist box in the same commit.
- Standing permission: commit each finished item without asking. Pushing still needs the user's go-ahead.

## Progress tracking (required after every commit or push)
After making a commit or push in this repo, invoke the global
`progress-tracker` skill before touching `current_progress.md` — it is
the authoritative, strict spec for how that file is created (if
missing) and updated (Completed appends, What's Next replacement,
formatting, attribution). Do not improvise the update from memory or
from this summary; invoke the skill every time this rule fires.
