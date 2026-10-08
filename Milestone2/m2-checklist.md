# Milestone 2 Scaffold Checklist

The assignment PDF was reviewed against this checklist on 2026-10-07. The
PDF is the grading authority; this file adds implementation sequencing and
records project-specific clarifications.

This checklist records the implementation evidence. A checked item means the
feature is implemented and covered by the code/tests/docs named in the
repository.

## Sequential implementation plan

Complete these steps in order so each feature has the database, identity, and
service-layer dependencies it needs before the next feature is added.

1. [x] **Freeze the remaining design decisions.** PostgreSQL is the selected
   project target; `BOOKED` and PostgreSQL row locking are selected. Confirm
   `READ COMMITTED`, derived `COMPLETED`, and cancellation/rebooking semantics
   are recorded in `docs/decisions.md` before schema migration.
2. [x] **Update the database foundation.** Migrate the runtime configuration
   to PostgreSQL, align `schema.sql` with the remaining decisions, add any
   decisions, add any booking version/state columns, preserve foreign keys and
   `UNIQUE (appointments.slot_id)`, and update `seed.sql` with valid BCrypt
   hashes and future test data.
3. [x] **Harden shared errors and validation.** Expand
   `ApiExceptionHandler`, define safe not-found/authorization/conflict
   exceptions, and establish consistent validation for IDs, dates, enums, and
   required fields before adding endpoint-specific behavior.
4. [x] **Implement authentication first.** Add the BCrypt dependency, build
   `UserRepository`, `AuthService`, `AuthController`, `UserSession`, and
   `SessionAuth`, then verify login, logout/session persistence, generic
   invalid-credential responses, and provider linkage.
5. [x] **Apply RBAC to the existing M1 browse path.** Keep public browsing
   behavior working, add any required service/date filters, and regression-test
   the existing SQL `LIMIT 10 OFFSET ...` pagination before protected features
   depend on it.
6. [x] **Implement the booking write path.** Build the slot read/reservation
   repository methods, `BookingRequest`, and customer booking service/controller
   flow. Validate open/future slots and return `201`, `400`, `401`, `403`,
   `404`, or `409` according to the contract.
7. [x] **Make booking transactional and race-safe.** Add the selected version
   check or row lock, create the appointment and update slot state in one
   transaction, catch database conflicts, and map them to a safe `409`.
8. [x] **Add customer appointment views.** Implement upcoming/history queries
   scoped to the authenticated customer, then add owner-only cancellation and
   atomic slot reavailability. Verify another customer cannot view or cancel
   those records.
9. [x] **Implement provider availability.** Add provider ownership queries,
   create/remove endpoints, future/time-range validation, duplicate handling,
   and rejection of removal when an active appointment exists.
10. [x] **Implement provider appointment viewing.** Return only booked
    appointments on the authenticated provider's slots, including the
    required customer fields and excluding cancelled records.
11. [x] **Build the UI against stable endpoints.** Add login, browse/filter,
    booking confirmation, customer appointments/cancellation, provider
    availability, and provider appointment screens after the API contracts are
    passing.
12. [x] **Verify each layer and the race condition.** Add service unit tests,
    endpoint/integration tests, then run the real two-customer same-slot test.
    Confirm exactly one success, one conflict, one active appointment, and
    consistent slot state.
13. [x] **Finish the submission artifacts.** Run `mvn test` and
    `mvn clean package`, update README/feature docs, complete the M2 report and
    code walkthrough, and confirm the clean-checkout definition of done.

## Foundation and architecture

- [x] Preserve the M1 flow: controller → service → repository → database.
- [x] Reserve DTOs for immutable request/response boundaries.
- [x] Reserve repositories as the only SQL/`JdbcTemplate` layer.
- [x] Reserve global handling for safe `400`, `401`, `403`, `404`, and `409` errors.
- [x] Resolve and record PostgreSQL as the project target; H2 is transitional local/test infrastructure.
- [x] Resolve and record `BOOKED` vs `CONFIRMED` terminology: use `BOOKED`.
- [x] Confirm derived `COMPLETED` status and cancellation/rebooking semantics.
- [x] Resolve and record PostgreSQL pessimistic row locking; confirm `READ COMMITTED` as the isolation level.

## Authentication and RBAC

- [x] Scaffold login controller, service, request/response DTOs, and session components.
- [x] Add BCrypt dependency and replace placeholder seed hashes.
- [x] Implement `POST /auth/login` and provider login behavior.
- [x] Establish server-side sessions without storing passwords.
- [x] Enforce `401` for unauthenticated and `403` for wrong-role requests.
- [x] Prevent account enumeration by making unknown-user and wrong-password failures equivalent.

## Customer workflow

- [x] Scaffold appointment controller/service/repository and booking DTO.
- [x] Keep M1 `GET /slots` filtering and SQL `LIMIT/OFFSET` pagination working.
- [x] Add required service/date filtering where selected by the API design.
- [x] Implement customer booking with `201` success and `409` conflict behavior.
- [x] Implement upcoming/history appointment views scoped to the caller.
- [x] Implement owner-only cancellation and safe slot reavailability.
- [x] Make booking and slot-state changes one transaction.

## Provider workflow

- [x] Scaffold provider controller/service/repository and availability DTO.
- [x] Implement owned-service availability creation.
- [x] Validate future start time and `endTime > startTime`.
- [x] Implement duplicate availability conflict handling.
- [x] Implement owned open-slot removal and reject removal with active appointments.
- [x] Implement provider appointment view scoped to the authenticated provider.

## Concurrency and data integrity

- [x] Reserve a dedicated real-database concurrency test location.
- [x] Preserve the active-slot uniqueness key as the database backstop.
- [x] Implement the selected pessimistic row lock strategy.
- [x] Catch database race/uniqueness failures and map them to safe `409` responses.
- [x] Prove exactly one success in a two-customer same-slot test.
- [x] Verify exactly one active appointment and consistent slot state after the race.

## UI and verification

- [x] Scaffold a frontend location and document its API connection.
- [x] Build home, login, browse/filter, booking, confirmation, and appointment screens.
- [x] Build provider availability and provider appointment screens.
- [x] Add endpoint/integration tests for all new routes.
- [x] Add service unit tests for validation, authorization, ownership, and conflicts.
- [x] Run `mvn test` and `mvn clean package` after implementation.
- [x] Update README, feature docs, decisions, report, and walkthrough to match code.
