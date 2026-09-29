# Feature 6: Coach / court manager login
Endpoint: POST /auth/provider/login

## Acceptance criteria
- Valid email and password for a PROVIDER → 200, session/token returned with `role = PROVIDER` and `providerId`
- Wrong password or unknown email → 401 (same response for both)
- Valid credentials but the user is a CUSTOMER → 403
- PROVIDER user with no row in `providers` → 403
- Missing email or password → 400
- Provider-only endpoints (Features 7 and 8) reject a CUSTOMER session with 403 and an unauthenticated request with 401
