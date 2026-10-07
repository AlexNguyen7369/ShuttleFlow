# Frontend scaffold — Milestone 2

No frontend implementation exists yet. This directory is reserved for the
Milestone 2 web interface: home, login, slot browsing/filtering, booking,
confirmation, customer appointments/cancellation, and provider workflows.

The UI must call the existing/new REST controllers and preserve the flow:

```text
UI → Controller → Service → Repository → Database → DTO response → UI
```

This solves the M2 usable-web-interface requirement while leaving the M1
backend behavior intact until a frontend technology is selected.
