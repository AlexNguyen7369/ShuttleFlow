# Milestone 2 Scaffold Checklist

This checklist records what the scaffold prepares for. A checked item means
the folder/file responsibility is documented and reserved; it does not mean
the feature is implemented.

## Sequential implementation plan

Complete these steps in order so each feature has the database, identity, and
service-layer dependencies it needs before the next feature is added.

1. [ ] **Freeze the design decisions.** Choose H2 or PostgreSQL, settle
   `BOOKED` versus `CONFIRMED`, decide whether `COMPLETED` is derived or
   stored, choose the isolation level, and select optimistic version checking
   or pessimistic row locking. Record every choice in `docs/decisions.md`.
2. [ ] **Update the database foundation.** Align `schema.sql` with those
   decisions, add any booking version/state columns, preserve foreign keys and
   `UNIQUE (appointments.slot_id)`, and update `seed.sql` with valid BCrypt
   hashes and future test data.
3. [ ] **Harden shared errors and validation.** Expand
   `ApiExceptionHandler`, define safe not-found/authorization/conflict
   exceptions, and establish consistent validation for IDs, dates, enums, and
   required fields before adding endpoint-specific behavior.
4. [ ] **Implement authentication first.** Add the BCrypt dependency, build
   `UserRepository`, `AuthService`, `AuthController`, `UserSession`, and
   `SessionAuth`, then verify login, logout/session persistence, generic
   invalid-credential responses, and provider linkage.
5. [ ] **Apply RBAC to the existing M1 browse path.** Keep public browsing
   behavior working, add any required service/date filters, and regression-test
   the existing SQL `LIMIT 10 OFFSET ...` pagination before protected features
   depend on it.
6. [ ] **Implement the booking write path.** Build the slot read/reservation
   repository methods, `BookingRequest`, and customer booking service/controller
   flow. Validate open/future slots and return `201`, `400`, `401`, `403`,
   `404`, or `409` according to the contract.
7. [ ] **Make booking transactional and race-safe.** Add the selected version
   check or row lock, create the appointment and update slot state in one
   transaction, catch database conflicts, and map them to a safe `409`.
8. [ ] **Add customer appointment views.** Implement upcoming/history queries
   scoped to the authenticated customer, then add owner-only cancellation and
   atomic slot reavailability. Verify another customer cannot view or cancel
   those records.
9. [ ] **Implement provider availability.** Add provider ownership queries,
   create/remove endpoints, future/time-range validation, duplicate handling,
   and rejection of removal when an active appointment exists.
10. [ ] **Implement provider appointment viewing.** Return only confirmed/
    booked appointments on the authenticated provider's slots, including the
    required customer fields and excluding cancelled records.
11. [ ] **Build the UI against stable endpoints.** Add login, browse/filter,
    booking confirmation, customer appointments/cancellation, provider
    availability, and provider appointment screens after the API contracts are
    passing.
12. [ ] **Verify each layer and the race condition.** Add service unit tests,
    endpoint/integration tests, then run the real two-customer same-slot test.
    Confirm exactly one success, one conflict, one active appointment, and
    consistent slot state.
13. [ ] **Finish the submission artifacts.** Run `mvn test` and
    `mvn clean package`, update README/feature docs, complete the M2 report and
    code walkthrough, and confirm the clean-checkout definition of done.

## Foundation and architecture

- [x] Preserve the M1 flow: controller → service → repository → database.
- [x] Reserve DTOs for immutable request/response boundaries.
- [x] Reserve repositories as the only SQL/`JdbcTemplate` layer.
- [x] Reserve global handling for safe `400`, `401`, `403`, `404`, and `409` errors.
- [ ] Resolve and record H2 vs PostgreSQL in `docs/decisions.md`.
- [ ] Resolve and record `BOOKED` vs `CONFIRMED` terminology.
- [ ] Resolve and record derived vs persisted `COMPLETED` status.
- [ ] Resolve and record isolation level and optimistic/pessimistic concurrency strategy.

## Authentication and RBAC

- [x] Scaffold login controller, service, request/response DTOs, and session components.
- [ ] Add BCrypt dependency and replace placeholder seed hashes.
- [ ] Implement `POST /auth/login` and provider login behavior.
- [ ] Establish server-side sessions without storing passwords.
- [ ] Enforce `401` for unauthenticated and `403` for wrong-role requests.
- [ ] Prevent account enumeration by making unknown-user and wrong-password failures equivalent.

## Customer workflow

- [x] Scaffold appointment controller/service/repository and booking DTO.
- [ ] Keep M1 `GET /slots` filtering and SQL `LIMIT/OFFSET` pagination working.
- [ ] Add required service/date filtering where selected by the API design.
- [ ] Implement customer booking with `201` success and `409` conflict behavior.
- [ ] Implement upcoming/history appointment views scoped to the caller.
- [ ] Implement owner-only cancellation and safe slot reavailability.
- [ ] Make booking and slot-state changes one transaction.

## Provider workflow

- [x] Scaffold provider controller/service/repository and availability DTO.
- [ ] Implement owned-service availability creation.
- [ ] Validate future start time and `endTime > startTime`.
- [ ] Implement duplicate availability conflict handling.
- [ ] Implement owned open-slot removal and reject removal with active appointments.
- [ ] Implement provider appointment view scoped to the authenticated provider.

## Concurrency and data integrity

- [x] Reserve a dedicated real-database concurrency test location.
- [ ] Preserve `UNIQUE (appointments.slot_id)` as the database backstop.
- [ ] Implement the selected optimistic version check or row lock strategy.
- [ ] Catch database race/uniqueness failures and map them to safe `409` responses.
- [ ] Prove exactly one success in a two-customer same-slot test.
- [ ] Verify exactly one active appointment and consistent slot state after the race.

## UI and verification

- [x] Scaffold a frontend location and document its API connection.
- [ ] Build home, login, browse/filter, booking, confirmation, and appointment screens.
- [ ] Build provider availability and provider appointment screens.
- [ ] Add endpoint/integration tests for all new routes.
- [ ] Add service unit tests for validation, authorization, ownership, and conflicts.
- [ ] Run `mvn test` and `mvn clean package` after implementation.
- [ ] Update README, feature docs, decisions, report, and walkthrough to match code.
