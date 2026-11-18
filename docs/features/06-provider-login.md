# Feature 6: Coach / court manager login
Endpoint: POST /auth/provider/login
Status: implemented (2026-10-07)

## Acceptance criteria
- Valid email and password for a PROVIDER → 200, JWT cookie set with `role = PROVIDER` and `providerId`
- Wrong password or unknown email → 401 (same response for both)
- Valid credentials but the user is a CUSTOMER → 403
- PROVIDER user with no row in `providers` → 403
- Missing email or password → 400
- Provider-only endpoints (Features 7 and 8) reject a CUSTOMER token with 403 and an unauthenticated request with 401
