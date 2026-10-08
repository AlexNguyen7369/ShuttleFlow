# Feature 8: View appointments booked with me
Endpoint: GET /provider/appointments
Status: implemented (2026-10-08)

## Acceptance criteria
- Logged-in provider → 200, BOOKED appointments on their own slots, soonest first
- Each item includes appointmentId, slotId, serviceName, startTime, endTime, and the booking customer's name and email
- Appointments on other providers' slots are never returned
- Cancelled appointments are excluded
- No bookings → 200 with an empty list
- Not logged in → 401
- CUSTOMER account → 403
