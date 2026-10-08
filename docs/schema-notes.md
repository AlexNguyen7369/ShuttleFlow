# Schema notes — Milestone 1

Source: relational schema and justification pages of `docs/CMPE 172 Milestone 1.pdf`, compared with `src/main/resources/schema.sql`.

## Contract decision — 2026-10-07

The relational-schema table and `schema.sql` are the authoritative Milestone 1
contract. The PDF's reversed time check is treated as a typo: a slot must have
`end_time > start_time`. Double-booking protection is a database concern,
implemented by a database uniqueness guard; application code should translate
the resulting conflict rather than replace the constraint with a check-then-
insert race.

For Milestone 2, one `schema.sql` serves both PostgreSQL (runtime) and H2
(tests). The double-booking guard is `UNIQUE (active_slot_id)` on
`appointments`: `active_slot_id` equals `slot_id` while an appointment is
`BOOKED` and is `NULL` once `CANCELLED` (enforced by the
`appointments_active_slot_matches` CHECK). UNIQUE treats NULLs as distinct, so
cancelled history is kept and the released slot can be booked again.

## Tables

| Table | Columns | Keys & constraints |
|---|---|---|
| `users` | `user_id`, `email`, `password_hash`, `full_name`, `role`, `created_at` | PK `user_id`; `email` UNIQUE; `role` IN (`CUSTOMER`, `PROVIDER`) |
| `providers` | `provider_id`, `user_id`, `name`, `type`, `location` | PK `provider_id`; FK `user_id` → `users`; `type` IN (`COURT`, `COACH`) |
| `services` | `service_id`, `provider_id`, `name`, `duration_min`, `price` | PK `service_id`; FK `provider_id` → `providers`; `duration_min > 0` |
| `availability_slots` | `slot_id`, `provider_id`, `service_id`, `start_time`, `end_time`, `status` | PK `slot_id`; FKs → `providers`, `services`; `end_time > start_time` |
| `appointments` | `appointment_id`, `user_id`, `slot_id`, `active_slot_id`, `status`, `booked_at` | PK `appointment_id`; FKs → `availability_slots`, `users`; one unique active-slot key |

Note: the PDF lists the slots table as `Available_Slots`; the actual table name is `availability_slots` (matches the Main Entities list).

## Relationships

| Relationship | FK | Cardinality | Meaning |
|---|---|---|---|
| Provider → Service | `services.provider_id` | 1:N | A coach can offer many sessions |
| Provider → Slot | `availability_slots.provider_id` | 1:N | A coach can have many time blocks |
| Service → Slot | `availability_slots.service_id` | 1:N | A session type can be offered in many slots |
| User → Appointment | `appointments.user_id` | 1:N | A player can book many slots |
| Slot → Appointment | `appointments.slot_id` | 1:0..1 | A slot is booked at most once |
| User → Provider | `providers.user_id` | 1:0..1 | Each user is at most one provider |

## Double-booking guard

The `UNIQUE (active_slot_id)` constraint on `appointments` is enforced by the
database, not application code, so two simultaneous "Book" presses cannot
both succeed. The service also locks the slot row with `SELECT ... FOR UPDATE`;
the losing request returns **"Court is already booked."**

## Justification points (from the PDF)

- **Cardinality:** as in the relationships table.
- **Weak entities:** none. Each entity has its own ID; some depend on other entities existing but are still strong.
- **Constraints:** `end_time > start_time` on slots (the PDF writes it backwards — typo); `NOT NULL` on FKs.

## Implementation vs spec

`schema.sql` matches the spec and adds a few things the PDF does not list:

| Addition in `schema.sql` | Where |
|---|---|
| `services.max_players INT NOT NULL DEFAULT 1` | `services` |
| `providers.location`, `services.price` defaults | `providers`, `services` |
| `UNIQUE (provider_id, start_time)` — a provider cannot list two slots at the same start | `availability_slots` |
| `status` CHECK: slots `OPEN`/`BOOKED`/`CANCELLED`; appointments `BOOKED`/`CANCELLED` (default `OPEN` / `BOOKED`) | `availability_slots`, `appointments` |
| `active_slot_id` + `UNIQUE (active_slot_id)` + CHECK tying it to `BOOKED` — one active booking per slot | `appointments` |
| `DROP TABLE IF EXISTS ... CASCADE` in FK-safe order at top of file (reset on every boot) | file header |

Consequences to keep in mind:

- Slot status and appointment existence can disagree (a `BOOKED` slot with no appointment). Booking must set both in one transaction.
- Cancelling an appointment preserves the row as history (`status = 'CANCELLED'`), clears `active_slot_id`, and releases the slot; the unique active-slot index permits one new `BOOKED` appointment.
- `password_hash` values in `seed.sql` are BCrypt hashes. The local-only test
  accounts and passwords are documented in the README; real credentials must
  be provided through environment variables and must never be committed.

## Seed data

4 users (2 providers, 2 customers), 2 providers (Court 3, Coach Kim), 3 services (singles rental, doubles rental, private coaching), 7 open slots dated 1–5 days after the boot day (`CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL ...`), so demo data never goes stale.

## Not modelled yet

`games`, `scores`, `shots` (head-to-head tracking) are intentionally out of the M1 schema. No table for confirmation notifications, metrics/logs, or the RAG knowledge base yet.

## Diagrams

The ER diagram (PDF page 3) is an image and was not transcribed. Verify it agrees with the tables above.
