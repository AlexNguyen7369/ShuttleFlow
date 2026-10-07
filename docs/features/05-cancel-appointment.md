# Feature 5: Cancel my appointment
Endpoint: DELETE /appointments/{id}

## Acceptance criteria
- Owner cancels their own upcoming appointment → 204, slot becomes available again
- Different user tries to cancel → 403
- Appointment doesn't exist → 404
- Appointment already in the past → 409
- Not logged in → 401
