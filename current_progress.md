# Current Progress

## Completed
1. 2026-09-28 — Alex Nguyen — Added CLAUDE.md (stack, hard rules, layout, build/test, conventions) and docs/decisions.md and docs/schema-notes.md distilled from the Milestone 1 PDF.
2. 2026-09-28 — Alex Nguyen — Pushed the ShuttleFlow app repo with definitions.md and video_script.md added to its .gitignore.
3. 2026-09-28 — Alex Nguyen — Added the add-endpoint project skill, the reviewer and test-writer agents, and docs/features specs 01-08, and removed trailing whitespace in HomeController.
4. 2026-09-29 — Alex Nguyen — Implemented feature 02 (GET /slots filtering by providerId and sessionType, 10-per-page LIMIT/OFFSET pagination, 400 on bad input) with 18 tests, README docs and reviewer PASS.
5. 2026-09-29 — Alex Nguyen — Updated the stale test note in CLAUDE.md now that src/test exists.
6. 2026-09-29 — Alex Nguyen — Merged CLAUDE.md, docs/, .claude/ agents and skill, and this log into the app repo and updated every path to the single-repo layout.
7. 2026-10-07 — Codex — Started M2-07/M2-09: added safe global 400/401/403/404/409/500 error mapping, shared request validation, BCrypt login, provider login, server-side sessions, logout, and customer/provider session guards. Added 28 passing tests including authentication and RBAC-boundary coverage.
8. 2026-10-08 — Codex — Completed the M2 booking, cancellation, provider availability, provider appointment, filtering, concurrency, frontend, documentation, dashboard history, and voice-script work. The regression suite passes 40 tests and `mvn clean package` succeeds.
9. 2026-10-08 — Alex Nguyen — Completed Milestone 2 end to end: fixed PostgreSQL generated keys, stale seed dates, past-slot browsing, slot-removal 500, missing CORS, framework-error 500s and session fixation; unified the portable schema, finished the UI, raised the suite to 78 passing tests including an 8-thread PostgreSQL race, and updated docs, report, summary and dashboard history.
10. 2026-10-08 — Alex Nguyen — Fixed the dashboard's empty Implemented additions (stale server; now lists all M1 and M2 tasks with a stale-server warning) and added bug code snippets with the faulty lines highlighted in red, captured from the pre-fix commit.
11. 2026-10-08 — Alex Nguyen — Removed the stale nested ShuttleFlow/ copy and organized the milestone files into Milestone1/ and Milestone2/ (assignment PDF, walkthrough and voice scripts), and pointed the dashboard at the root context.md.
12. 2026-10-27 — Alex Nguyen — Planned Milestone 3: recorded the rederive-session outbox/poller/dead-letter design in Milestone3/m3-checklist.md, wrote Milestone3/milestone3plan.md, and added a one-commit-per-checklist-item rule to CLAUDE.md.
13. 2026-10-27 — Alex Nguyen — Recorded the ten Milestone 3 decisions (outbox, after-commit fast path, poller, gateway DTO, timeouts/retry, dead-letter, idempotency key, /health, metrics, logging) in docs/decisions.md (M3 item 1).
14. 2026-10-27 — Alex Nguyen — Added the immutable NotificationRequest DTO and the notifications outbox table (unique idempotency_key, PENDING/SENT/FAILED status) to schema.sql and docs/schema-notes.md (M3 item 2).
15. 2026-10-27 — Alex Nguyen — Added the mock notification service (POST /mock-notifications: 202 new, 200 repeat key stored once, 400 on blank fields) with 2 tests (M3 item 3).
16. 2026-10-27 — Alex Nguyen — Added NotificationGateway: one RestClient POST per event to the configurable shuttleflow.notification.base-url, classifying results as SENT / RETRYABLE (5xx, refused) / PERMANENT (4xx), with 4 real-HTTP stub tests (M3 item 4).
17. 2026-10-27 — Alex Nguyen — Added 1s connect / 2s read timeouts and up to 3 attempts with doubling backoff (timeout, refused, 5xx only; never 4xx) to NotificationGateway, with blip, give-up and hung-service tests (M3 item 5).
18. 2026-10-27 — Alex Nguyen — Wired the transactional outbox: book() and cancel() insert a BOOKED:/CANCELLED:<id> notifications row in their transaction and an @Async after-commit listener sends it (rollback sends nothing); added NotificationRepository, NotificationService, 9 tests including booking/cancel with the service down (M3 items 6 and 7, done together because the same code covers both).
19. 2026-10-27 — Alex Nguyen — Added the @Scheduled outbox poller (every 30s, PENDING rows older than 60s, dead-letter FAILED after 5 rounds) with unit and integration tests (M3 item 6a).
20. 2026-10-27 — Alex Nguyen — Added SLF4J logging with ids only: INFO booking created/cancelled, WARN booking conflict, plus the gateway's sent/attempt-failed/gave-up lines (M3 item 8).
21. 2026-10-27 — Alex Nguyen — Added GET /health (SELECT 1 via HealthController, HealthService and HealthRepository; 200 UP / 503 DOWN with an ERROR log) and 2 tests (M3 item 9).
22. 2026-10-27 — Alex Nguyen — Added GET /metrics with AtomicLong counters (bookings created, booking conflicts, notifications sent, notification failures) and the live pending-outbox count, plus a test (M3 item 10).
23. 2026-10-27 — Alex Nguyen — Documented the notification-service-down failure scenario (detection, impact, recovery, crash variant) in docs/features/09-booking-notifications.md and ticked the required integration tests, which the earlier commits already cover (M3 items 11 and 12).
24. 2026-10-27 — Alex Nguyen — Added a multi-stage Dockerfile (Maven/JDK build stage, non-root eclipse-temurin:17-jre runtime) and .dockerignore; image build not yet run because Docker is not installed on this machine (M3 item 13).
25. 2026-10-27 — Alex Nguyen — Added docker-compose.yml (postgres:16 with pg_isready healthcheck, app waiting on service_healthy and reaching the database at host db), a committed .env.example and .env in .gitignore; docker compose up not yet run here because Docker is not installed (M3 item 14).
26. 2026-10-27 — Alex Nguyen — Added GitHub Actions CI (.github/workflows/ci.yml): Temurin 17 with Maven cache and mvn -B verify on push and pull_request, with a healthchecked postgres:16 service so the race test runs; locally all 100 tests pass including the PostgreSQL race test (M3 item 15).
27. 2026-10-27 — Alex Nguyen — Added the distribution diagram (Milestone3/distribution-diagram.svg and .png): client, scheduler layers, the booking transaction with the outbox insert, commit point, after-commit listener and poller, gateway timeout/retry/fallback, and the DTO crossing to the notification service (M3 item 16).
28. 2026-10-27 — Alex Nguyen — Updated the README (CI badge, Docker run, /health, /metrics, /mock-notifications, the notification design, new tests), added docs/features/10-health-and-metrics.md, and refreshed the CLAUDE.md package layout (M3 item 17).

## What's next
**Run M3 checklist item 18 verification: mvn test, mvn clean package, reviewer agent, then push so the first GitHub Actions run goes green; docker compose up still needs a machine with Docker.**

**Why this is next:** it proves the milestone end to end before the report and video, which depend on the code being final.
