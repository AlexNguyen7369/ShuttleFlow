# Milestone 2 Scaffold Checklist

This checklist records what the scaffold prepares for. A checked item means
the folder/file responsibility is documented and reserved; it does not mean
the feature is implemented.

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
