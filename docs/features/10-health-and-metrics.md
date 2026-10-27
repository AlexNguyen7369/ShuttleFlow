# Feature 10: Health check and metrics
Endpoints: GET /health, GET /metrics
Status: implemented (2026-10-27)

## Acceptance criteria
- `GET /health` runs `SELECT 1` through controller → service → repository
- Database reachable → `200 {"status":"UP","database":"UP"}`
- Database unreachable → `503 {"status":"DOWN","database":"DOWN"}` and an ERROR log line; the status code alone tells Docker/CI the answer
- `GET /metrics` → `200` with `bookingsCreated`, `bookingConflicts`, `notificationsSent`, `notificationFailures` (running totals since startup) and `pendingNotifications` (live outbox count)
- A successful booking increments `bookingsCreated`; a `409` increments `bookingConflicts`
- No authentication; neither endpoint exposes user data
