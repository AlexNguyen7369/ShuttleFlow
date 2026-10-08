# Frontend source

- `app.js` — the whole client, grouped by screen: session, browse/filter/paginate,
  booking + confirmation dialog, customer appointments, provider workspace.
  Every action is a single `fetch` to the Spring Boot API with the session cookie.
- `styles.css` — layout and theme for all screens.
