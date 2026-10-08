# Feature 7: Set or remove court availability
Endpoints:
- POST /provider/slots
- DELETE /provider/slots/{id}
Status: implemented (2026-10-08)

Request body for POST: `{ "serviceId": 1, "startTime": "2026-10-04T18:00:00", "endTime": "2026-10-04T19:00:00" }`

Supporting endpoint: `GET /provider/services` → 200 with the provider's own services (feeds the availability form); 401/403 as below.

## Acceptance criteria

### Create (POST /provider/slots)
- Logged-in provider creates a slot for one of their own services → 201, slot is OPEN and appears in `GET /slots`
- `endTime` not after `startTime` → 400
- `startTime` in the past → 400
- Provider already has a slot with that start time (`UNIQUE (provider_id, start_time)`) → 409
- `serviceId` belongs to a different provider → 403
- `serviceId` doesn't exist → 404
- Not logged in → 401; CUSTOMER → 403

### Remove (DELETE /provider/slots/{id})
- Provider removes their own OPEN slot → 204, slot no longer appears in `GET /slots` (deleted, or set to CANCELLED when cancelled appointment history references it)
- Slot belongs to a different provider → 403
- Slot doesn't exist → 404
- Slot has a confirmed appointment → 409 (booking must be cancelled first)
- Not logged in → 401; CUSTOMER → 403
