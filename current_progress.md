# Current Progress

## Completed
1. 2026-09-28 — Alex Nguyen — Added CLAUDE.md (stack, hard rules, layout, build/test, conventions) and docs/decisions.md and docs/schema-notes.md distilled from the Milestone 1 PDF.
2. 2026-09-28 — Alex Nguyen — Pushed the ShuttleFlow app repo with definitions.md and video_script.md added to its .gitignore.
3. 2026-09-28 — Alex Nguyen — Added the add-endpoint project skill, the reviewer and test-writer agents, and docs/features specs 01-08, and removed trailing whitespace in HomeController.
4. 2026-09-29 — Alex Nguyen — Implemented feature 02 (GET /slots filtering by providerId and sessionType, 10-per-page LIMIT/OFFSET pagination, 400 on bad input) with 18 tests, README docs and reviewer PASS.
5. 2026-09-29 — Alex Nguyen — Updated the stale test note in CLAUDE.md now that src/test exists.
6. 2026-09-29 — Alex Nguyen — Merged CLAUDE.md, docs/, .claude/ agents and skill, and this log into the app repo and updated every path to the single-repo layout.
7. 2026-10-07 — Codex — Started M2-07/M2-09: added safe global 400/401/403/404/409/500 error mapping, shared request validation, BCrypt login, provider login, server-side sessions, logout, and customer/provider session guards. Added 28 passing tests including authentication and RBAC-boundary coverage.
8. 2026-10-08 — Codex — Completed the M2 booking, cancellation, provider availability, provider appointment, filtering, concurrency, frontend, documentation, dashboard history, and voice-script work. The regression suite passes 40 tests and `mvn clean package` succeeds.
9. 2026-10-08 — Alex Nguyen — Completed Milestone 2 end to end: fixed PostgreSQL generated keys, stale seed dates, past-slot browsing, slot-removal 500, missing CORS, framework-error 500s and session fixation; unified the portable schema, finished the UI, raised the suite to 78 passing tests including an 8-thread PostgreSQL race, and updated docs, report, summary and dashboard history.
10. 2026-10-08 — Alex Nguyen — Fixed the dashboard's empty Implemented additions (stale server; now lists all M1 and M2 tasks with a stale-server warning) and added bug code snippets with the faulty lines highlighted in red, captured from the pre-fix commit.
11. 2026-10-08 — Alex Nguyen — Removed the stale nested ShuttleFlow/ copy and organized the milestone files into Milestone1/ and Milestone2/ (assignment PDF, walkthrough and voice scripts), and pointed the dashboard at the root context.md.
12. 2026-10-27 — Alex Nguyen — Planned Milestone 3: recorded the rederive-session outbox/poller/dead-letter design in Milestone3/m3-checklist.md, wrote Milestone3/milestone3plan.md, and added a one-commit-per-checklist-item rule to CLAUDE.md.

## What's next
**Implement M3 checklist item 1: record the Milestone 3 decisions (outbox + poller, timeouts/retry, idempotency key, dead-letter state, /health, metrics, no Actuator) in docs/decisions.md.**

**Why this is next:** every later M3 item (DTO, gateway, outbox, health, metrics) implements these decisions, so they are written down first as the reference for code and the report.
