# Feature 12: Autonomous reminder agent
Trigger: `@Scheduled` every 15 minutes (`shuttleflow.ai.reminder.poll-ms`); manual: POST /ai/reminders/run (provider) → `{ "scanned": 1, "queued": 1, "fallbacks": 0 }`
Status: implemented (2026-11-16)

## Acceptance criteria
- Finds `BOOKED` appointments starting within the next 24 hours that have no `APPOINTMENT_REMINDER` notification yet
- Gemini writes a short personal reminder (first name, provider, start time)
- Self-check (bonus): the text must name the appointment's court/coach and state its exact start time (12h or 24h, matched on digit boundaries so 11pm never passes for 1pm) with no placeholders or links; otherwise the template "Reminder: your <service> at <provider> starts <time>." is used
- LLM error, rate limit (429) or over-long text → template; the run continues with the next appointment and never throws out of the scheduler
- Each reminder is queued in the M3 outbox in its own transaction with key `REMINDER:<appointmentId>`, so the after-commit send, poller retry and dead-letter rules apply unchanged
- One reminder per appointment: a re-run queues none, and the `UNIQUE (idempotency_key)` blocks a duplicate from a concurrent run
- `GET /metrics` reports `remindersQueued` and `reminderFallbacks`
- Manual trigger: provider → `200`, customer → `403`, logged out → `401`
