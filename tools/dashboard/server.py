#!/usr/bin/env python3
"""Project-local, dependency-free engineering dashboard."""
import argparse
import datetime as dt
import json
import os
import re
import subprocess
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
DATA = HERE / "data"
STATIC = HERE / "static"
LOCK = threading.RLock()
CONTEXT_MARKER_START = "<!-- dashboard:active-tasks:start -->"
CONTEXT_MARKER_END = "<!-- dashboard:active-tasks:end -->"


def run(*args, cwd=ROOT, timeout=5):
    try:
        p = subprocess.run(args, cwd=cwd, text=True, capture_output=True, timeout=timeout)
        return p.returncode, p.stdout.strip(), p.stderr.strip()
    except (OSError, subprocess.SubprocessError) as exc:
        return 1, "", str(exc)


def git(*args):
    return run("git", *args)


def read_json(path, default):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return default


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")
    tmp.replace(path)


def context_path():
    """Return the repository context file."""
    return ROOT / "context.md"


def sync_active_tasks(items):
    """Keep Doing tasks visible in the project context without rewriting its snapshot."""
    path = context_path()
    try:
        original = path.read_text(encoding="utf-8") if path.exists() else ""
        section_lines = [CONTEXT_MARKER_START, "## Active dashboard tasks", ""]
        active = [item for item in items if item.get("status") == "doing"]
        if active:
            for item in active:
                section_lines.append(f"- **{item.get('title', 'Untitled task')}** — {item.get('detail', '').strip()}")
        else:
            section_lines.append("No tasks currently in Doing.")
        section_lines.extend(["", CONTEXT_MARKER_END])
        section = "\n".join(section_lines)
        pattern = re.compile(re.escape(CONTEXT_MARKER_START) + r".*?" + re.escape(CONTEXT_MARKER_END), re.S)
        updated = pattern.sub(section, original) if pattern.search(original) else (original.rstrip() + "\n\n" + section + "\n")
        if updated != original:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(updated, encoding="utf-8")
    except OSError:
        # The board remains usable if the project context is unavailable/read-only.
        return


def next_task_id(items, milestone):
    prefix = str(milestone or "M2").upper()
    numbers = [int(match.group(1)) for item in items if (match := re.fullmatch(re.escape(prefix) + r"-(\d+)", str(item.get("id", ""))))]
    return f"{prefix}-{max(numbers, default=0) + 1:02d}"


def iso(timestamp=None):
    return dt.datetime.fromtimestamp(timestamp or dt.datetime.now().timestamp()).astimezone().isoformat(timespec="seconds")


def project_dirs():
    dirs = [ROOT]
    for child in ROOT.iterdir():
        if child.is_dir() and child.name not in {".git", ".codex", ".claude", "tools", "node_modules", ".venv"}:
            if any((child / marker).exists() for marker in ("pom.xml", "package.json", "pyproject.toml", "Cargo.toml", "go.mod", "build.gradle")):
                dirs.append(child)
    return dirs


def detect_project():
    dirs = project_dirs()
    manifests = []
    languages = []
    tests = []
    for directory in dirs:
        for name, language in (("pom.xml", "Java"), ("package.json", "JavaScript/TypeScript"), ("pyproject.toml", "Python"), ("Cargo.toml", "Rust"), ("go.mod", "Go"), ("build.gradle", "Java")):
            path = directory / name
            if path.exists():
                manifests.append(str(path.relative_to(ROOT)))
                if language not in languages:
                    languages.append(language)
        if (directory / "pom.xml").exists(): tests.append({"command": "mvn test", "source": str((directory / "pom.xml").relative_to(ROOT))})
        if (directory / "package.json").exists():
            package = read_json(directory / "package.json", {})
            for name, command in (package.get("scripts") or {}).items():
                if "test" in name.lower(): tests.append({"command": f"npm run {name}", "source": str((directory / "package.json").relative_to(ROOT))})
        if (directory / "pyproject.toml").exists(): tests.append({"command": "python -m pytest", "source": str((directory / "pyproject.toml").relative_to(ROOT))})
    guidance = [str(p.relative_to(ROOT)) for p in ROOT.rglob("*") if p.is_file() and p.name in {"AGENTS.md", "CLAUDE.md", "README.md", "current_progress.md"} and ".git" not in p.parts][:80]
    name = next((d.name for d in dirs[1:]), ROOT.name)
    return {"name": name, "root": str(ROOT), "languages": languages or ["Unknown"], "manifests": manifests, "test_commands": tests, "guidance": guidance, "project_directories": [str(d.relative_to(ROOT)) for d in dirs]}


def status():
    code, out, err = git("status", "--short")
    return {"clean": code == 0 and not out, "files": out.splitlines() if out else [], "error": err if code else None}


def changes():
    s = status()
    rows = []
    for line in s["files"]:
        rows.append({"status": line[:2].strip() or "?", "path": line[3:] if len(line) > 3 else line})
    return rows


def history():
    code, out, _ = git("log", "-n", "30", "--date=iso", "--pretty=format:%h%x09%an%x09%ad%x09%s")
    if code:
        return []
    return [{"sha": p[0], "author": p[1], "date": p[2], "subject": p[3]} for line in out.splitlines() if len(p := line.split("\t", 3)) == 4]


TASK_ID = re.compile(r"^M(\d+)-(\d+)$")


def history_order(item):
    """Milestone order (M1 before M2), then task number; non-task entries such as TESTS go last."""
    match = TASK_ID.match(str(item.get("id", "")))
    return (int(match.group(1)), int(match.group(2))) if match else (99, 0)


def implemented_additions():
    """Every completed milestone task. A hand-written implemented.json entry wins over its board copy."""
    items = {str(item.get("id", "")): item for item in read_board("implemented.json", [])}
    for item in read_board("todo.json", []):
        item_id = str(item.get("id", ""))
        if item.get("status") != "done" or not TASK_ID.match(item_id) or item_id in items:
            continue
        items[item_id] = {
            "id": item_id,
            "title": item.get("title", "Completed task"),
            "detail": item.get("detail", ""),
            "date": str(item.get("completed") or item.get("started") or "")[:10],
            "source": "dashboard task board"
        }
    return sorted(items.values(), key=history_order)


def source_lines(rel, ref=None):
    """Lines of a repo file, from the working tree or from a git revision (for code that has since been fixed)."""
    if ref:
        try:
            shown = subprocess.run(["git", "show", f"{ref}:{rel}"], cwd=ROOT, text=True, capture_output=True, timeout=5)
        except (OSError, subprocess.SubprocessError):
            return None
        return shown.stdout.splitlines() if shown.returncode == 0 else None
    path = (ROOT / rel).resolve()
    if ROOT.resolve() not in path.parents or not path.is_file():
        return None
    return path.read_text(encoding="utf-8", errors="replace").splitlines()


def bug_snippet(rel, highlight, ref=None, context=3):
    """Capture the code around a bug: highlight is a list of [start, end] line ranges (1-based, inclusive)."""
    lines = source_lines(rel, ref)
    ranges = [[int(r[0]), int(r[-1])] for r in highlight or [] if r]
    if not lines or not ranges or any(not 1 <= lo <= hi <= len(lines) for lo, hi in ranges):
        return None
    start = max(1, min(lo for lo, _ in ranges) - context)
    stop = min(len(lines), max(hi for _, hi in ranges) + context)
    return {"file": rel, "ref": ref, "from": start, "lines": lines[start - 1:stop], "highlight": ranges}


def branches():
    code, out, _ = git("branch", "-a", "--format=%(refname:short)")
    return [{"name": x, "current": x == current_branch()} for x in out.splitlines()] if code == 0 else []


def current_branch():
    return git("branch", "--show-current")[1] or "detached"


def tests():
    detected = detect_project()["test_commands"]
    return [{**item, "status": "not_run", "note": "Detected from project configuration; run explicitly to update this result."} for item in detected]


def files_matching(pattern):
    found = []
    for directory in project_dirs():
        for path in directory.rglob("*"):
            if path.is_file() and ".git" not in path.parts and path.stat().st_size < 2_000_000:
                try:
                    text = path.read_text(errors="ignore")
                except OSError:
                    continue
                for line_no, line in enumerate(text.splitlines(), 1):
                    if pattern.search(line): found.append({"path": str(path.relative_to(ROOT)), "line": line_no, "text": line.strip()[:240]})
                    if len(found) >= 100: return found
    return found


def gate():
    project = detect_project()
    state = status()
    if state["error"]: return {"state": "unknown", "text": f"Git status unavailable: {state['error']}"}
    if not project["test_commands"]: return {"state": "unknown", "text": "No test command detected; compatibility gate is not configured."}
    if state["clean"]: return {"state": "unknown", "text": "Tests detected, but no recorded test run is available."}
    return {"state": "changed", "text": "Project changed since the last dashboard observation; run the detected tests before relying on the gate."}


def read_board(name, default):
    return read_json(DATA / name, default)


TREE_IGNORES = {".git", ".idea", ".venv", "__pycache__", "node_modules", "target", "data"}


def context_summary(path, is_dir):
    """Give tree nodes a useful summary without requiring a checked-in map."""
    name = path.name if path != ROOT else ROOT.name
    relative = str(path.relative_to(ROOT)) if path != ROOT else "."
    if is_dir:
        summaries = {
            "src": "Application source: HTTP controllers, business services, repositories, DTOs, authentication, and tests.",
            "frontend": "Browser-facing entry point and presentation assets for the booking workflow.",
            "docs": "Feature contracts, schema notes, design decisions, and milestone evidence.",
            "tools": "Project tooling, including the live engineering dashboard.",
            "resources": "Runtime configuration, database schema, and seed data.",
            "main": "Spring Boot application code organized by architectural layer.",
            "test": "Automated unit, endpoint, integration, and concurrency coverage.",
        }
        return summaries.get(name, f"Project area containing {relative}.")
    suffix = path.suffix.lower()
    if "/controller/" in f"/{relative}/": return "HTTP boundary: translates requests and responses."
    if "/service/" in f"/{relative}/": return "Business rules, validation, authorization, and transaction boundary."
    if "/repository/" in f"/{relative}/": return "Parameterized SQL and database access."
    if "/dto/" in f"/{relative}/": return "Immutable request or response shape crossing the API boundary."
    if "/auth/" in f"/{relative}/": return "Session and role context used by the security boundary."
    if suffix in {".sql", ".properties", ".yml", ".yaml"}: return "Runtime/database configuration used by the Spring application."
    if suffix in {".md", ".pdf"}: return "Project documentation or specification evidence."
    if suffix in {".js", ".css", ".html"}: return "Frontend/dashboard presentation asset."
    if suffix == ".java": return "Java application or test source."
    return "Project file."


def context_tree(path, depth=0):
    is_dir = path.is_dir()
    node = {"name": path.name if path != ROOT else ROOT.name, "path": "." if path == ROOT else str(path.relative_to(ROOT)), "kind": "directory" if is_dir else "file", "summary": context_summary(path, is_dir)}
    if is_dir:
        try:
            children = [child for child in path.iterdir() if child.name not in TREE_IGNORES and not child.name.startswith(".")]
        except OSError:
            children = []
        node["children"] = [context_tree(child, depth + 1) for child in sorted(children, key=lambda item: (not item.is_dir(), item.name.lower()))]
    return node


def context_architecture():
    files = []
    for directory in project_dirs():
        for path in directory.rglob("*"):
            if path.is_file() and not any(part in TREE_IGNORES for part in path.parts):
                files.append(str(path.relative_to(ROOT)))
    groups = {
        "frontend": {"name": "Frontend", "summary": "User-facing booking screens and browser assets.", "paths": [p for p in files if p.startswith("frontend/")]},
        "controller": {"name": "Controller", "summary": "Receives HTTP requests and returns API responses.", "paths": [p for p in files if "/controller/" in f"/{p}"]},
        "service": {"name": "Service", "summary": "Owns validation, authorization, booking rules, and transactions.", "paths": [p for p in files if "/service/" in f"/{p}"]},
        "repository": {"name": "Repository", "summary": "Runs parameterized SQL through JdbcTemplate.", "paths": [p for p in files if "/repository/" in f"/{p}"]},
        "database": {"name": "Database", "summary": "Schema, seed data, and PostgreSQL/H2 configuration.", "paths": [p for p in files if p.endswith(("schema.sql", "seed.sql", "application.properties"))]},
        "tests": {"name": "Tests", "summary": "Service, endpoint, and concurrent-booking verification.", "paths": [p for p in files if "/test/" in f"/{p}"]},
    }
    return {"nodes": list(groups.values()), "connections": [
        {"from": "frontend", "to": "controller", "label": "HTTP/JSON"},
        {"from": "controller", "to": "service", "label": "method calls"},
        {"from": "service", "to": "repository", "label": "rules + transactions"},
        {"from": "repository", "to": "database", "label": "JdbcTemplate / SQL"},
        {"from": "tests", "to": "service", "label": "unit + integration coverage"},
    ]}


def project_context():
    projected = [item for item in read_board("todo.json", []) if item.get("status") != "done"]
    feature_specs = []
    feature_dir = ROOT / "docs" / "features"
    if feature_dir.exists():
        feature_specs = [{"name": path.stem, "path": str(path.relative_to(ROOT))} for path in sorted(feature_dir.glob("*.md"))]
    return {
        "generated_at": iso(),
        "tree": context_tree(ROOT),
        "architecture": context_architecture(),
        "stack": [
            {"name": "Browser", "summary": "Static frontend sends booking/auth requests."},
            {"name": "Spring Boot 3.3.4 / Java 17", "summary": "Controllers, services, repositories, sessions, DTOs, and global errors."},
            {"name": "JdbcTemplate", "summary": "Repositories own portable parameterized SQL."},
            {"name": "PostgreSQL / H2", "summary": "PostgreSQL at runtime; in-memory H2 for tests; one portable schema.sql with FOR UPDATE + UNIQUE (active_slot_id)."},
        ],
        "projected": projected,
        "specifications": feature_specs,
    }


def payload(route):
    project = detect_project()
    if route == "/api/meta": return {"project": project, "dashboard": "project-dashboard", "features": ["team", "needs_you", "agents", "analytics", "changes", "history", "implemented", "todo", "bugs", "bug_snippets", "context", "tests"], "port": PORT}
    if route == "/api/gate": return gate()
    if route == "/api/team": return {"branch": current_branch(), "branches": branches()}
    if route == "/api/changes": return {"status": status(), "files": changes()}
    if route == "/api/history": return {"commits": history()}
    if route == "/api/implemented": return {"items": implemented_additions()}
    if route == "/api/tests": return {"detected": tests()}
    if route == "/api/todo": return {"items": read_board("todo.json", [])}
    if route == "/api/manual": return {"items": read_board("manual.json", [])}
    if route == "/api/suggestions": return {"items": read_board("suggestions.json", [])}
    if route == "/api/bugs": return {"items": read_board("bugs.json", [])}
    if route == "/api/context": return project_context()
    if route == "/api/needtoknow": return {"items": files_matching(re.compile(r"TODO|FIXME|NEEDS[- ]YOU|BLOCKED", re.I))[:50]}
    if route == "/api/agents": return {"items": read_board("agents.json", []), "sources": [x for x in (".agents", ".claude", ".codex") if (ROOT / x).exists()]}
    if route == "/api/analytics": return {"commits": len(history()), "changed_files": len(changes()), "detected_tests": len(tests()), "languages": project["languages"]}
    return None


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_): pass
    def send_json(self, value, code=200):
        body = json.dumps(value).encode()
        self.send_response(code); self.send_header("Content-Type", "application/json"); self.send_header("Content-Length", str(len(body))); self.end_headers(); self.wfile.write(body)
    def do_GET(self):
        path = urlparse(self.path).path
        if path == "/" or path == "/index.html":
            body = (STATIC / "index.html").read_bytes(); self.send_response(200); self.send_header("Content-Type", "text/html; charset=utf-8"); self.send_header("Content-Length", str(len(body))); self.end_headers(); self.wfile.write(body); return
        if path in {"/app.js", "/static/app.js"}:
            body = (STATIC / "app.js").read_bytes(); self.send_response(200); self.send_header("Content-Type", "text/javascript"); self.send_header("Content-Length", str(len(body))); self.end_headers(); self.wfile.write(body); return
        if path == "/static/style.css":
            body = (STATIC / "style.css").read_bytes(); self.send_response(200); self.send_header("Content-Type", "text/css"); self.send_header("Content-Length", str(len(body))); self.end_headers(); self.wfile.write(body); return
        with LOCK:
            value = payload(path)
        self.send_json(value if value is not None else {"error": "not found"}, 200 if value is not None else 404)
    def do_POST(self):
        path = urlparse(self.path).path
        try: body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
        except ValueError: self.send_json({"error": "invalid JSON"}, 400); return
        mapping = {"/api/todo": "todo.json", "/api/manual": "manual.json", "/api/suggestions": "suggestions.json", "/api/bugs": "bugs.json"}
        if path not in mapping: self.send_json({"error": "not found"}, 404); return
        with LOCK:
            items = read_board(mapping[path], [])
            if body.get("action") == "add":
                if path == "/api/bugs":
                    required = ("name", "source", "details", "solution", "subagent", "discovered_at")
                    missing = [field for field in required if not str(body.get(field, "")).strip()]
                    if missing: self.send_json({"error": f"missing bug fields: {', '.join(missing)}"}, 400); return
                    item = {field: str(body[field]).strip() for field in required}
                    item["id"] = f"BUG-{len(items) + 1:03d}"
                    # Optional code location: {"file", "line", "endLine"} or "highlight": [[a, b], ...], plus an
                    # optional git "ref" when the buggy code is no longer in the working tree.
                    if body.get("file"):
                        highlight = body.get("highlight") or [[body.get("line"), body.get("endLine") or body.get("line")]]
                        try:
                            snippet = bug_snippet(str(body["file"]), highlight, body.get("ref"))
                        except (TypeError, ValueError):
                            snippet = None
                        if snippet is None: self.send_json({"error": "file/line range could not be read"}, 400); return
                        item["snippet"] = snippet
                else:
                    item = {**body, "id": next_task_id(items, body.get("milestone", "M2")), "milestone": body.get("milestone", "M2"), "created": iso()}
                items.append(item)
            elif body.get("action") == "move" and path == "/api/todo":
                if body.get("status") not in {"next", "doing"}: self.send_json({"error": "tasks can move only to next or doing"}, 400); return
                found = False
                for item in items:
                    if item.get("id") == body.get("id"):
                        item["status"] = body["status"]
                        if body["status"] == "doing": item["started"] = iso()
                        found = True
                        break
                if not found: self.send_json({"error": "todo item not found"}, 404); return
                sync_active_tasks(items)
            elif body.get("action") == "delete": items = [x for x in items if x.get("id") != body.get("id")]
            else: self.send_json({"error": "supported actions: add, move, delete"}, 400); return
            write_json(DATA / mapping[path], items)
        self.send_json({"ok": True, "items": items})


PORT = 8765


def main():
    global ROOT, DATA, STATIC, PORT
    parser = argparse.ArgumentParser(description="Project-local adaptive dashboard")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--root", type=Path, default=None)
    parser.add_argument("--lan", action="store_true", help="bind to the network; read-only unless separately extended")
    args = parser.parse_args()
    if args.root:
        ROOT = args.root.resolve(); DATA = HERE / "data"; STATIC = HERE / "static"
    PORT = args.port
    host = "0.0.0.0" if args.lan else "127.0.0.1"
    print(f"Project dashboard: http://127.0.0.1:{PORT}", flush=True)
    ThreadingHTTPServer((host, PORT), Handler).serve_forever()


if __name__ == "__main__": main()
