# Decisions — Milestone 1

Source: `docs/CMPE 172 Milestone 1.pdf`, plus what is currently implemented in `ShuttleFlow/`. Where the two disagree it is flagged under **Open items**.

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
- PostgreSQL is the database named in the spec (see Open items: the skeleton runs on H2).
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
| Double-booking prevention | `UNIQUE (slot_id)` on `appointments`, enforced by the database | Holds even if two requests race; the service layer catches the constraint violation and returns "Court is already booked." |
| Pagination | SQL `LIMIT`/`OFFSET`, page size 10 | Spec requirement |
| Provider modelling | A provider is a user with at most one provider row (`providers.user_id`) | Spec: "each user is at most one provider" |
| Weak entities | None | Every entity has its own ID, so all are strong even when existence depends on another entity |

## Open items / discrepancies to resolve

1. **PostgreSQL vs H2.** Spec says PostgreSQL; `pom.xml` and `application.properties` use file-based H2. `README.md` calls H2 local-dev with Postgres as a later drop-in. Decide whether Milestone 1 is graded on Postgres.
2. **Spec typo in constraints.** The PDF's Justification says `CHECK start_time > end_time`; the relational schema table and `schema.sql` correctly use `end_time > start_time`. Treat the latter as correct.
3. **Spec typo in the double-booking paragraph.** "enforced now through java, but through the database" almost certainly means *not* through Java, but through the database. The implementation follows that reading.
4. **Role naming.** The roles table says "Admin" for coach/court manager, but the schema stores `PROVIDER`. Keep `PROVIDER` in the DB.
5. **Diagrams not captured.** The block diagram (page 2) and ER diagram (page 3) are images in the PDF; their content was not extracted here. Check them against `schema-notes.md`.
6. **Session type filter.** The spec filters by "open play / coaching" but the schema has no explicit session-type column; it is implied by `providers.type` (`COURT`/`COACH`) and `services.name`. **Resolved 2026-09-29:** filter on `providers.type` — `OPEN_PLAY` = `COURT`, `COACHING` = `COACH`; no schema change.
