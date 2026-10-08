# ShuttleFlow frontend — Milestone 2

The dependency-free frontend is `index.html` plus `src/app.js` and
`src/styles.css`. It is intentionally static so it can be served with any
simple web server while the Spring Boot API runs on port 8080.

It provides home, login, slot browsing/filtering, booking, booking confirmation
through the appointments view, customer cancellation, provider availability,
and provider appointment views.

The UI must call the existing/new REST controllers and preserve the flow:

```text
UI → Controller → Service → Repository → Database → DTO response → UI
```

Start it locally with `python3 -m http.server 5173 --directory frontend`.
The app sends same-session requests with `credentials: include` to the API.
