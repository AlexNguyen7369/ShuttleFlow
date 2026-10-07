---
description: Create or start the project-local adaptive dashboard
argument-hint: "[--port N] [--lan]"
---

Use the project-dashboard workflow for the current repository.

1. Resolve the project root from the current working directory.
2. If `tools/dashboard/server.py` is absent, initialize a project-local,
   stdlib-only dashboard using the repository's detected language, tests, Git
   metadata, agent files, and guidance documents. Use the RobloxGames dashboard
   only as a feature reference; do not copy Roblox-specific assumptions.
3. Ensure the project has `.claude/commands/dashboard.md` pointing at its own
   `tools/dashboard/server.py`. Preserve existing files unless initialization
   requires adding them.
4. Check `/api/meta` on the requested/default port (8765), start the local
   server if needed, then verify `/api/meta` and `/api/gate`.
5. Report the URL and the gate `text`; report actual errors if startup fails.

Never perform Git branch, push, switch, or sync actions unless explicitly
requested. Never enable LAN write access unless explicitly requested.
