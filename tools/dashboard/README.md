# Project dashboard

This is a project-local, standard-library-only dashboard. It discovers the
repository's manifests, language, tests, Git history, guidance files, agent
directories, TODO markers, changes, branches, and recent commits at runtime.

Start it from the repository root:

```sh
python3 tools/dashboard/server.py
```

Open http://127.0.0.1:8765. Use `--port N` for another port and `--lan` only
when the dashboard should be reachable from the local network. The dashboard
is read-only for discovered project data; its local TODO/manual/suggestions
JSON files are the editable board sources. Use **Queue** to move a backlog task
to Up next or **Start** to move a task to Doing; Doing tasks are reflected in
the project's `context.md` automatically.

API endpoints include `/api/meta`, `/api/gate`, `/api/team`, `/api/agents`,
`/api/analytics`, `/api/changes`, `/api/history`, `/api/needtoknow`,
`/api/todo`, `/api/implemented`, `/api/bugs`, `/api/context`, and `/api/tests`.

The Changes & history tab's **Implemented additions** list every completed
milestone task (M1, M2, ...), grouped by milestone in task order. Plain-English
entries in `data/implemented.json` take precedence over a task's board copy;
any `data/todo.json` task whose status becomes `done` appears automatically.

If the page warns that the server is out of date, `server.py` changed after the
server started (Python does not reload it). Restart the server.

The Context tab is generated from the current filesystem and project board on
each refresh. It provides an expandable structure tree, an interactive layer
diagram, live stack/connection explanations, and projected unfinished work
from the Milestone requirements and feature-spec backlog.

## Bug tracking

The Bugs tab reads `tools/dashboard/data/bugs.json`. Subagents can record a
discovered bug with `POST /api/bugs` and an `add` action containing these
required fields: `name`, `source`, `details`, `solution`, `subagent`, and
`discovered_at`.

A bug can also carry the code where it lives, shown with the faulty lines
highlighted in red. Add `file` plus `line`/`endLine`, or `highlight`
(`[[start, end], ...]` for several ranges). Add `ref` (a git revision) when the
buggy code has since been fixed, so the snippet shows the code as it was. The
server captures 3 lines of context around the range into a `snippet` field.

```sh
curl -X POST localhost:8765/api/bugs -H 'Content-Type: application/json' -d '{
  "action": "add", "name": "...", "source": "...", "details": "...", "solution": "...",
  "subagent": "reviewer", "discovered_at": "2026-10-08",
  "file": "src/main/java/.../ProviderService.java", "highlight": [[71, 71]], "ref": "2c3a234"}'
```

The dashboard does not infer or invent bugs from source-code
markers; only explicitly recorded subagent findings appear in this tab.
