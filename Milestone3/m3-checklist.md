# Milestone 3 Checklist — Integration, Distribution & Deployment

Source: `Milestone3/CMPE172_Milestone3_Integration_and_Deployment.pdf`
(reviewed 2026-10-20). The PDF is the grading authority; this file adds
sequencing and ShuttleFlow-specific design choices.

Starting state (2026-10-20): none of M3 exists yet. No notification code, no
`/health`, no metric, no logging, no Dockerfile, no compose file, no
`.github/workflows/`. `AppointmentService.book()` and `cancel()` are
`@Transactional`, so the notify call must run after commit, not inside them.

Full build plan: `Milestone3/milestone3plan.md`.

## Changes from rederive session (2026-10-27)

The design session improved this plan in these places. The items below are
already updated.

1. **Transactional outbox** (items 6, 6a, 11). The owed notification is a
   `notifications` row written in the same transaction as the booking or
   cancel. With only an after-commit listener, a crash right after commit
   loses the notification. With the outbox it cannot be lost.
2. **Exhausted retries stay PENDING** (items 5, 6a). A row whose retries all
   failed is not dropped. A scheduled poller (on a timer, not "on crash")
   resends `PENDING` rows older than ~60s. A 4xx, or too many failed rounds,
   moves the row to the `FAILED` dead-letter state so it is never resent
   forever.
3. **Refinements.**
   - The payload field is `recipient`, not "email" (item 2).
   - The idempotency key comes from event type + appointment id, never from
     payload details (item 6).
   - Session IDs are never logged (item 8).
   - A `.env.example` with placeholders is committed (item 14).

## Sequential implementation plan

1. [x] **Record M3 decisions** in `docs/decisions.md`: commit-then-notify
   mechanism, outbox table + poller, timeout/retry values, idempotency key, fallback behavior,
   `/health` shape, chosen metric, and whether to use Actuator or plain code.
2. [x] **Notification DTO.** Add immutable `NotificationRequest`
   (`recipient`, `type` = `BOOKING_CONFIRMED` | `BOOKING_CANCELLED`, `message`,
   plus `idempotencyKey` and `appointmentId`) in `dto/`.
3. [x] **Mock notification service.** Add a second controller, e.g.
   `MockNotificationController` at `POST /mock-notifications`. It validates
   the DTO, logs and stores it in memory, and returns success. A repeated
   `idempotencyKey` returns success without storing it twice.
4. [x] **Notification client (Gateway).** Add `NotificationGateway` with one
   coarse-grained method, `send(NotificationRequest)`. It calls the mock over
   real HTTP with `RestClient` (already in `spring-boot-starter-web`). The
   base URL comes from a property (`shuttleflow.notification.base-url`).
5. [x] **Timeout, retry, fallback.**
   - Set connect and read timeouts on the request factory (e.g. 1s / 2s).
   - Retry a few times (e.g. 3 attempts, short backoff), only on timeout,
     connection error, or 5xx. Never retry 4xx.
   - Fallback: log WARN or ERROR and count the failure. Leave the outbox row
     `PENDING` for the poller; a 4xx marks it `FAILED` instead. Never throw
     back into the booking path. The booking response is unchanged.
6. [x] **Commit-then-notify via outbox.** In `book()` and `cancel()`, insert a
   `notifications` row (`PENDING`, unique `idempotency_key`) in the same
   transaction, then publish an application event. Handle it with
   `@TransactionalEventListener(phase = AFTER_COMMIT)`. A rollback then sends
   nothing, and the HTTP call never holds the slot row lock.
   Idempotency key: `BOOKED:<appointmentId>` / `CANCELLED:<appointmentId>`,
   so every retry carries the same key. The listener is `@Async`, so the
   booking response never waits on the call. It is the fast path, and the
   poller (6a) is the backstop.
6a. [x] **Outbox poller.** A `@Scheduled` job every ~30s resends `PENDING`
   rows older than ~60s. It sets `SENT` on success and `FAILED` on a 4xx or
   after 5 failed rounds. Status updates are guarded with
   `WHERE status = 'PENDING'`.
7. [x] **Trigger on both book and cancel.** The cancel path needs the
   customer email and slot details for the message. No new query is needed:
   the recipient is `UserSession.getEmail()`, and the court and time come from
   the existing `AppointmentRepository.findCustomerAppointment`.
8. [x] **Logging.** Add SLF4J `Logger` (comes with Spring Boot) and emit
   these messages:
   - INFO: `Booking created appointmentId=12 slotId=5 userId=3`, `Notification sent key=BOOKED:12 attempt=1`
   - WARN: `Notification attempt 2/3 failed key=BOOKED:12: Read timed out`, `Booking conflict slotId=5`
   - ERROR: `Notification gave up after 3 attempts key=BOOKED:12`, `Health check: database unreachable`
   - Never log passwords, password hashes, session IDs (a session ID is a
     login), or full request bodies. Log ids only.
9. [x] **Health endpoint.** `GET /health` runs `SELECT 1` through a
   repository. It returns `200 {"status":"UP","database":"UP"}`, or
   `503 {"status":"DOWN","database":"DOWN"}` when the query fails. Keep the
   layers: `HealthController` → `HealthService` → `HealthRepository`.
10. [ ] **Metric.** Pick at least one: bookings created, failed bookings
    (409s), notification failures, or average booking latency. Expose it
    (e.g. `GET /metrics` or inside `/health`). Plain counters
    (`AtomicLong`) are enough. Actuator + Micrometer is the other option.
11. [ ] **Failure scenario + recovery.** Document one scenario: the
    notification service is down or slow. Recovery: bounded retries, then
    fallback. The booking is still committed, and the failure is logged and
    counted. The outbox row stays `PENDING`, and the poller resends it once
    the service is back (required, not optional).
12. [ ] **Integration test (required).** Point
    `shuttleflow.notification.base-url` at a dead port or a stub that times
    out. Book a slot and assert `201`, plus one active appointment in the DB.
    Add tests for cancel with the service down, for a retry that succeeds on
    attempt 2, for a duplicate key that is not stored twice, and for
    `/health` UP/DOWN. Outbox tests: a committed booking writes a `PENDING`
    row, a rolled-back (409) booking writes none, and a 4xx sets `FAILED`.
13. [ ] **Dockerfile.** Multi-stage build: `maven:3.9-eclipse-temurin-17`
    builds the jar, then `eclipse-temurin:17-jre` runs it on port 8080.
    Add a `.dockerignore` (`target/`, `data/`, `.git/`).
14. [ ] **docker-compose.yml.** Two services: `db` (`postgres:16`, with a
    healthcheck) and `app` (`depends_on: db: condition: service_healthy`,
    `SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/shuttleflow`). Read
    credentials from env or `.env`. Never commit real secrets. Commit a
    `.env.example` with placeholder values, and add `.env` to `.gitignore`. Smoke test:
    `docker compose up` then `curl localhost:8080/health`.
15. [ ] **GitHub Actions.** Add `.github/workflows/ci.yml`. It runs on every
    `push` (and `pull_request`): `actions/setup-java` with Temurin 17 and the
    Maven cache, then `mvn -B verify`. Optional: a `postgres:16` service
    container plus `SHUTTLEFLOW_PG_TEST_URL`, so the race test also runs in CI.
16. [ ] **Distribution diagram** (PNG or PDF):
    Client → Appointment Scheduler (Spring app: controller → service →
    `NotificationGateway`) → Notification Service (`/mock-notifications`).
    Mark the timeout/retry/fallback on the arrow and the commit point before
    the call.
17. [ ] **Docs.** Update README (run with Docker, `/health`, metric, CI
    badge), `docs/decisions.md`, and `docs/features/`.
18. [ ] **Verify.** `mvn test`, `mvn clean package`, `docker compose up`,
    a green CI run on GitHub, and a reviewer agent PASS.

## Design justification notes (for the report)

- **Coarse-grained:** one call per business event carries the whole
  notification. Per-field calls would add network round trips, failure
  points, and latency.
- **DTO across the boundary:** the remote side never sees `AppointmentRecord`
  or DB rows. This keeps the contract stable if the mock is later replaced
  by Slack, Discord, or email.
- **Commit-then-notify:** a notification must never announce a booking that
  rolled back. A slow remote call must never hold the `FOR UPDATE` lock.
- **Idempotent retries:** a timeout can hide a success. The same key makes a
  resend harmless. The key comes from event type + appointment id, not
  payload details: a cancel-and-rebook has identical details but is a new event.
- **Transactional outbox:** the owed notification commits with the booking,
  so a crash right after commit cannot lose it. A poller resends what the
  fast path missed.
- **Dead-letter state:** rows that can never succeed (a 4xx, or too many
  failed rounds) become `FAILED` and are set aside for a person, not retried forever.
- **Reuse:** the same gateway later sends AI-generated reminders, so keep
  `type` open to extension.

## Deliverables

- [ ] Milestone 3 report (PDF, mandatory): distribution boundary
      (coarse-grained interface, DTO, fault handling, commit-then-notify) and
      system management (logging plan, health endpoint, metric, failure and
      recovery), with code snippets. This becomes a section of the Final report.
- [ ] Distribution diagram (PDF or PNG).
- [ ] Source code: notification boundary, health endpoint, metrics,
      Dockerfile, docker-compose.yml, GitHub Actions workflow, tests.
- [ ] Code-walkthrough video, **minimum 5 minutes**. Explain the code; do
      not just demo the app.
- [ ] Submission: `CMPE172_Milestone3_FirstName_LastName.zip` (report +
      diagram + code), plus the GitHub repo link and the video link.
- [ ] Check the General Rules in the Project Overview for submission,
      academic integrity, and video rules.

## Optional

- [ ] Replace the mock with a real channel (Discord/Slack webhook or Gmail
      SMTP) behind the same `NotificationGateway` interface.
