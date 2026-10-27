# Feature 9: Booking notifications across a service boundary
Endpoints: POST /mock-notifications (the receiving service); triggered by POST /appointments and DELETE /appointments/{id}
Status: implemented (2026-10-27)

Payload (one coarse-grained call per event):
`{ "recipient": "alex@shuttleflow.com", "type": "BOOKING_CONFIRMED", "message": "Your Singles at Court 3 on Sat Nov 7, 10:00 AM is booked.", "idempotencyKey": "BOOKED:57", "appointmentId": 57 }`

## Acceptance criteria
- A committed booking or cancellation writes one `notifications` row (`PENDING`) in the same transaction; a rolled-back one (e.g. 409) writes none
- The send happens only after commit, on a background thread; the booking response never waits for it
- Timeouts: connect 1s, read 2s. Up to 3 attempts with 200ms/400ms backoff on timeout, refused connection or 5xx; a 4xx is never retried
- Success → `SENT`; 4xx → `FAILED`; exhausted retries → stays `PENDING` with `attempts + 1`
- A poller every 30s resends `PENDING` rows older than 60s; after 5 failed rounds the row becomes `FAILED` (dead letter)
- The receiver answers 202 for a new key and 200 for a repeat key, storing it once; blank fields → 400
- Logs carry ids and keys only, never recipients, messages, passwords or session IDs

## Failure scenario: notification service down or slow
**What happens.** A player books a court while the notification service is
down (connection refused) or hung (no reply).

**Detection.**
- Each attempt is bounded by the timeouts. The gateway logs
  `WARN Notification attempt n/3 failed key=BOOKED:57` per attempt and
  `ERROR Notification gave up after 3 attempts key=BOOKED:57`.
- `GET /metrics` shows `notificationFailures` rising and `pendingNotifications` > 0.

**Impact.**
- None on booking. The appointment and slot change are already committed,
  and the player gets `201`.
- The send runs after commit on another thread, so it holds no slot lock and
  adds no latency.
- Only the notification is delayed.

**Recovery.**
1. The outbox row stays `PENDING`.
2. When the service is back, the next poller run (within ~30–90s) delivers it
   and marks it `SENT`.
3. If the earlier attempt actually arrived and only the reply was lost, the
   resend carries the same `BOOKED:57` key. The receiver acknowledges it
   without sending a second email.
4. If the outage outlasts 5 poller rounds, the row becomes `FAILED` and is
   left for an operator. Find these rows with
   `SELECT * FROM notifications WHERE status = 'FAILED'`.

**Crash variant.** If the app process dies right after commit, the row is
already durable. The poller on the restarted app sends it, so no crash
detection is needed.

**Tests.** These live in `NotificationIntegrationTest`, `NotificationGatewayTest` and `NotificationServiceTest`:
- booking and cancel succeed with the service down;
- a 409 writes no row;
- a blip succeeds on attempt 2;
- a hung service times out;
- a 4xx is not retried;
- the poller retries stale rows and dead-letters after the cap.
