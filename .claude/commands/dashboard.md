---
description: Start or inspect this project's local adaptive dashboard
argument-hint: "[--port N] [--lan]"
---

From the project root, check `http://127.0.0.1:<port>/api/meta` (default
8765). If it is not responding, start `python3 tools/dashboard/server.py`
with the supplied arguments in the background. Verify `/api/meta` and
`/api/gate`, then report the dashboard URL and the gate's `text`. Do not run
Git branch, push, switch, or sync actions unless explicitly requested.
