---
name: add-endpoint
description: Step-by-step TDD checklist for adding a new ShuttleFlow API endpoint (controller → service → repository → DTO, tests, README, reviewer loop). Use when adding or implementing a new API endpoint or any feature from docs/features/.
---

# Add an endpoint

Work through every step in order. Don't skip ahead. `CLAUDE.md` wins on any conflict.

## 1. Confirm the feature file
- [ ] Find the `docs/features/NN-*.md` this endpoint implements and read it in full.
- [ ] Confirm the spec: route, HTTP method, request (path/query/body), response shape, rules, acceptance criteria and status codes.
- [ ] No feature file, or spec ambiguous/contradicts `docs/decisions.md` / `docs/schema-notes.md`? **Stop and ask the user** before continuing.

## 2. Write failing tests first
- [ ] Delegate to the `test-writer` subagent, passing the feature file path. No production code before this.

## 3. Confirm red
- [ ] Run `mvn test` from `ShuttleFlow/ShuttleFlow/`.
- [ ] New tests FAIL for the right reason: missing endpoint/behavior (404/405, wrong status/body, missing DB effect).
- [ ] Not for the wrong reason (compile errors, typos, setup/context failures). If any pass or fail wrongly, **don't proceed**; fix the tests (via `test-writer`) first.

## 4. DTO (`dto/`)
- [ ] Create an immutable DTO class (final fields, constructor + getters, no setters, no Lombok, no records) exposing only what the client needs Match SlotDTO
- [ ] NEVER expose `password_hash` or any credential/internal column.
- [ ] camelCase JSON fields (`slotId`, `providerName`, `startTime`); controllers never return rows/entities.


## 5. Repository (`repository/`)
- [ ] Only layer that touches SQL: `JdbcTemplate` + `RowMapper`; SQL as `private static final` constants in the class.
- [ ] Parameterized queries (`?`) only; never concatenate input.
- [ ] Portable, standard SQL (H2 now, PostgreSQL later).
- [ ] Pagination via SQL `LIMIT` + `OFFSET`, 10 per page; never slice in memory.
- [ ] Select only needed columns; never `password_hash` unless authenticating.
- [ ] No ORM/JPA/Spring Data.
- [ ] Schema/constraint changes go in `schema.sql`: keep spec table names, roles `CUSTOMER`/`PROVIDER`, `CHECK`-guarded uppercase statuses. Update `seed.sql` if needed.

## 6. Service (`service/`)
- [ ] Business rules and input validation live here.
- [ ] Ownership/authorization: a customer may only view/cancel their own appointments; a provider may only manage their own slots/appointments.
- [ ] Handle not-found (404) and forbidden (403) explicitly.
- [ ] Never log or store plaintext passwords.

## 7. Double-booking guard (anything that creates appointments)
- [ ] Rely on the DB `UNIQUE (slot_id)` on `appointments`; keep the constraint.
- [ ] Service catches `DuplicateKeyException` / `DataIntegrityViolationException` and returns "Court is already booked." (409).
- [ ] Never a check-then-insert in Java.

## 8. Controller (`controller/`)
- [ ] Thin: parse/validate params, delegate to the service, return a DTO. No logic, no SQL.
- [ ] Constructor injection only (no field `@Autowired`).
- [ ] Correct HTTP status codes per the spec (200/201/204/400/401/403/404/409); camelCase JSON.

## 9. Test and verify
- [ ] Run the full `mvn test`; ALL existing and new tests pass.
- [ ] Optional smoke check: `mvn spring-boot:run`, then `curl` the new endpoint.

## 10. README
- [ ] Update `README.md`: method, path, params, response shape, error cases.

## 11. Reviewer loop
- [ ] Run the `reviewer` subagent on the changes.
- [ ] Fix everything it flags, re-run `mvn test`, then re-run `reviewer`.
- [ ] **Keep looping until it returns PASS.** Do not stop at NEEDS CHANGES.

## 12. Definition of done
- [ ] Tests green, reviewer PASS, README updated.
- [ ] Final written summary to the user:
  - Files changed, grouped by layer (dto / repository / service / controller / schema / tests / docs)
  - Endpoint contract (method, path, request, response, status codes)
  - Tests added (criterion → test)
  - Reviewer verdict, plus any open questions
