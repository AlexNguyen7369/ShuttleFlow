# ShuttleFlow

Badminton court and coach booking system (CMPE 172 project). Requirements: `docs/CMPE 172 Milestone 1.pdf`; distilled in `docs/decisions.md` and `docs/schema-notes.md`.

## Stack

- Java 17, Spring Boot 3.3.4 (`spring-boot-starter-web`, `spring-boot-starter-jdbc`), Maven
- SQL via `JdbcTemplate` — no ORM
- H2 file DB (`./data/shuttleflow`) for now; the spec names PostgreSQL, so keep SQL standard/portable
- Frontend: React planned, none yet

## Hard rules

- **No ORM.** No JPA/Hibernate/Spring Data. Raw SQL through `JdbcTemplate` only.
- **Layering is strict:** `controller` → `service` → `repository` → DB. Controllers hold no logic; only repositories touch SQL; controllers return DTOs, never rows/entities.
- **Double-booking is prevented by the database:** `UNIQUE (slot_id)` on `appointments` stays. The service layer catches the constraint violation and returns "Court is already booked." Never replace it with a check-then-insert in Java.
- **Pagination uses SQL `LIMIT` + `OFFSET`, 10 per page.**
- Keep the schema aligned with the spec tables (`users`, `providers`, `services`, `availability_slots`, `appointments`) and their constraints; don't rename them.
- Roles are `CUSTOMER` and `PROVIDER` in the DB.
- Use parameterized queries (`?`) — never concatenate user input into SQL.
- Never store or log plaintext passwords; seed hashes are placeholders only.
- Don't commit `data/`, `target/`, `context.md`, `definitions.md`, `video_script.md` (already gitignored).

## Package layout

The Maven project is in the nested `ShuttleFlow/` directory (it has its own `.git`). Run Maven from there.

```
docs/                                   requirements PDF + decisions.md, schema-notes.md
ShuttleFlow/
├── pom.xml
├── src/main/java/com/shuttleflow/
│   ├── ShuttleflowApplication.java     entry point
│   ├── controller/                     @RestController — HTTP in, DTO out (HomeController, SlotController)
│   ├── service/                        @Service — business rules (HomeService, SlotService)
│   ├── repository/                     @Repository — JdbcTemplate + RowMapper (SlotRepository)
│   └── dto/                            immutable response types (HomeDto, SlotDto)
└── src/main/resources/
    ├── application.properties          datasource + sql.init
    ├── schema.sql                      DDL, drops and recreates tables on every boot
    └── seed.sql                        sample data
```

## Build and test

Run from `ShuttleFlow/ShuttleFlow/`:

```bash
mvn spring-boot:run        # start on http://localhost:8080
mvn clean package          # build the jar into target/
mvn test                   # run tests (none exist yet — src/test is empty/absent)
```

Requires JDK 17+ and Maven 3.9+. Smoke check: `curl localhost:8080/` and `curl localhost:8080/slots`.

`schema.sql` and `seed.sql` reload on every startup (`spring.sql.init.mode=always`), so local data resets on restart.

## Conventions

- Package root `com.shuttleflow`; one class per file; classes named by role (`XxxController`, `XxxService`, `XxxRepository`, `XxxDto`).
- DTOs: immutable — final fields, constructor + getters, no setters, no Lombok.
- Constructor injection; no field `@Autowired`.
- SQL lives as constants in the repository class; map rows with `RowMapper`.
- Database naming: `snake_case` tables/columns, `<table>_id` primary keys, statuses as uppercase strings guarded by `CHECK`.
- JSON fields are camelCase (`slotId`, `providerName`, `startTime`).
- Enforce data rules in the schema (constraints) as well as in code.
- Add tests under `src/test/java/com/shuttleflow/` mirroring the main package.

## Progress tracking (required after every commit or push)
After making a commit or push in this repo, invoke the global
`progress-tracker` skill before touching `current_progress.md` — it is
the authoritative, strict spec for how that file is created (if
missing) and updated (Completed appends, What's Next replacement,
formatting, attribution). Do not improvise the update from memory or
from this summary; invoke the skill every time this rule fires.
