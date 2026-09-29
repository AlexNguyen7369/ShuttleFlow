# Current Progress

## Completed
1. 2026-09-28 — Alex Nguyen — Added CLAUDE.md (stack, hard rules, layout, build/test, conventions) and docs/decisions.md and docs/schema-notes.md distilled from the Milestone 1 PDF.
2. 2026-09-28 — Alex Nguyen — Pushed the ShuttleFlow app repo with definitions.md and video_script.md added to its .gitignore.
3. 2026-09-28 — Alex Nguyen — Added the add-endpoint project skill, the reviewer and test-writer agents, and docs/features specs 01-08, and removed trailing whitespace in HomeController.

## What's next
**Resolve the open items in docs/decisions.md, starting with PostgreSQL vs H2 for the database.**

**Why this is next:** the spec names PostgreSQL but the skeleton runs on H2, and the schema, cancel semantics and session-type filter all build on that choice before Milestone 2 booking work can start.
