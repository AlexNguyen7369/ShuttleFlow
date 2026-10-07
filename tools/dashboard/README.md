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
`/api/todo`, and `/api/tests`.
