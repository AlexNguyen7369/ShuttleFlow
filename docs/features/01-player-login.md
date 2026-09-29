# Feature 1: Player login
Endpoint: POST /auth/login

## Acceptance criteria
- Valid email and password for a CUSTOMER → 200, session/token returned
- Wrong password → 401
- Email not registered → 401 (same response as wrong password, so accounts can't be enumerated)
- Missing email or password → 400
- Passwords are compared against `password_hash`; plaintext is never stored or logged
- A PROVIDER account logging in here → 200, with `role = PROVIDER` in the response (see Feature 6)
