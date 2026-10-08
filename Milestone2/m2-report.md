# ShuttleFlow Milestone 2 Implementation Evidence

## Scope

Milestone 2 implements session login, customer and provider role boundaries,
slot browsing/filtering, customer booking and cancellation, provider
availability, provider appointment viewing, a browser workflow, and a
database-backed concurrency proof.

## Evidence map

| Requirement | Implementation evidence | Verification |
|---|---|---|
| PostgreSQL runtime / H2 tests | `src/main/resources/application.properties`, `schema.sql`, `src/test/resources/application.properties`, `schema-h2.sql` | Spring context tests and package build |
| BCrypt credentials | `PasswordConfig`, `seed.sql`, `SeedPasswordHashTest` | BCrypt fixture test |
| Login and sessions | `AuthController`, `AuthService`, `UserRepository`, `UserSession` | `AuthControllerIntegrationTest` |
| 401/403 RBAC | `SessionAuth`, `ApiExceptionHandler` | `SessionAuthTest`, endpoint tests |
| Browse/filter/page 10 | `SlotController`, `SlotService`, `SlotRepository` | `SlotControllerBrowseTest` |
| Booking transaction | `AppointmentService.book`, `AppointmentRepository`, `SlotRepository` | M2 endpoint tests |
| Race safety | `SELECT ... FOR UPDATE`, active-slot unique key | `ConcurrentBookingTest` |
| Customer history/cancellation | `AppointmentService`, `AppointmentController` | M2 endpoint tests |
| Provider availability | `ProviderService`, `ProviderController`, `ProviderRepository` | M2 endpoint tests |
| Provider appointment isolation | `findProviderAppointments` scoped by provider ID | M2 endpoint tests |
| Browser workflow | `frontend/index.html`, `frontend/src/app.js`, `frontend/src/styles.css` | JavaScript syntax check and manual browser run |
| Architecture/docs | `README.md`, `docs/features`, `docs/decisions.md`, `docs/schema-notes.md` | Checklist and walkthrough review |

## Commands

```bash
mvn test
mvn clean package
python3 -m http.server 5173 --directory frontend
```

The test command is the regression gate before committing or pushing.
