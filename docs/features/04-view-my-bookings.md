# Feature 4: View my upcoming bookings and history
Endpoint: GET /appointments?view={upcoming|history}
Status: implemented (2026-10-08)

## Acceptance criteria
- `view=upcoming` → 200, the caller's BOOKED appointments whose slot starts in the future, soonest first
- `view=history` → 200, the caller's past appointments (and cancelled ones), most recent first
- `view` omitted → defaults to `upcoming`
- Each item includes appointmentId, providerName, serviceName, startTime, endTime, status
- Only the caller's own appointments are returned, never another user's
- No appointments → 200 with an empty list
- Unknown `view` value → 400
- Not logged in → 401
