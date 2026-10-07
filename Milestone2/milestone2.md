# CMPE 172 Milestone 2 — Booking and Concurrency

## Purpose

Milestone 2 upgrades ShuttleFlow from the Milestone 1 read-only skeleton into a complete court and coach booking system. The implementation must support customer and provider workflows, session-based authentication, role-based authorization, transactional booking, and a demonstrated strategy for preventing double-booking under concurrent requests.

This document is the implementation checklist and working specification for Milestone 2. The course assignment PDF is the grading authority. Existing endpoint-specific contracts live in `docs/features/`; when those documents conflict with the assignment PDF, document the decision and follow the assignment requirement.

Source assignment:

`CMPE172_Milestone2_Booking_and_Concurrency.pdf`

## Scope

Milestone 2 includes:

1. A usable web interface.
2. Customer login and provider login.
3. Session-based authentication and role-based access control.
4. Available-slot browsing, filtering, and SQL pagination.
5. Customer booking, appointment viewing, and cancellation.
6. Provider availability management.
7. Provider appointment viewing.
8. Input validation and global exception handling.
9. Transactional booking with an explicit ACID and isolation-level explanation.
10. A concrete concurrency-control implementation.
11. Unit, integration, and concurrent-booking tests.
12. Source code, a Milestone 2 report, and a code-walkthrough video.

The following Milestone 1/post-course features are outside this milestone unless explicitly needed by the implementation:

- Booking-confirmation email/SMS integrations.
- Operational metrics and health-check work planned for Milestone 3.
- Conversational booking, autonomous waitlists, and RAG planned for Milestone 4.
- Match tracking, scores, shots, and head-to-head analytics.

## Required architecture

The application must preserve this request flow:

```text
Web UI → Controller → Service → Repository → Database
       ←           ←         ←             ←
```

### Backend layers

- `controller`: HTTP routing, request binding, session extraction, response status selection, and DTO responses. Controllers must remain thin.
- `service`: business rules, validation, role checks, ownership checks, transaction boundaries, and translation of domain/database failures.
- `repository`: the only layer that contains SQL or accesses `JdbcTemplate`. Use parameterized SQL and `RowMapper` mappings.
- `dto`: immutable request/response shapes. Never expose database rows, password hashes, session data, or internal credentials.
- `src/main/resources`: schema, seed data, and application configuration.

### Technology constraints

- Java 17.
- Spring Boot 3.3.4.
- Maven.
- Spring JDBC with `JdbcTemplate`.
- No JPA, Hibernate, Spring Data repositories, or other ORM.
- Current local database: file-based H2.
- The course design names PostgreSQL. Any migration from H2 to PostgreSQL must be an explicit documented decision, with SQL and test behavior updated consistently.
- SQL must remain portable unless the selected concurrency strategy intentionally requires a database-specific feature.

## Web interface requirements

The student may use Thymeleaf with HTML/Bootstrap or a React/Angular SPA consuming JSON. The interface must provide at least:

- A home view.
- Login view/form.
- Available-slot browsing.
- Filtering and pagination.
- A booking form/action for a selected slot/service.
- Booking confirmation.
- Customer appointment views.
- Customer cancellation.
- Provider availability creation/removal.
- Provider appointment viewing.

The report and code-walkthrough video must clearly explain how a UI action travels through the backend layers and how the response returns to the UI.

## Authentication and RBAC

### Identity and passwords

- Login accepts an email/username and password.
- User records contain a BCrypt password hash in `password_hash`.
- Passwords must never be stored, returned, printed, logged, or placed in DTOs in plaintext.
- Login must compare the supplied password with the stored BCrypt hash.
- Unknown accounts and incorrect passwords must have the same failure behavior so that accounts cannot be enumerated.

### Session behavior

- A successful login establishes a server-side session.
- The session must identify the authenticated user and role; it must not store a plaintext password.
- The role must be loaded from the user's database row through JDBC.
- Provider sessions must also identify the provider row needed for provider-owned operations.
- Unauthenticated requests must receive `401`.
- An authenticated user attempting a route outside their role must receive `403`.

OAuth or JWT may be used instead of the simple session approach, but session-based authentication is the default expected implementation and must remain coherent throughout the application.

### Roles

The database roles are:

- `CUSTOMER`: may browse slots, book, view their own appointments, and cancel their own eligible appointments.
- `PROVIDER`: may manage their own availability and view appointments booked on their own slots. Providers may not use customer-only booking operations unless a documented requirement explicitly permits it.

Do not use `ADMIN` in place of the project role `PROVIDER` without documenting and consistently applying the change.

## Endpoint contracts

The following contracts are the required feature surface. Existing detailed specifications are in `docs/features/`.

### 1. Customer/provider login

#### `POST /auth/login`

Request:

```json
{
  "email": "player@example.com",
  "password": "secret"
}
```

Behavior:

- Valid credentials for a customer: `200`, session established, role returned.
- Valid credentials for a provider: `200`, session established, role `PROVIDER` returned.
- Wrong password: `401`.
- Unknown email: `401`, with the same externally observable behavior as a wrong password.
- Missing or invalid credentials: `400`.
- Never include `password_hash` or the submitted password in the response.

#### `POST /auth/provider/login`

Behavior:

- Valid provider credentials: `200`, session established, `role = PROVIDER`, and `providerId` returned where needed.
- Wrong password or unknown email: `401` with indistinguishable failure behavior.
- Valid credentials for a customer: `403`.
- Provider user without a corresponding `providers` row: `403`.
- Missing or invalid credentials: `400`.

Provider-only endpoints must reject unauthenticated requests with `401` and customer sessions with `403`.

### 2. Browse, filter, and paginate slots

#### `GET /slots`

Supported filters should include the existing project contract and the Milestone 2 assignment's provider/service/date filtering needs:

- `providerId` — provider filter.
- `serviceId` or service filter — service filter.
- Date or date-range filter where supported by the chosen API design.
- Existing `sessionType` values: `OPEN_PLAY` and `COACHING`.
- `page` — 1-based page number, default `1`.

Rules:

- Return only available/open slots.
- Use SQL `LIMIT 10 OFFSET (page - 1) * 10`; do not fetch all rows and paginate in Java.
- Order results deterministically by start time, with a stable secondary key if needed.
- Filters must combine correctly with one another and pagination.
- A page beyond the final page returns `200` and an empty list.
- Invalid page values, unknown session types, invalid IDs, and malformed dates return `400`.
- The response includes the slot ID, provider name, service name, start/end times, and price as appropriate.
- Do not expose password or internal credential columns.

Existing session-type mapping:

- `OPEN_PLAY` → provider type `COURT`.
- `COACHING` → provider type `COACH`.

### 3. Book a slot

#### `POST /appointments`

Example request:

```json
{
  "slotId": 4,
  "serviceId": 1
}
```

If the selected service is already fully represented by the slot, `serviceId` may be omitted from the final API contract; the implementation must document the decision and validate that the slot/service relationship is valid.

Rules:

- Only an authenticated customer may book.
- An open slot can be booked once.
- A successful booking returns `201` and creates an appointment.
- The slot is no longer available after a successful booking.
- A nonexistent slot returns `404`.
- A past slot returns `409`.
- Missing, malformed, or invalid IDs return `400`.
- An unauthenticated request returns `401`.
- A provider attempt returns `403`.
- A conflict caused by an existing booking or concurrent race returns `409` and a safe booking-conflict message.
- Appointment creation and slot-state change occur in one transaction.

### 4. View customer appointments

#### `GET /appointments?view={upcoming|history}`

Rules:

- `view` defaults to `upcoming`.
- `upcoming` returns the caller's future active/booked appointments, soonest first.
- `history` returns the caller's past appointments and cancelled appointments, most recent first.
- Only the authenticated caller's records may be returned.
- No records returns `200` with an empty array.
- Unknown view values return `400`.
- Unauthenticated requests return `401`.
- Each item includes appointment ID, provider name, service name, start/end times, and status.

### 5. Cancel a customer appointment

#### `DELETE /appointments/{id}`

Rules:

- Only the appointment owner may cancel it.
- Owner cancels an eligible upcoming appointment: `204`.
- A different authenticated user: `403`.
- A nonexistent appointment: `404`.
- A past appointment: `409`.
- An unauthenticated request: `401`.
- Cancellation must update appointment state and slot availability atomically.
- The rebooking behavior must be explicit: if cancelled slots become available again, the appointment uniqueness design must allow that safely.

### 6. Provider availability management

#### `POST /provider/slots`

Example request:

```json
{
  "serviceId": 1,
  "startTime": "2026-10-04T18:00:00",
  "endTime": "2026-10-04T19:00:00"
}
```

Rules:

- Only an authenticated provider may create availability.
- The service must belong to the authenticated provider.
- The new slot is open and appears in `GET /slots`.
- `endTime` must be after `startTime`; otherwise `400`.
- A start time in the past returns `400`.
- Duplicate provider/start-time availability returns `409`.
- A service belonging to another provider returns `403`.
- A nonexistent service returns `404`.
- Unauthenticated requests return `401`.
- Customer sessions return `403`.

#### `DELETE /provider/slots/{id}`

Rules:

- Only the owning provider may remove a slot.
- An own open slot is removed/deactivated and no longer appears in `GET /slots`; return `204`.
- A slot belonging to another provider returns `403`.
- A nonexistent slot returns `404`.
- A slot with an active/confirmed appointment returns `409`; booking must be cancelled first.
- Unauthenticated requests return `401`.
- Customer sessions return `403`.

### 7. Provider appointments

#### `GET /provider/appointments`

Rules:

- Only an authenticated provider may access this endpoint.
- Return only appointments on that provider's own slots.
- Sort by soonest appointment first.
- Exclude cancelled appointments.
- Include appointment ID, slot ID, service name, start/end times, and booking customer's name and email.
- Never expose appointments for another provider.
- No bookings returns `200` with an empty array.
- Unauthenticated requests return `401`.
- Customer sessions return `403`.

## Data model requirements

The existing schema uses these core tables:

### `users`

- `user_id` primary key.
- Unique email/username.
- `password_hash`.
- Full name.
- Role constrained to `CUSTOMER` or `PROVIDER`.

### `providers`

- `provider_id` primary key.
- Foreign key to `users`.
- Provider name, type, and location.
- Provider type constrained to `COURT` or `COACH`.
- A provider user may have at most one provider row according to the project model.

### `services`

- `service_id` primary key.
- Foreign key to provider.
- Name, duration, capacity, and price as applicable.
- Duration must be positive.

### `availability_slots`

- `slot_id` primary key.
- Foreign keys to provider and service.
- Start and end times.
- End must be after start.
- Slot state must support open, booked/active, and cancelled/removed behavior.
- Preserve the provider/start-time uniqueness rule unless a documented, equivalent constraint is introduced.

### `appointments`

- `appointment_id` primary key.
- Foreign keys to slot and user.
- Appointment status.
- Booking timestamp.
- Preserve a database-level uniqueness guard so that a slot cannot have two active bookings.

## Status model and required reconciliation

The Milestone 2 PDF says appointment statuses are `BOOKED` and `CANCELLED`, with `COMPLETED` for past appointments. Existing Milestone 1 code and feature documents use `CONFIRMED` and `CANCELLED`. Before implementing the full workflow, make one explicit project decision:

1. Migrate the schema, seed data, feature documents, queries, DTOs, and tests to the PDF terminology (`BOOKED`, `CANCELLED`, optionally derived/recorded `COMPLETED`); or
2. Retain `CONFIRMED` as an internal/API synonym and document why it satisfies the assignment's booked state.

Do not mix `BOOKED` and `CONFIRMED` inconsistently across the schema, SQL, API, and tests.

Past appointment behavior must be deterministic. Decide whether `COMPLETED` is persisted or derived from a booked appointment whose start time has passed, and document the choice in `docs/decisions.md`.

## Transaction and ACID requirements

### Booking transaction

Booking must be one service-level transaction containing all state changes required to reserve a slot:

1. Authenticate the caller and confirm customer role.
2. Load the slot and validate that it exists, is open, is not in the past, and belongs to the expected service/provider relationship.
3. Apply the selected concurrency-control strategy.
4. Create exactly one appointment for the slot.
5. Update the slot state, if slot state is persisted separately.
6. Commit all changes together.

If any step fails, all changes must roll back. No partially created appointment or partially reserved slot may remain.

### ACID explanation required in code/report

- Atomicity: booking and associated slot changes succeed or fail together.
- Consistency: foreign keys, status checks, ownership rules, and uniqueness constraints remain valid.
- Isolation: concurrent requests cannot both observe and reserve the same open slot successfully.
- Durability: after commit, the booking remains stored and visible to subsequent requests.

### Isolation level

Choose and document an isolation level. The explanation must state:

- Which isolation level is used.
- Why it is appropriate for booking.
- What anomalies it prevents or permits.
- How it interacts with the selected lock/version strategy.
- Whether H2 and PostgreSQL behave equivalently for the chosen SQL.

Do not claim that the default isolation level is sufficient without explaining why.

## Concurrency-control requirements

### Race condition to demonstrate

Two different users submit booking requests for the same open slot at approximately the same time. Both requests may initially read that the slot is open. The system must ensure that exactly one booking succeeds; the other must receive a conflict response, and the database must never contain two active appointments for the same slot.

### Acceptable strategies

Choose one strategy and justify it:

- Optimistic version check: add/use a version value, condition the update on the expected version and open state, and treat an update count of zero as a conflict; or
- Pessimistic row lock: use a database-supported `SELECT ... FOR UPDATE` within the transaction. The assignment specifically notes this for MySQL/PostgreSQL and recommends an optimistic version check for SQLite; verify compatibility if retaining H2.

The current project default should be the optimistic approach unless the database is deliberately migrated to PostgreSQL and the locking approach is documented.

### Mandatory database backstop

Keep a database uniqueness constraint preventing multiple active bookings for a slot. Application code must not rely on a check-then-insert sequence alone. Catch the resulting constraint/concurrency failure and map it to `409`.

### Retry

If the selected strategy requires retry, implement bounded retry only for the retryable transaction/conflict condition. Do not retry validation failures, authorization failures, nonexistent resources, or malformed requests. Document retry count and behavior.

## Validation and error handling

Validate at the service boundary and, where useful, at request binding:

- Required fields are present.
- IDs are positive and syntactically valid.
- Email/username and password fields are valid for the login contract.
- Dates and times parse correctly.
- End time is after start time.
- New availability is not in the past.
- Slot/service/provider relationships are valid.
- Appointment ownership is checked before cancellation.
- Roles are checked before role-specific operations.
- State transitions are legal.

Use a global exception handler and safe, consistent error DTOs. At minimum, support:

| Status | Meaning |
|---|---|
| `400` | Malformed input, missing field, invalid format, invalid filter, or invalid time range |
| `401` | No authenticated session or invalid login credentials |
| `403` | Wrong role or authenticated user does not own the resource |
| `404` | Requested slot, service, appointment, or provider does not exist |
| `409` | Booking race/conflict, past appointment/slot, duplicate availability, or illegal state transition |

Do not send stack traces, SQL details, password information, or internal exception messages to clients.

## Testing plan

### Unit tests

Test service rules independently of the web layer where practical:

- Valid booking.
- Booking by an unauthenticated caller.
- Booking by a provider.
- Missing/nonexistent slot.
- Past slot.
- Already-booked slot.
- Booking conflict translation.
- Appointment ownership on cancellation.
- Cancellation of past appointment.
- Slot reavailability after valid cancellation.
- Provider ownership of services and slots.
- Invalid start/end times.
- Past availability creation.
- Role checks for every protected operation.

### Endpoint/integration tests

Cover every new endpoint and assert both status and response/body or database effect. Include:

- Login success and failure equivalence.
- Session persistence across requests.
- `401` unauthenticated behavior.
- `403` wrong-role behavior.
- `404` missing-resource behavior.
- `400` validation behavior.
- `409` conflict behavior.
- Customer isolation: one customer cannot view or cancel another customer's appointment.
- Provider isolation: one provider cannot manage or inspect another provider's resources.
- SQL pagination with `LIMIT`/`OFFSET`.
- Filter combinations and stable ordering.

### Concurrency test

Add an automated test that:

1. Creates or selects one open slot.
2. Creates two distinct authenticated customer booking attempts.
3. Releases both attempts at the same time using a barrier/latch.
4. Waits for both results.
5. Asserts exactly one success.
6. Asserts the other result is a booking conflict (`409` at the HTTP boundary or the equivalent service exception).
7. Asserts the database contains exactly one active appointment for the slot.
8. Asserts the slot state is consistent with that appointment.
9. Cleans up or isolates test data so the result is repeatable.

The test must exercise the real transaction/database behavior, not merely mock both calls to return predetermined outcomes.

## Documentation and deliverables

### Milestone 2 report

The report PDF must include:

- Booking workflow.
- Frontend-to-backend request flow.
- Authentication and role-based access design.
- Customer and provider feature behavior.
- The concrete same-slot race condition.
- ACID analysis.
- Chosen isolation level and rationale.
- Chosen optimistic or pessimistic concurrency strategy.
- Retry behavior where applicable.
- Database uniqueness backstop.
- Java and SQL snippets showing the important implementation.
- Test strategy and the result of the concurrent-booking test.

### Source code

Submit the complete working booking system, including:

- Web interface.
- Authentication and sessions.
- RBAC.
- Booking/cancellation.
- Provider availability.
- Provider appointment view.
- Database schema and seed updates.
- Unit/integration tests.
- Two-thread or equivalent concurrency test.

### Code-walkthrough video

- Minimum five minutes.
- Explain the core code and how it works; do not only demonstrate the running application.
- Show the request flow, authentication/RBAC, transaction boundary, concurrency strategy, database constraint, and tests.

### Submission package

The assignment requests a package named:

`CMPE172_Milestone2_FirstName_LastName.zip`

The final submission also requires the GitHub repository link and video link.

## Implementation checklist

### Foundation

- [ ] Confirm the final database choice: H2 for local development or PostgreSQL.
- [ ] Resolve `BOOKED` versus `CONFIRMED` terminology and update all affected artifacts.
- [ ] Decide whether `COMPLETED` is persisted or derived.
- [ ] Decide how cancelled appointments release a slot while preserving uniqueness safety.
- [ ] Choose isolation level and concurrency strategy.
- [ ] Record decisions in `docs/decisions.md`.

### Authentication/RBAC

- [ ] Add BCrypt hashing/checking dependency and implementation.
- [ ] Implement login validation and generic credential failures.
- [ ] Establish and read server-side sessions.
- [ ] Add role and provider ownership checks.
- [ ] Test `401` and `403` behavior for every protected route.

### Customer flow

- [ ] Browse/filter/paginate slots.
- [ ] Book an open slot.
- [ ] View upcoming appointments.
- [ ] View appointment history.
- [ ] Cancel an eligible owned appointment.
- [ ] Show confirmation in the UI.

### Provider flow

- [ ] Create own-service availability.
- [ ] Remove own open availability.
- [ ] Reject removal when an active appointment exists.
- [ ] View own provider appointments and customer details.

### Reliability

- [ ] Add service-level transactions.
- [ ] Implement and document isolation/concurrency controls.
- [ ] Preserve database uniqueness backstop.
- [ ] Add conflict mapping and bounded retry where needed.
- [ ] Add global error handling without stack traces.

### Verification

- [ ] Add unit tests for business rules.
- [ ] Add endpoint/integration tests for all routes.
- [ ] Add real concurrent same-slot test.
- [ ] Run `mvn test`.
- [ ] Run `mvn clean package`.
- [ ] Update README and feature documentation.
- [ ] Prepare the report and five-minute code walkthrough.

## Definition of done

Milestone 2 is complete only when a clean checkout can build and run the application; a customer can log in, browse, book, view, and cancel appointments; a provider can log in, manage availability, and inspect their bookings; all protected actions enforce session/RBAC rules; booking is transactional; two simultaneous attempts cannot both succeed; the automated concurrency test proves that behavior; and the report, source package, and walkthrough cover the required design decisions.

