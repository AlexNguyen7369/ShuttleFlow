# ShuttleFlow Milestone 2 Voice Script

## Opening

ShuttleFlow Milestone 2 turns the Milestone 1 read-only badminton availability
browser into a complete booking system. The implementation keeps the required
path clear: the browser sends JSON to a controller, the controller delegates to
a service, the service validates and authorizes the action, the repository runs
parameterized JDBC SQL, and the database enforces the final integrity rules.

## 1. Foundation and architecture

The backend remains Java 17 with Spring Boot 3.3.4. We use Spring Web for REST
controllers and Spring JDBC for `JdbcTemplate` repositories. There is no JPA,
Hibernate, Spring Data, or ORM. Controllers translate HTTP requests and return
DTOs. Services own business rules, authorization, and transactions. Repositories
are the only classes that contain SQL.

PostgreSQL is the runtime target, configured through environment variables. H2
is isolated to the repeatable test profile. The original entities remain in
place: users, providers, services, availability slots, and appointments.
Appointment status is stored as `BOOKED` or `CANCELLED`; `COMPLETED` is derived
when a booked appointment is read after its start time.

## 2. Authentication and role-based access

The login flow begins at `POST /auth/login` or
`POST /auth/provider/login`. `AuthController` passes the request to
`AuthService`. `UserRepository` loads the email, BCrypt hash, role, and linked
provider ID. The service compares the supplied password with BCrypt and never
stores or returns the plaintext password.

Unknown email and wrong password use the same `401` response, so the endpoint
does not reveal whether an account exists. Provider login additionally requires
both the `PROVIDER` role and a provider row.

After login, the server stores an immutable `UserSession` in the server-side
HTTP session. `SessionAuth` supplies reusable customer and provider guards. A
missing session produces `401`; an authenticated user with the wrong role
produces `403`. Logout invalidates the session.

## 3. Shared validation and errors

`ApiExceptionHandler` translates domain and binding failures into safe JSON.
Invalid input maps to `400`, unauthenticated access to `401`, wrong roles or
ownership to `403`, missing records to `404`, booking/state conflicts to `409`,
and unexpected failures to a generic `500`. SQL messages, stack traces,
passwords, and session internals are not exposed.

The services validate positive IDs, required fields, allowed appointment views,
future start times, time ranges, provider ownership, and state transitions.

## 4. Browsing and filtering

`GET /slots` remains public and preserves the Milestone 1 behavior: SQL uses a
page size of 10 with `LIMIT` and `OFFSET`, ordered by start time and slot ID.
Filters can combine by provider, provider session type, service, and date.
Only `OPEN` slots are returned. The browser's Availability screen calls this
endpoint, renders the cards, and provides pagination controls.

## 5. Customer booking

A customer selects an open slot and submits `POST /appointments` with its
`slotId`. `AppointmentController` delegates to `AppointmentService`, which
requires a customer session and validates the request.

Booking is one atomic transaction. The service locks the selected slot using
PostgreSQL's `SELECT ... FOR UPDATE` strategy under `READ COMMITTED`, checks
that it exists, is open, and starts in the future, inserts the appointment, and
changes the slot to `BOOKED`. The response is `201` with a safe appointment
DTO.

The database also enforces one active booking. The appointment keeps an
`active_slot_id` only while it is booked; PostgreSQL uses a partial unique index
and H2 uses a compatible unique index. This preserves cancelled history while
allowing rebooking.

## 6. Concurrency proof

The race test starts two customer threads at the same time against one future
slot. Both attempts reach the booking service, but the first transaction gets
the row lock and commits the appointment and slot update. The second waits,
then observes that the slot is no longer open and receives the safe conflict
`409`, with the message `Court is already booked.`

The automated test verifies exactly one successful appointment, exactly one
conflict, exactly one active appointment, and a final `BOOKED` slot state.

## 7. Customer appointments and cancellation

`GET /appointments?view=upcoming` returns only the signed-in customer's future
booked appointments in soonest-first order. `view=history` returns that same
customer's past and cancelled appointments in reverse chronological order.
No caller-supplied user ID is accepted.

`DELETE /appointments/{id}` locks the appointment, checks ownership and the
future start time, marks it `CANCELLED`, clears its active booking key, and
reopens the slot in the same transaction. A different customer gets `403`, a
missing appointment gets `404`, and a past or already cancelled appointment
gets `409`.

## 8. Provider availability and appointments

Providers create availability with `POST /provider/slots`, supplying a service
ID and start/end times. The service checks that the service exists and belongs
to the logged-in provider, that the range is valid, and that the start is in
the future. The database's provider/start uniqueness rule is translated into
a safe `409`.

Providers remove their own open slots with `DELETE /provider/slots/{id}`.
Booked slots cannot be removed, and another provider cannot access the slot.

`GET /provider/appointments` returns only active bookings on the authenticated
provider's slots. It includes the booking customer's name and email, while
cancelled appointments and other providers' appointments are excluded.

## 9. Browser workflow

The dependency-free frontend in `frontend/` contains the home screen, login
screen, browse/filter screen, booking action, appointments and history screen,
cancellation action, provider slot creation/removal, and provider appointment
view. It sends session cookies with `credentials: include` and uses the same
REST contracts as the backend.

Run the API with `mvn spring-boot:run` and serve the UI with:

```bash
python3 -m http.server 5173 --directory frontend
```

## 10. Verification and handoff

The regression suite covers seed password hashes, login/session behavior,
global errors, browse pagination and filters, customer/provider authorization,
booking and cancellation, provider availability, provider appointment
isolation, and the concurrent same-slot race. The final handoff runs:

```bash
mvn test
mvn clean package
```

The dashboard Changes & History tab records the plain-English implementation
summary. Its historical baseline is stored in
`tools/dashboard/data/implemented.json`; completed future M2 tasks are appended
automatically from the dashboard task board.

## Closing

Milestone 2 is complete when the tests and package command are green, the
checklist is checked, the browser flows are available, and the code walkthrough
can follow every request from UI to controller, service, repository, database,
and response.
