# ShuttleFlow Agent Instructions

ShuttleFlow is a CMPE 172 badminton court and coach booking system. This file is the Codex equivalent of the repository's former `CLAUDE.md`; follow it for all repository work. The Milestone 2 requirements summarized below come from `CMPE172_Milestone2_Booking_and_Concurrency.pdf`.

## Repository and stack

- Run commands from this directory, the Maven project root.
- Java 17 and Spring Boot 3.3.4.
- Maven build; use `mvn test` for verification.
- JDBC through `JdbcTemplate`; do not add JPA, Hibernate, Spring Data, or another ORM.
- The current local database is file-based H2. Keep SQL portable and document any decision to move to PostgreSQL, which is the database named by the course specification.
- The backend uses `controller` → `service` → `repository` → database layering. A React/Angular SPA or Thymeleaf frontend is required for Milestone 2; keep frontend-to-backend flow clear and documented.

## Non-negotiable architecture rules

- Controllers translate HTTP requests and responses only. They must not contain SQL or business rules.
- Services own validation, authorization, booking/cancellation rules, transactions, and exception translation.
- Repositories are the only classes that contain SQL and database access. Use parameterized `?` placeholders only.
- Return immutable DTOs, never rows/entities or database credentials. Use constructor injection, final fields, getters, and no Lombok setters.
- Keep database names in `snake_case` and JSON properties in `camelCase`.
- Do not log or store plaintext passwords, session secrets, or other credentials. Never commit secrets or local database data.
- Preserve the schema entities `users`, `providers`, `services`, `availability_slots`, and `appointments` unless a Milestone 2 requirement requires a documented schema extension.
- Enforce important data rules in both the schema and service layer where appropriate.

## Milestone 2 acceptance requirements

Implement and test the complete core booking workflow:

### Web interface

- Provide home, available-slot browsing/filtering, booking form, and booking confirmation flows.
- Explain and preserve the request path: UI → Controller → Service → Repository → Database → response.
- Browse slots by provider, service, and/or date with SQL `LIMIT` and `OFFSET`; retain a page size of 10.

### Authentication and RBAC

- Support username/email and password login.
- Store passwords only as BCrypt hashes; never compare or persist plaintext values.
- Establish a server-side session at login. Read the role from the user's JDBC row.
- Support `CUSTOMER` and `PROVIDER` roles. Enforce provider-only and customer-only endpoints in the service/security boundary.
- Return `401` when unauthenticated and `403` for an authenticated user with the wrong role.
- Do not allow account enumeration: unknown users and wrong passwords have the same login response.
- OAuth/JWT are optional; the default implementation is session-based login.

### Required customer behavior

- Browse, filter, and paginate open slots.
- Book an open slot for a chosen service.
- View the caller's upcoming appointments and history; never return another user's appointments.
- Cancel only the caller's own upcoming appointment.
- Use the feature specifications in `docs/features/` as the endpoint-level contract, resolving conflicts in favor of the Milestone 2 PDF and documenting the resolution.

### Required provider behavior

- Create availability slots tied to one of the provider's own services.
- Remove the provider's own open slots, but reject removal when a confirmed/active appointment exists.
- View appointments booked on the provider's own slots, including the booking customer's name and email.
- Never expose appointments belonging to another provider.

### Statuses and validation

- Model appointment lifecycle as `BOOKED` and `CANCELLED`, with `COMPLETED` for past appointments where required by the Milestone 2 specification. Keep slot lifecycle states consistent with the database and API.
- Validate all request bodies, query parameters, IDs, dates, and time ranges.
- Reject past starts, invalid ranges, missing fields, unknown filters, and invalid IDs with appropriate `400`/`404` responses.
- Use global exception handling. Return standard status codes (`400`, `401`, `403`, `404`, `409`) and safe error bodies; never return stack traces.

## Booking transactions and concurrency

- Treat booking as one atomic transaction: validate/read the slot, reserve it, create the appointment, and update slot state together.
- Use `@Transactional` at the service boundary and explain the ACID properties for booking.
- Explicitly document the race: two users attempt to book the same slot simultaneously.
- Choose and document an isolation/concurrency strategy before implementing it. Prefer an optimistic version check for the current H2/portable setup; if PostgreSQL is adopted, a pessimistic `SELECT ... FOR UPDATE` strategy is also acceptable. Do not silently mix strategies.
- Keep `UNIQUE (slot_id)` on `appointments` as the database-level backstop. Never replace concurrency control with a Java check-then-insert.
- Catch the database conflict and return `409` with the contract's booking-conflict message.
- Add retry where the selected strategy requires it, with bounded and well-defined behavior.
- Cancellation must atomically update the appointment and make the slot available again when the feature contract permits rebooking.

## Testing requirements

- Add unit tests for service rules: booking, validation, owner-only cancellation, role checks, and conflict handling.
- Add endpoint/integration tests for every new route and meaningful error case.
- Add an automated two-thread (or equivalent concurrent) test that submits two booking attempts for the same slot and asserts exactly one succeeds and the other receives a conflict.
- Tests must be repeatable and must not depend on test order.
- Run `mvn test` from this directory after changes. Do not weaken, skip, or disable tests to make the build pass.

## Existing project conventions

- Package root: `com.shuttleflow`.
- Main packages: `controller`, `service`, `repository`, `dto`.
- SQL belongs in repository constants; map rows with `RowMapper`.
- Existing feature contracts are in `docs/features/`; read the relevant file before changing an endpoint.
- Keep `README.md` and relevant docs updated when routes, setup, schema, status values, or concurrency behavior change.
- Do not commit `data/`, `target/`, `context.md`, `definitions.md`, or `video_script.md`.

## Verification commands

```bash
mvn test
mvn clean package
mvn spring-boot:run
```

Before declaring Milestone 2 complete, verify the implementation, tests, report/code walkthrough material, and source package all cover authentication/RBAC, booking/cancellation, provider availability, transactions/ACID, the selected isolation/concurrency strategy, and the concurrent-booking proof.

