# Feature 2: Browse and filter open slots
Status: implemented (2026-09-29)

Endpoint: GET /slots?providerId={id}&sessionType={OPEN_PLAY|COACHING}&serviceId={id}&date={yyyy-MM-dd}&page={n}

## Acceptance criteria
- No filters → 200, first page of OPEN slots ordered by `start_time`
- Each page contains at most 10 slots, fetched with SQL `LIMIT 10 OFFSET (page-1)*10`
- `page` defaults to 1; a page past the last one → 200 with an empty list
- `providerId` filter (coach openings) → only slots for that provider
- `sessionType` filter → only slots for that session type (open play vs coaching); mapped via `providers.type`: `OPEN_PLAY` = `COURT`, `COACHING` = `COACH` (decided 2026-09-29, no schema change)
- `serviceId` and `date` filters can be combined with the existing filters; dates before today are rejected with `400`
- Filters combine with each other and with pagination
- Only slots with `status = 'OPEN'` that have not started yet are returned; BOOKED, CANCELLED, and past slots never appear
- `page` < 1 or not a number, or unknown `sessionType` → 400
- Response includes slotId, providerName, serviceName, startTime, endTime, price
