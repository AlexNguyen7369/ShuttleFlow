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
| **M1 (current)** | Read-only skeleton: layered Spring Boot app, schema, seed data, `GET /` and `GET /slots` |
| **M2** | Full booking flow: login, browse/filter/paginate slots, book/cancel appointments, provider slot management |
| **M3** | Mock booking-confirmation service, logging, health check, one operational metric |
| **M4** | Conversational + autonomous booking agents, RAG Q&A over facility/rules knowledge base |
| **Post-course** | Match tracking (`games`, `scores`, `shots` tables), head-to-head stats, ML/CV shot analysis |

## Tech stack

- **Backend:** Java 17, Spring Boot 3.3 (`spring-boot-starter-web` + `spring-boot-starter-jdbc`) — no ORM, raw SQL via `JdbcTemplate`
- **Database:** H2 (file-based) for local dev; schema targets standard SQL so Postgres is a drop-in swap later
- **Frontend:** React (planned — this repo is backend-only so far)
- **Architecture:** Front Controller (Spring's `DispatcherServlet`) routing to `@RestController` → `@Service` → `@Repository` → DTO layers

## Prerequisites

- JDK 17+
- Maven 3.9+

## Run

```bash
mvn spring-boot:run
```

App starts on `http://localhost:8080`. `schema.sql` and `seed.sql` reload on every startup (`spring.sql.init.mode=always`).

## Endpoints (current)

```bash
curl http://localhost:8080/
# {"appName":"ShuttleFlow","openSlotCount":5}

curl http://localhost:8080/slots
# [{"slotId":1,"providerName":"Court 3","serviceName":"Singles Court Rental","startTime":"2026-09-27T09:00:00","endTime":"2026-09-27T10:00:00","price":20.00}, ...]
```

## Project structure

```
src/main/java/com/shuttleflow/
  controller/   HomeController, SlotController — HTTP in, DTOs out
  service/      HomeService, SlotService — business rules
  repository/   SlotRepository — JdbcTemplate + RowMapper
  dto/          HomeDto, SlotDto
src/main/resources/
  schema.sql    users, providers, services, availability_slots, appointments — incl. double-booking UNIQUE constraints
  seed.sql      sample providers (Court 3, Coach Kim), services, open slots
```

`games`, `scores`, and `shots` — the tables behind head-to-head tracking — are intentionally left out of the M1 schema; they land once the booking core is solid.

See the Milestone 1 report for the full ER diagram, wireframes, and architecture writeup.
