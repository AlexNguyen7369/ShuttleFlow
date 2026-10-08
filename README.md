# ShuttleFlow (Head2Head)

**ShuttleFlow** is a badminton platform that starts as a court and coach booking system and grows into full head-to-head match tracking — live scores, shot data, and player stats — with an AI layer on top.

## Vision

Badminton players and coaches currently juggle group chats and spreadsheets to book courts and lessons, and have no structured record of games played against each other. ShuttleFlow closes both gaps in stages:

1. **Book** — players browse and reserve court time or coaching sessions; providers (courts, coaches) publish availability.
2. **Track** — bookings extend into logged matches: scores, sets, and head-to-head records between players.
3. **Analyze** — shot-level data and computer vision turn logged matches into real stats (shot placement, rally length, win patterns).
4. **Assist** — a conversational agent books slots for you ("book me singles Saturday afternoon"), an autonomous agent fills cancelled slots from a waitlist, and a grounded Q&A assistant answers rules/facility questions from a small knowledge base.

This repo currently implements stage 1, built as the required project for CMPE 172 (Enterprise Software Platforms).

## Roadmap

| Milestone | Scope |
|---|---|
| **M1** | Read-only skeleton: layered Spring Boot app, schema, seed data, `GET /` and `GET /slots` |
| **M2** | Full booking flow: login, browse/filter/paginate slots, book/cancel appointments, provider slot management |
| **M3** | Mock booking-confirmation service, logging, health check, one operational metric |
| **M4** | Conversational + autonomous booking agents, RAG Q&A over facility/rules knowledge base |
| **Post-course** | Match tracking (`games`, `scores`, `shots` tables), head-to-head stats, ML/CV shot analysis |

## Tech stack

- **Backend:** Java 17, Spring Boot 3.3 (`spring-boot-starter-web` + `spring-boot-starter-jdbc`) — no ORM, raw SQL via `JdbcTemplate`
- **Database:** PostgreSQL at runtime; H2 is test-only infrastructure with an in-memory test profile
- **Frontend:** dependency-free static HTML/CSS/JavaScript under `frontend/`
- **Architecture:** Front Controller (Spring's `DispatcherServlet`) routing to `@RestController` → `@Service` → `@Repository` → DTO layers

## Prerequisites

- JDK 17+
- Maven 3.9+

## Run

```bash
mvn spring-boot:run
```

App starts on `http://localhost:8080`. Set `DB_HOST`, `DB_PORT`, `DB_NAME`,
`DB_USERNAME`, and `DB_PASSWORD` (or `SPRING_DATASOURCE_URL`) before running.
`schema.sql` and `seed.sql` reload on every startup (`spring.sql.init.mode=always`).

The seeded local test accounts use BCrypt hashes:

| Account | Role | Test password |
|---|---|---|
| `court.manager@shuttleflow.com` | PROVIDER | `court-manager-test` |
| `coach.kim@shuttleflow.com` | PROVIDER | `coach-kim-test` |
| `alex@shuttleflow.com` | CUSTOMER | `alex-test` |
| `jamie@shuttleflow.com` | CUSTOMER | `jamie-test` |

These passwords are for local/test fixtures only, not deployment credentials.

## Endpoints (current)

```bash
curl http://localhost:8080/
# {"appName":"ShuttleFlow","openSlotCount":5}

curl http://localhost:8080/slots
# [{"slotId":1,"providerName":"Court 3","serviceName":"Singles Court Rental","startTime":"2026-09-27T09:00:00","endTime":"2026-09-27T10:00:00","price":20.00}, ...]
```

### `GET /slots`

Open slots ordered by `startTime`, 10 per page (SQL `LIMIT`/`OFFSET`).

| Param | Default | Meaning |
|---|---|---|
| `page` | `1` | 1-based page number |
| `providerId` | — | Only slots for this provider (coach openings) |
| `sessionType` | — | `OPEN_PLAY` (court providers) or `COACHING` (coach providers) |

Filters combine with each other and with `page`. A page past the last returns `200` with `[]`. `page` below 1 or not a number, or an unknown `sessionType`, returns `400` with `{"error": "..."}`.

```bash
curl "http://localhost:8080/slots?sessionType=COACHING&page=1"
```

### Authentication

`POST /auth/login` accepts `{ "email": "...", "password": "..." }` and creates a server-side session. `POST /auth/provider/login` applies the same flow but requires a linked `PROVIDER` account. Invalid credentials always return `401` with the same generic message; missing credentials or malformed JSON return `400`. A customer using provider-only service boundaries receives `403`. `POST /auth/logout` invalidates the session.

Authentication is layered as UI/API → `AuthController` → `AuthService` → `UserRepository` → database. `SessionAuth` provides shared `401`/`403` checks for customer and provider services. Password hashes are compared with BCrypt and never returned.

### Customer and provider booking endpoints

- `POST /appointments` — customer-only booking; returns `201` with a `BOOKED` appointment.
- `GET /appointments?view=upcoming|history` — caller-scoped appointment views; completed status is derived for past booked appointments.
- `DELETE /appointments/{id}` — owner-only cancellation; releases the slot and preserves history.
- `POST /provider/slots` — provider-only availability creation for an owned service.
- `DELETE /provider/slots/{id}` — provider-only removal of an open owned slot.
- `GET /provider/appointments` — provider-scoped booked appointments with customer name and email.

Booking is one `READ COMMITTED` transaction: the service locks the slot with
`SELECT ... FOR UPDATE`, validates its state, inserts the appointment, and
marks the slot `BOOKED`. A database active-slot uniqueness key remains the
backstop, and a competing request receives `409 {"error":"Court is already booked."}`.

The browser workflow is in `frontend/index.html`: home, login, available-slot
filters, booking, confirmation through the booking list, cancellation, and
provider availability/appointment views. Serve it locally with
`python3 -m http.server 5173 --directory frontend` while the Spring API runs
on port 8080.

## Project structure

```
src/main/java/com/shuttleflow/
  controller/   HTTP boundary and global error handling
  service/      validation, authorization, booking rules, and transactions
  repository/   parameterized JdbcTemplate SQL and row mapping
  dto/          immutable API request/response objects
  auth/         server-side identity and role guards
src/main/resources/
  schema.sql    users, providers, services, availability_slots, appointments — incl. double-booking UNIQUE constraints
  seed.sql      sample providers (Court 3, Coach Kim), services, open slots
```

`games`, `scores`, and `shots` — the tables behind head-to-head tracking — are intentionally left out of the M1 schema; they land once the booking core is solid.

See the Milestone 1 report for the full ER diagram, wireframes, and architecture writeup.
