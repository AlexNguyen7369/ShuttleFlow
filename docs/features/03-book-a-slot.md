# Feature 3: Book a slot for open play
Endpoint: POST /appointments

Request body: `{ "slotId": 4 }`

## Acceptance criteria
- Logged-in customer books an OPEN slot → 201, appointment created with status CONFIRMED, slot no longer OPEN
- Slot is already booked (including two simultaneous requests) → 409 "Court is already booked."; the `UNIQUE (slot_id)` constraint is the guard, and the service layer catches the violation
- Slot doesn't exist → 404
- Slot start time is in the past → 409
- Missing or invalid `slotId` → 400
- Not logged in → 401
- PROVIDER account tries to book → 403
- Booking and slot status change happen in one transaction
