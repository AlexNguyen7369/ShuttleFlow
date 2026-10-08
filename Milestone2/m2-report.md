# ShuttleFlow — Milestone 2 Report: Booking and Concurrency

Source material for the submitted report PDF. Every claim below is backed by
code and a test in this repository (verified 2026-10-08).

## 1. Scope

Milestone 2 turns the read-only M1 skeleton into a working booking system:
session login for customers and providers, role-based access control, slot
browsing with filters and SQL pagination, transactional booking, cancellation
with rebooking, provider availability management, provider appointment views,
a browser client, and an automated proof that two simultaneous bookings for
one slot cannot both succeed.

## 2. Request flow (UI → database → UI)

```text
Browser (frontend/src/app.js)  fetch + JSESSIONID cookie
  → DispatcherServlet (front controller) → CorsConfig
  → @RestController   bind JSON/params, pass HttpSession, choose status code
  → @Service          SessionAuth (401/403), validation (400), ownership (403),
                      business rules (404/409), @Transactional boundary
  → @Repository       parameterized SQL constants via JdbcTemplate + RowMapper
  → PostgreSQL        constraints: FKs, CHECKs, UNIQUE keys
  ← DTO (immutable)   serialized to camelCase JSON
  ← ApiExceptionHandler maps domain exceptions to {"error": "..."}
```

Example — "Confirm booking" in the dialog sends `POST /appointments
{"slotId":1}` → `AppointmentController.book` → `AppointmentService.book` →
`SlotRepository.findByIdForUpdate`, `AppointmentRepository.insert`,
`SlotRepository.updateStatus` → `201` with an `AppointmentDto`, which the
dialog renders as the confirmation (appointment number, time, status).

## 3. Authentication and RBAC

- **Passwords:** `users.password_hash` holds BCrypt hashes
  (`PasswordConfig` → `BCryptPasswordEncoder`). `AuthService` calls
  `passwordEncoder.matches`; no DTO, log line, or session ever holds a password.
- **No enumeration:** unknown email and wrong password both return
  `401 "Invalid email or password."`.
- **Sessions:** on success the old session is invalidated, a new one is issued
  (session-fixation defense), and an immutable `UserSession` (userId, role,
  providerId) is stored server-side. The role is loaded from the `users` row
  over JDBC; the provider ID from a `LEFT JOIN providers`.
- **Guards:** `SessionAuth.requireCustomer/requireProvider` → `401` with no
  session, `403` for the wrong role. Ownership checks compare
  `appointments.user_id` / `availability_slots.provider_id` with the session.
- **Provider login** additionally rejects customers and provider users with no
  `providers` row (`403`).

## 4. Features

| Feature | Endpoint | Key rules |
|---|---|---|
| Customer / provider login | `POST /auth/login`, `POST /auth/provider/login`, `GET /auth/session`, `POST /auth/logout` | BCrypt, generic 401, fresh session |
| Browse / filter / page | `GET /slots` | OPEN + future only; `providerId`, `sessionType`, `serviceId`, `date`; `LIMIT 10 OFFSET (page-1)*10` |
| Book | `POST /appointments` | customer only; 404 missing, 409 past/booked/race, 400 bad input |
| My bookings | `GET /appointments?view=upcoming\|history` | caller-scoped; `COMPLETED` derived for past bookings |
| Cancel | `DELETE /appointments/{id}` | owner only; 409 if past/already cancelled; slot reopens atomically |
| Provider services | `GET /provider/services` | own services for the availability form |
| Create availability | `POST /provider/slots` | own service (403), future start, `end > start` (400), duplicate start (409) |
| Remove availability | `DELETE /provider/slots/{id}` | owner only; 409 if booked; soft-cancel if history exists |
| Provider bookings | `GET /provider/appointments` | own slots only, BOOKED only, customer name + email |

## 5. The race condition

Two customers press **Book** on slot 7 at the same instant. Without control,
both transactions read `status = 'OPEN'`, both insert an appointment, and the
court is sold twice. ShuttleFlow prevents this in two independent layers.

### 5.1 Pessimistic row lock (primary control)

```java
@Transactional(isolation = Isolation.READ_COMMITTED)
public AppointmentDto book(BookingRequest request, HttpSession session) {
    UserSession user = sessionAuth.requireCustomer(session);
    ...
    SlotRecord slot = slotRepository.findByIdForUpdate(request.getSlotId())   // row lock
            .orElseThrow(() -> new NotFoundException("Slot not found."));
    if (slot.startTime().isBefore(LocalDateTime.now())) throw new ConflictException("This slot has already started.");
    if (!"OPEN".equals(slot.status())) throw new BookingConflictException();   // 409
    try {
        long appointmentId = appointmentRepository.insert(slot.slotId(), user.getUserId());
        if (slotRepository.updateStatus(slot.slotId(), "OPEN", "BOOKED") != 1) throw new BookingConflictException();
        ...
    } catch (DataIntegrityViolationException e) {
        throw new BookingConflictException();                                   // backstop → 409
    }
}
```

```sql
SELECT slot_id, provider_id, service_id, start_time, end_time, status
FROM availability_slots WHERE slot_id = ? FOR UPDATE;
```

Transaction B blocks on `FOR UPDATE` until A commits, then reads the committed
`BOOKED` status and returns `409 "Court is already booked."`.

### 5.2 Database uniqueness backstop

```sql
CREATE TABLE appointments (
    ...
    status          VARCHAR(20) NOT NULL DEFAULT 'BOOKED' CHECK (status IN ('BOOKED', 'CANCELLED')),
    active_slot_id  BIGINT,
    CONSTRAINT appointments_one_active_booking UNIQUE (active_slot_id),
    CONSTRAINT appointments_active_slot_matches CHECK (
        (status = 'BOOKED' AND active_slot_id = slot_id)
        OR (status = 'CANCELLED' AND active_slot_id IS NULL))
);
```

Even if a future code path forgot the lock, a second active booking for the
same slot violates `appointments_one_active_booking`; the service translates
that `DataIntegrityViolationException` to the same `409`. Cancelling sets
`active_slot_id = NULL`; because UNIQUE treats NULLs as distinct, cancelled
history accumulates while exactly one active booking is allowed.

## 6. ACID analysis

| Property | How booking satisfies it |
|---|---|
| Atomicity | Appointment insert and slot `OPEN → BOOKED` run in one `@Transactional` method; any exception rolls both back. Cancellation (appointment `CANCELLED` + slot `OPEN`) is likewise one transaction. |
| Consistency | FKs, `CHECK (end_time > start_time)`, status CHECKs, `UNIQUE (provider_id, start_time)`, `UNIQUE (active_slot_id)` and its CHECK keep every committed state valid. |
| Isolation | `READ COMMITTED` + `SELECT ... FOR UPDATE` serialises writers on the same slot; readers never see uncommitted bookings. |
| Durability | After commit PostgreSQL's WAL persists the booking; it is visible to every later request (verified by follow-up `GET`s in tests). |

## 7. Isolation level

- **Chosen:** `READ COMMITTED` (PostgreSQL's default, declared explicitly on
  `book` and `cancel`).
- **Why it suffices:** the only contended state is one slot row, and the row
  lock already serialises the read-check-write sequence on it. Under READ
  COMMITTED each statement sees the latest committed data, so the waiting
  transaction re-reads the slot after the lock is released and sees `BOOKED`.
- **Anomalies:** dirty reads are prevented. Non-repeatable reads and lost
  updates on the slot are prevented for bookers by the lock. Phantoms are
  possible in general but irrelevant: booking touches one known row.
- **Why not SERIALIZABLE:** it would add serialization failures that need a
  retry loop without making this single-row reservation any safer.
- **H2 vs PostgreSQL:** both support `FOR UPDATE` and READ COMMITTED with the
  same SQL. The test suite runs on H2; `PostgresConcurrentBookingTest` re-runs
  the race on a real PostgreSQL server to remove any doubt.

## 8. Retry behaviour

No automatic retry. A losing request's outcome is final (the slot is taken),
so retrying can never succeed. Validation, authorization and not-found errors
are never retried. A lock timeout/deadlock victim
(`PessimisticLockingFailureException`) returns
`409 "The slot is busy right now. Please try again."` so the user can retry.

## 9. Tests and results

Command: `mvn test` (H2) and
`SHUTTLEFLOW_PG_TEST_URL=jdbc:postgresql://localhost:5432/shuttleflow_test mvn test`.
Result on 2026-10-08: **78 tests, 0 failures** on JDK 17 (68 on H2 + 10
PostgreSQL race rounds); 68 passed with the PostgreSQL class skipped on JDK 27;
`mvn clean package` succeeds.

| Test class | Tests | What it proves |
|---|---|---|
| `BookingServiceUnitTest` | 12 | Service rules with mocked repositories: valid booking, 401/403 roles, 400/404/409 slot states, constraint violation → `BookingConflictException`, owner-only cancel, provider ownership, duplicate → 409, soft-cancel vs delete |
| `M2ServiceRulesTest` | 3 | Same rules against the real schema |
| `SlotControllerBrowseTest` | 22 | Filters (provider, session type, service, date), ordering, `LIMIT/OFFSET`, future-only, `400`s |
| `AuthControllerIntegrationTest` | 9 | Login equivalence, provider linkage, session restore/logout, session fixation, CORS, 404/405 mapping |
| `M2EndpointIntegrationTest` | 16 | Every booking/provider route: statuses `201/204/400/401/403/404/409` plus database effects and customer/provider isolation |
| `SessionAuthTest`, `SeedPasswordHashTest` | 5 | Guards; seed hashes are BCrypt |
| `ConcurrentBookingTest` | 1 | Two customers released together by a `CountDownLatch`: one success, one conflict, one active row, slot `BOOKED` |
| `PostgresConcurrentBookingTest` | 10 | 8 threads × 10 rounds on PostgreSQL: exactly 1 success and 7 conflicts per round, one active appointment, consistent slot |

Manual end-to-end checks on PostgreSQL (2026-10-08): a 37-request `curl`
walkthrough of every route and error status, and a headless-Chrome run of the
UI (filter, login failure/success, booking dialog + confirmation, reload
restoring the session, cancellation into history, provider create/duplicate/
remove), all with no console errors.

## 10. Evidence map

| Requirement | Implementation | Verification |
|---|---|---|
| PostgreSQL runtime, H2 tests, one schema | `application.properties`, `schema.sql`, `src/test/resources/application.properties` | context tests, PG race test, live run |
| BCrypt credentials | `PasswordConfig`, `seed.sql` | `SeedPasswordHashTest` |
| Login, sessions, fixation | `AuthController`, `AuthService`, `UserRepository`, `UserSession` | `AuthControllerIntegrationTest` |
| 401/403 RBAC + ownership | `SessionAuth`, services, `ApiExceptionHandler` | `SessionAuthTest`, endpoint + unit tests |
| Browse/filter/page 10 | `SlotController`, `SlotService`, `SlotRepository` | `SlotControllerBrowseTest` |
| Booking transaction | `AppointmentService.book` | unit + endpoint tests |
| Race safety | `FOR UPDATE`, `UNIQUE (active_slot_id)` | `ConcurrentBookingTest`, `PostgresConcurrentBookingTest` |
| History / cancellation / rebooking | `AppointmentService`, `AppointmentRepository` | endpoint tests |
| Provider availability + services | `ProviderService`, `ProviderController`, `ProviderRepository` | endpoint + unit tests |
| Provider appointment isolation | `findProviderAppointments` scoped by provider ID | endpoint tests |
| Browser workflow | `frontend/index.html`, `frontend/src/app.js`, `CorsConfig` | headless-Chrome run |

## 11. Still to do by hand before submission

- Export this report to PDF with screenshots of the UI.
- Record the ≥ 5-minute code walkthrough (script: `video_script2.md`).
- Zip as `CMPE172_Milestone2_FirstName_LastName.zip` and submit with the
  GitHub and video links.
