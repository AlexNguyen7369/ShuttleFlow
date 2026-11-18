# ShuttleFlow frontend (Next.js)

A Next.js (App Router, JavaScript) client for the Spring Boot API. It replaced the static HTML client after
Milestone 4.

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173, needs the API running on :8080
npm run build      # production build (CI runs this)
```

- **Pages:** `/` home, `/login`, `/browse` (filter, paginate, book with a confirmation dialog), `/bookings`
  (upcoming/history, cancel), `/provider` (services, create/remove slots, booked appointments, run reminders),
  `/assistant` (booking chat on `/ai/chat`, policy questions on `/ai/ask`).
- **API calls:** every request goes to `/api/...`, which `next.config.mjs` forwards to the API
  (`API_URL`, default `http://localhost:8080`). The browser therefore sees one origin, and no CORS is needed.
- **Login:** the API sets the JWT in an `HttpOnly`, `SameSite=Lax` cookie named `SF_AUTH`. Page code never reads
  or stores the token; `app/auth.js` restores who is signed in from `GET /auth/session` on load.
