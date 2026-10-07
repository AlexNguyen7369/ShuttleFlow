# Milestone 2 Definitions: ShuttleFlow

This document is the implementation map for Milestone 2. It explains the
folder structure, the responsibility of each file or software component, and
how the new booking system extends the Milestone 1 (M1) read-only skeleton.

`milestone2.md` is the requirements/checklist document. This file is the
code-oriented companion: it describes where a responsibility belongs and how
data should move through the application.

## Status legend

- **Existing/M1** — currently present in the repository.
- **M2 extension** — an existing M1 file that must be expanded.
- **M2 new** — a file or component to add as the feature is implemented.
- **Supporting** — documentation, configuration, or test material.

The M2 tree below is the target organization. Items marked “planned” are not
claimed to exist until they are implemented.

## Target folder structure

```text
ShuttleFlow/
├── Milestone2/
│   ├── milestone2.md                 # assignment requirements and checklist
│   └── m2definitions.md              # this file: implementation definitions
├── docs/
│   ├── decisions.md                  # architecture and unresolved decisions
│   ├── schema-notes.md               # database/ER notes
│   └── features/                     # endpoint contracts and acceptance tests
│       ├── 01-player-login.md
│       ├── 02-browse-and-filter-slots.md
│       ├── 03-book-a-slot.md
│       ├── 04-view-my-bookings.md
│       ├── 05-cancel-appointment.md
│       ├── 06-provider-login.md
│       ├── 07-manage-availability.md
│       └── 08-view-provider-appointments.md
├── pom.xml                           # Maven dependencies and build
├── README.md                         # setup, endpoints, and project overview
├── src/
│   ├── main/
│   │   ├── java/com/shuttleflow/
│   │   │   ├── ShuttleflowApplication.java
│   │   │   ├── controller/
│   │   │   │   ├── ApiExceptionHandler.java
│   │   │   │   ├── HomeController.java
│   │   │   │   ├── SlotController.java
│   │   │   │   ├── AuthController.java                 # M2 new
│   │   │   │   ├── AppointmentController.java          # M2 new
│   │   │   │   └── ProviderController.java             # M2 new
│   │   │   ├── service/
│   │   │   │   ├── InvalidRequestException.java
│   │   │   │   ├── HomeService.java
│   │   │   │   ├── SlotService.java
│   │   │   │   ├── AuthService.java                    # M2 new
│   │   │   │   ├── AppointmentService.java             # M2 new
│   │   │   │   ├── ProviderService.java                # M2 new
│   │   │   │   └── BookingConflictException.java       # M2 new
│   │   │   ├── repository/
│   │   │   │   ├── SlotRepository.java
│   │   │   │   ├── UserRepository.java                 # M2 new
│   │   │   │   ├── ProviderRepository.java             # M2 new
│   │   │   │   └── AppointmentRepository.java           # M2 new
│   │   │   ├── dto/
│   │   │   │   ├── HomeDto.java
│   │   │   │   ├── SlotDto.java
│   │   │   │   ├── LoginRequest.java                   # M2 new
│   │   │   │   ├── LoginResponse.java                  # M2 new
│   │   │   │   ├── AppointmentDto.java                 # M2 new
│   │   │   │   ├── BookingRequest.java                 # M2 new
│   │   │   │   ├── AvailabilityRequest.java            # M2 new
│   │   │   │   └── ProviderAppointmentDto.java         # M2 new
│   │   │   └── auth/                                   # M2 new
│   │   │       ├── UserSession.java
│   │   │       └── SessionAuth.java
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── schema.sql
│   │       └── seed.sql
│   └── test/java/com/shuttleflow/
│       ├── controller/                               # endpoint/integration tests
│       ├── service/                                  # business-rule unit tests
│       └── concurrency/                              # real two-request race test
└── frontend/                                         # M2 new, if React is selected
    ├── package.json
    └── src/
        ├── App.*
        ├── api/*
        ├── components/*
        └── pages/*
```

The exact frontend technology may be React, Angular, or Thymeleaf. The
backend package tree and request flow remain the same; only the `frontend/`
presentation layer changes.

## Application flow

Every M2 request follows the M1 layering rule:

```text
Browser/UI
   ↓ HTTP request
Controller → Service → Repository → PostgreSQL (H2 only during transition/tests)
   ↑ DTO response      ← SQL result
Browser/UI
```

- The **controller** owns HTTP concerns: routes, request binding, session
  extraction, and status codes.
- The **service** owns application rules: validation, role checks, ownership,
  transactions, and safe exception translation.
- The **repository** is the only layer that executes SQL through
  `JdbcTemplate`.
- A **DTO** is the deliberately limited input/output shape. It prevents
  password hashes, database rows, and session internals from crossing the API.
- The **database** enforces relationships, valid states, and the final
  uniqueness backstop against double booking.

## File and component definitions

### Project and documentation files

| File/component | Status | Role and interaction with M1 |
|---|---|---|
| `Milestone2/milestone2.md` | Existing M2 | Detailed assignment scope, endpoint rules, ACID requirements, concurrency strategy, tests, and definition of done. It is the source checklist for the implementation. |
| `Milestone2/m2definitions.md` | Existing M2 | This implementation glossary and file map. It explains the intended code ownership so new features do not bypass the M1 architecture. |
| `pom.xml` | M1 → M2 | Keeps Java 17, Spring Boot, Web, JDBC, and tests. M2 adds the PostgreSQL driver plus BCrypt/session support as needed; H2 may remain in a test profile during transition. It must not add JPA/Hibernate or Spring Data. |
| `README.md` | M1 → M2 | Documents how to run the app and the public API. M2 should add login, booking, cancellation, provider routes, session behavior, and test commands. |
| `docs/features/*.md` | Existing supporting | Each file is an endpoint contract and acceptance criteria. M2 code and tests should implement these contracts, with any PDF conflict recorded in `docs/decisions.md`. |
| `docs/decisions.md` | Existing supporting | Records architectural choices such as H2 vs PostgreSQL, `BOOKED` vs `CONFIRMED`, derived completion, isolation level, and concurrency control. |
| `docs/schema-notes.md` | Existing supporting | Explains the M1 relational model and is the reference for safe schema changes. |

### Application entry point and configuration

| File/component | Status | Role and interaction with M1 |
|---|---|---|
| `ShuttleflowApplication.java` | Existing/M1 | Spring Boot entry point. Component scanning discovers controllers, services, repositories, and the M2 authentication components below the package root. No booking logic belongs here. |
| `application.properties` | M1 → M2 | Configures the PostgreSQL datasource and SQL initialization. A separate test profile may retain H2 while migration work is completed; session behavior and BCrypt-related settings belong here only when needed. |
| `schema.sql` | M1 → M2 | Creates `users`, `providers`, `services`, `availability_slots`, and `appointments`. M2 must preserve foreign keys, valid status checks, provider/start uniqueness, and `UNIQUE (slot_id)` as the database backstop. An optimistic strategy may add a slot version column. |
| `seed.sql` | M1 → M2 | Supplies repeatable local/test data. M2 must replace placeholder passwords with BCrypt hashes and include usable customer/provider accounts without exposing plaintext passwords in code or logs. |

### Controllers: HTTP boundary

| File/component | Status | Role and interaction with M1 |
|---|---|---|
| `HomeController.java` | Existing/M1 | Handles `GET /` and delegates to `HomeService`. It remains thin; the home count automatically reflects repository data after M2 bookings/cancellations. |
| `SlotController.java` | Existing/M1 → M2 | Handles `GET /slots`. It retains M1 provider/session filters and SQL pagination, then adds service/date filters if required. It should not decide availability or execute SQL. |
| `ApiExceptionHandler.java` | Existing/M1 → M2 | Converts known exceptions and binding failures into safe JSON errors. M2 extends it for `401`, `403`, `404`, and `409`; it must never return SQL details, stack traces, or credentials. |
| `AuthController.java` | M2 new | Handles `POST /auth/login`, provider login, and optionally logout. It passes credentials to `AuthService`, establishes the server-side session, and returns a safe `LoginResponse`. |
| `AppointmentController.java` | M2 new | Handles customer booking, appointment listing, and cancellation: `POST /appointments`, `GET /appointments`, and `DELETE /appointments/{id}`. It delegates all authorization and state rules to `AppointmentService`. |
| `ProviderController.java` | M2 new | Handles provider availability creation/removal and provider appointment viewing. It obtains the authenticated identity from the session and delegates ownership checks to `ProviderService`. |

### Services: business and transaction layer

| File/component | Status | Role and interaction with M1 |
|---|---|---|
| `HomeService.java` | Existing/M1 | Calls `SlotRepository.countOpen()` and builds `HomeDto`. M2 keeps this service as the example of the thin controller/service/repository flow. |
| `SlotService.java` | Existing/M1 → M2 | Validates page/filter values and maps `OPEN_PLAY` to `COURT` and `COACHING` to `COACH`. M2 extends validation for new filters without moving SQL into the service. |
| `InvalidRequestException.java` | Existing/M1 → M2 | Represents invalid input and maps to `400`. It is for malformed/invalid requests, not authentication failures or booking races. |
| `AuthService.java` | M2 new | Loads a user by email, checks the supplied password against `password_hash` using BCrypt, rejects unknown/wrong credentials identically, verifies provider linkage, and creates the session identity. |
| `AppointmentService.java` | M2 new | Owns customer-only booking, appointment views, cancellation, ownership checks, status transitions, and the booking transaction. `@Transactional` belongs at this service boundary. |
| `ProviderService.java` | M2 new | Owns provider-only availability creation/removal and provider appointment views. It verifies that services and slots belong to the authenticated provider. |
| `BookingConflictException.java` | M2 new | Safe domain exception for an already-booked slot or concurrent reservation. The global handler maps it to `409` without exposing a database constraint message. |

### Repositories: SQL and database mapping

| File/component | Status | Role and interaction with M1 |
|---|---|---|
| `SlotRepository.java` | Existing/M1 → M2 | Uses parameterized `JdbcTemplate` SQL and `RowMapper` logic to count and browse open slots. M2 adds targeted reads/updates for reservation state only if they remain repository methods; it must keep `LIMIT 10 OFFSET ...`. |
| `UserRepository.java` | M2 new | Reads the user ID, BCrypt hash, full name, and role needed for login. It must never return or log a submitted plaintext password. |
| `ProviderRepository.java` | M2 new | Reads provider ownership, provider/service relationships, availability rows, and provider appointment projections. Its queries must scope results to the authenticated provider. |
| `AppointmentRepository.java` | M2 new | Inserts appointments, updates cancellation/slot state, lists customer/provider appointments, and performs the guarded booking update. It catches or exposes database conflict information for service translation. |
| `JdbcTemplate` | Existing/M1 technology | Spring's JDBC helper used only by repositories. It binds `?` parameters, executes SQL, and maps rows; it is not an ORM and does not replace service rules. |

### DTOs: API data contracts

| File/component | Status | Role and interaction with M1 |
|---|---|---|
| `HomeDto.java` | Existing/M1 | Public response containing app name and open-slot count. It remains credential-free. |
| `SlotDto.java` | Existing/M1 | Public slot response with ID, provider/service names, times, and price. M2 reuses it for booking selection and may add only documented display fields. |
| `LoginRequest.java` | M2 new | Input email and password for login. It is validated at the request/service boundary and never echoed back. |
| `LoginResponse.java` | M2 new | Safe login result such as user ID, role, and provider ID when applicable. It excludes password hashes and session secrets. |
| `BookingRequest.java` | M2 new | Input slot ID and optional service ID. It carries no authenticated user ID; the service gets that from the server-side session. |
| `AppointmentDto.java` | M2 new | Customer-facing appointment summary for upcoming/history views. It is filtered by the logged-in customer. |
| `AvailabilityRequest.java` | M2 new | Provider input containing service ID and start/end times. The service validates range, past dates, service ownership, and duplicate availability. |
| `ProviderAppointmentDto.java` | M2 new | Provider-facing appointment summary including slot/service details and the booking customer's name/email, scoped to that provider. |

### Authentication/session components

| File/component | Status | Role and interaction with M1 |
|---|---|---|
| `UserSession.java` | M2 new | Small server-side session value containing authenticated user ID, role, and provider ID when applicable. It contains no password. |
| `SessionAuth.java` | M2 new | Centralizes reading the session from an HTTP request and enforcing “authenticated”, “customer”, or “provider” access. It is the source of identity for services and prevents controllers from trusting caller-supplied user IDs. |
| Server-side HTTP session | M2 new | Created after successful login and retained across requests. Unauthenticated protected requests become `401`; authenticated wrong-role requests become `403`. |
| BCrypt password checker | M2 new dependency/component | Compares the submitted password with the stored BCrypt hash. It replaces M1's placeholder seed hashes and never stores plaintext credentials. |

### Tests

| Test area | Status | Role and interaction with M1 |
|---|---|---|
| `SlotControllerBrowseTest` | Existing/M1 | Integration coverage for the existing `GET /slots` contract, including filters, status filtering, ordering, and SQL pagination. It is the regression safety net for the M1 path. |
| Controller/integration tests | M2 new | Use MockMvc and a real application context to test login, sessions, `401`/`403`, endpoint responses, ownership isolation, and database effects. |
| Service unit tests | M2 new | Test validation, role/ownership rules, past-slot handling, cancellation, and exception translation without requiring HTTP. |
| Concurrency test | M2 new | Uses two distinct customers, a barrier/latch, and the real database transaction path to prove exactly one same-slot booking succeeds and the other returns `409`. It must also verify one active appointment and consistent slot state. |

## M1-to-M2 interaction map

| M1 capability | M2 extension | Compatibility rule |
|---|---|---|
| `GET /` → `HomeController` → `HomeService` → `SlotRepository` | The count reflects slots opened, booked, cancelled, or re-opened by M2 workflows. | Preserve the endpoint and DTO shape unless a documented change is needed. |
| `GET /slots` → `SlotController` → `SlotService` → `SlotRepository` | M2 uses the same browse path to select a slot before booking and adds required filters. | Keep only open slots visible and keep pagination in SQL. |
| `users`, `providers`, `services` seed data | Login and provider ownership use these same rows. | Preserve IDs/relationships in tests through lookups rather than hard-coding fragile assumptions. |
| `availability_slots.status = OPEN` | Booking changes the slot out of the open result; valid cancellation can make it available again according to the chosen state model. | Update appointment and slot state atomically. |
| `appointments.slot_id UNIQUE` | Two simultaneous customers compete for the same database guard. | Keep the constraint even when an optimistic version update or row lock is added. |
| M1 controller/service/repository/DTO layering | All M2 features use the same four-layer boundary plus session authentication. | Controllers stay thin; repositories remain the only SQL owners. |

## Booking and concurrency definition

The critical M2 race is:

```text
Customer A ─┐
             ├─ both attempt the same open slot
Customer B ─┘
       ↓
one transaction reserves/inserts successfully
the other transaction receives 409
database ends with exactly one active appointment
```

The implementation must select one strategy and record it in
`docs/decisions.md`:

1. **Optimistic version check:** update an open slot
   with `WHERE slot_id = ? AND status = 'OPEN' AND version = ?`. A zero-row
   update is a conflict, followed by the appointment insert only for the
   winner.
2. **Pessimistic row lock:** use PostgreSQL's `SELECT ... FOR UPDATE` inside
   the booking transaction when the project chooses the lock-based strategy.

In either case, `UNIQUE (slot_id)` remains the final database backstop. The
service catches a uniqueness/concurrency failure and returns a safe `409`.
Booking, slot-state change, and appointment creation are one transaction, so
failure rolls back all of them. This gives atomicity, consistency, isolation,
and durability as required by the M2 specification.

## Definition of complete integration

M2 is integrated with M1 when:

- the original home and slot browse behavior still passes its tests;
- a customer can log in, browse, book, view, and cancel through the same
  layered backend;
- a provider can log in, manage owned availability, and view owned bookings;
- sessions and role/ownership checks produce the required `401`/`403` results;
- schema constraints and a real concurrent test prove that a slot cannot be
  double booked; and
- README, feature contracts, decisions, and the final M2 report agree with
  the code and database terminology.
