# Feature 3: Book a slot for open play
Endpoint: POST /appointments
Status: implemented (2026-10-08)

Request body: `{ "slotId": 4 }`

## Acceptance criteria
- Logged-in customer books an OPEN slot → 201, appointment created with status BOOKED, slot no longer OPEN
- Slot is already booked (including two simultaneous requests) → 409 "Court is already booked."; the slot row lock (`SELECT ... FOR UPDATE`) serialises competing requests and the `UNIQUE (active_slot_id)` constraint is the database backstop; the service layer translates a violation to this 409
- Slot doesn't exist → 404
- Slot start time is in the past → 409
- Missing or invalid `slotId` → 400
- Not logged in → 401
- PROVIDER account tries to book → 403
- Booking and slot status change happen in one transaction
