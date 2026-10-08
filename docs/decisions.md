# Decisions — Milestone 1

Source: `docs/CMPE 172 Milestone 1.pdf`, plus what is currently implemented in `src/`. Where the two disagree it is flagged under **Open items**.

## Product

- **Name:** ShuttleFlow — badminton court and coaching booking system.
- **Users:** browse court availability/times and coaching sessions; manage their own bookings.
- **Providers (coaches / court managers):** post and remove availability.

## Roles

| Role | Who | Can do |
|---|---|---|
| `CUSTOMER` | Player | Browse open court times; book or cancel court bookings |
| `PROVIDER` (Admin) | Court manager / coach | Manage court availability; view bookings; create/remove time slots |

## Stack

- Java, Spring Boot, SQL via **JDBC — no ORM**.
- PostgreSQL is the runtime target named in the spec. H2 is test-only infrastructure configured under `src/test/resources/application.properties`.
- Framing from the spec: Spring Boot in place of J2EE, REST in place of CORBA/distributed objects.

## Core features (full project scope)

1. Email + password login for players.
2. Filter by coach openings and session type (open play / coaching); **paginated search, 10 per page, via SQL `LIMIT` + `OFFSET`**.
3. Book a slot for open play.
4. View upcoming confirmed bookings and history.
5. Cancel own bookings.
6. Coach / court manager login.
7. Court managers set or remove availability.
8. Coaches view appointments booked with them (including who booked).
9. Automated email/SMS confirming a reservation and its time.
10. Logging/metrics showing booking activity and overall active history.
11. AI: conversational booking assistant (courts for play or coaching, available times) and Q&A over a small internal knowledge base (RAG).

Milestone 1 itself is only the design + a read-only skeleton (`GET /`, `GET /slots`). Features 1, 3–10 and pagination/filtering are not built yet.

## Architecture decisions

| Decision | Choice | Why |
|---|---|---|
| Data access | Raw SQL through `JdbcTemplate` | Spec forbids an ORM |
| Layering | `@RestController` → `@Service` → `@Repository` → DTO | Front-controller pattern via Spring's `DispatcherServlet`; keeps HTTP, rules and SQL separate |
| Double-booking prevention | Pessimistic slot lock plus a unique active-slot database key | The service locks the slot inside the booking transaction; `active_slot_id` remains a database backstop and permits cancelled history/rebooking. |
| Pagination | SQL `LIMIT`/`OFFSET`, page size 10 | Spec requirement |
| Provider modelling | A provider is a user with at most one provider row (`providers.user_id`) | Spec: "each user is at most one provider" |
| Weak entities | None | Every entity has its own ID, so all are strong even when existence depends on another entity |

## Open items / discrepancies to resolve

1. **PostgreSQL vs H2.** **Resolved 2026-10-07:** PostgreSQL is the runtime/project target; H2 is test-only infrastructure.
2. **Spec typo in constraints.** The PDF's Justification says `CHECK start_time > end_time`; the relational schema table and `schema.sql` correctly use `end_time > start_time`. Treat the latter as correct.
3. **Spec typo in the double-booking paragraph.** "enforced now through java, but through the database" almost certainly means *not* through Java, but through the database. The implementation follows that reading.
4. **Role naming.** The roles table says "Admin" for coach/court manager, but the schema stores `PROVIDER`. Keep `PROVIDER` in the DB.
5. **Diagrams not captured.** The block diagram (page 2) and ER diagram (page 3) are images in the PDF; their content was not extracted here. Check them against `schema-notes.md`.
6. **Session type filter.** The spec filters by "open play / coaching" but the schema has no explicit session-type column; it is implied by `providers.type` (`COURT`/`COACH`) and `services.name`. **Resolved 2026-09-29:** filter on `providers.type` — `OPEN_PLAY` = `COURT`, `COACHING` = `COACH`; no schema change.

## Resolved decisions — 2026-10-07

1. **Milestone 1 relational schema contract:** Treat the relational-schema table and the implemented `schema.sql` as authoritative. The PDF's `CHECK start_time > end_time` is a typo; the valid rule is `end_time > start_time`. Keep `PROVIDER` as the stored role name even though the narrative calls providers "Admin".
2. **Database target:** Use PostgreSQL for the project target and eventual deployment. H2 remains the current local skeleton database only until the PostgreSQL driver, configuration, and test profile are added.
3. **Appointment terminology:** Use `BOOKED` for an active appointment and `CANCELLED` for a cancelled appointment. Do not use `CONFIRMED` in the M2 schema or API.
4. **Concurrency mechanism:** Use PostgreSQL pessimistic row locking with `SELECT ... FOR UPDATE` inside the booking transaction. Keep the database uniqueness backstop as an additional safety net.

## Resolved M2 decisions — 2026-10-07

1. **Isolation level: `READ COMMITTED`.** This is PostgreSQL's default and is
   sufficient when booking first locks the slot row, then validates its open
   state, inserts the appointment, and updates the slot before committing. A
   competing booking waits for the row lock, then observes the winner's
   committed state and returns `409`. `SERIALIZABLE` would add unnecessary
   serialization failures and retry complexity for this single-row reservation
   flow.
2. **Cancellation and rebooking: preserve history and allow rebooking.** Mark
   the appointment `CANCELLED`, clear its `active_slot_id`, return the slot to
   `OPEN`, and enforce one active booking with `UNIQUE (active_slot_id)`.
   *(Updated 2026-10-08: the PostgreSQL-only partial index was replaced by this
   portable key so runtime and tests share one `schema.sql`; a `CHECK` ties
   `active_slot_id = slot_id` to `BOOKED` and `NULL` to `CANCELLED`.)* This
   preserves customer history while preventing two active bookings.
3. **Completed appointments: derive `COMPLETED`.** Keep the stored status as
   `BOOKED` or `CANCELLED`; present a booked appointment whose start time is in
   the past as `COMPLETED` in history queries. This avoids a scheduler and
   prevents status drift.

## Resolved M2 decisions — 2026-10-08

1. **No booking retry.** With `READ COMMITTED` plus `SELECT ... FOR UPDATE`,
   a losing request blocks, then reads the winner's committed `BOOKED` state —
   its `409` is final, so a retry could never succeed. PostgreSQL raises no
   serialization failures at this level. A lock timeout or deadlock victim
   (`PessimisticLockingFailureException`) is mapped to a safe `409 "The slot is
   busy right now. Please try again."` so the client can retry by hand.
2. **Browsing lists only future slots.** `GET /slots` and the home count filter
   `start_time > CURRENT_TIMESTAMP`; a past `OPEN` slot can never be booked
   anyway (`409`), so listing it would only produce failures.
3. **Slot removal with history.** A provider's `DELETE /provider/slots/{id}`
   hard-deletes an open slot that no appointment ever referenced. If cancelled
   appointments still reference it, the slot is set to `CANCELLED` instead
   (foreign keys would reject a delete, and the customer's history must
   survive). Either way it disappears from browsing. Re-adding the same start
   time afterwards returns `409` because `UNIQUE (provider_id, start_time)` still
   holds.
4. **Customer-only appointment views.** `GET /appointments` requires a
   `CUSTOMER` session (`403` for providers, who use `GET /provider/appointments`).
5. **Session fixation.** Login invalidates any existing session and issues a
   new one before storing the identity. `GET /auth/session` returns the
   current identity so the UI can restore itself after a reload.
6. **Frontend origin.** The static client runs on its own port (5173), so
   `CorsConfig` allows that explicit origin with credentials (never `*`);
   override with `SHUTTLEFLOW_CORS_ORIGINS`.
7. **Seed data.** Seed slots use `CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL ...`
   so they are always in the future on boot; the expression is portable across
   PostgreSQL and H2.
