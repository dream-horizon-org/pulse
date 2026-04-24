#!/usr/bin/env python3
"""
Lint ClickHouse DDL under backend/db/**/clickhouse/: fail if CREATE TABLE otel.*
declares a physical project-scoping column that is not exactly ProjectId (PascalCase).
Row policies use ProjectId on otel.*.

Catches common variants (project_id, projectId, PROJECT_ID, projectid, proj_id, …)
by normalizing identifiers (ignore case, underscores, hyphens).

Legacy exception: project_monthly_usage keeps project_id until migrated.
"""

from __future__ import annotations

import os
import re
import sys
from pathlib import Path

# Tables allowed to use legacy project key column names (e.g. project_id).
# Matches logical name and physical shard/suffix tables (e.g. project_monthly_usage_local).
PROJECT_KEY_COLUMN_LEGACY_TABLES = frozenset({"project_monthly_usage"})

# Canonical project column name for new otel tables (matches row policies).
CANONICAL_PROJECT_COLUMN = "ProjectId"

# Normalized forms that mean "project id" but are not the canonical spelling.
_FORBIDDEN_PROJECT_KEY_NORMALIZED = frozenset({"projectid", "projid"})

_REPO_ROOT = Path(__file__).resolve().parents[2]
_SQL_GLOB = "backend/db/**/clickhouse/**/*.sql"

# CREATE TABLE otel.something — capture table name (word chars + underscore).
_CREATE_OTEL_TABLE_RE = re.compile(
    r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?otel\.([`\w]+)",
    re.IGNORECASE | re.DOTALL,
)

# First token on a column line: backtick-quoted or bare identifier.
_COLUMN_NAME_HEAD_RE = re.compile(
    r"^\s*(?:`([^`]+)`|([A-Za-z_][\w]*))\s+",
)


def _normalized_project_key_identifier(name: str) -> str:
    """Lowercase, strip separators — project_id / project-id / projectId -> projectid."""
    raw = name.strip().strip("`\"")
    return re.sub(r"[_\s\-]+", "", raw).lower()


def _is_noncanonical_project_key_column(name: str) -> bool:
    if name == CANONICAL_PROJECT_COLUMN:
        return False
    key = _normalized_project_key_identifier(name)
    return key in _FORBIDDEN_PROJECT_KEY_NORMALIZED


def _allows_legacy_project_key_column_table(table: str) -> bool:
    t = table.strip("`\"")
    for base in PROJECT_KEY_COLUMN_LEGACY_TABLES:
        if t == base or t.startswith(f"{base}_"):
            return True
    return False


def _repo_root() -> Path:
    return Path(os.environ.get("GITHUB_WORKSPACE", _REPO_ROOT)).resolve()


def _strip_line_comments(text: str) -> str:
    out_lines = []
    for line in text.splitlines():
        cut = None
        i = 0
        while i < len(line):
            if i < len(line) - 1 and line[i : i + 2] == "--":
                cut = i
                break
            i += 1
        out_lines.append(line[:cut] if cut is not None else line)
    return "\n".join(out_lines)


def _split_sql_statements(text: str) -> list[str]:
    """Split on semicolons; good enough for Pulse DDL (no semicolons inside strings)."""
    parts = []
    for raw in text.split(";"):
        s = raw.strip()
        if s:
            parts.append(s)
    return parts


def _table_header_end_pos(stmt: str, match_end: int) -> int:
    """Position after optional ON CLUSTER line (cluster id may be backtick-quoted)."""
    s = stmt[match_end:]
    stripped = s.lstrip()
    if not stripped.upper().startswith("ON CLUSTER"):
        return match_end + (len(s) - len(stripped))
    line_end = stripped.find("\n")
    if line_end == -1:
        paren = stripped.find("(")
        if paren != -1:
            return match_end + (len(s) - len(stripped)) + paren
        return match_end + len(s)
    consumed = (len(s) - len(stripped)) + line_end + 1
    return match_end + consumed


def _skip_as_table_clone(stmt: str, header_end: int) -> bool:
    """True if this is CREATE TABLE ... AS other_table (no inline column list)."""
    tail = stmt[header_end:].lstrip()
    return bool(re.match(r"AS\s+", tail, re.IGNORECASE))


def _extract_column_list_paren(stmt: str, open_paren_idx: int) -> str | None:
    """
    From first '(' of column list, return inner text up to matching ')'.
    Uses paren depth for nested () e.g. bloom_filter(0.001), LowCardinality(String).
    """
    if open_paren_idx < 0 or open_paren_idx >= len(stmt) or stmt[open_paren_idx] != "(":
        return None
    depth = 0
    i = open_paren_idx
    while i < len(stmt):
        c = stmt[i]
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return stmt[open_paren_idx + 1 : i]
        i += 1
    return None


def _find_column_list_open_paren(stmt: str, after_header: int) -> int:
    """Index of '(' starting the column list, or -1."""
    tail = stmt[after_header:]
    idx_as = re.search(r"\bAS\b", tail, re.IGNORECASE)
    search_region = tail[: idx_as.start()] if idx_as else tail
    pos = search_region.find("(")
    if pos == -1:
        return -1
    return after_header + pos


def _violations_in_statement(stmt: str, filepath: Path) -> list[tuple[str, str]]:
    """Return list of (table_name, offending_line) for this statement."""
    m = _CREATE_OTEL_TABLE_RE.search(stmt)
    if not m:
        return []
    table = m.group(1).strip("`\"")
    if _allows_legacy_project_key_column_table(table):
        return []

    header_end = _table_header_end_pos(stmt, m.end())
    if _skip_as_table_clone(stmt, header_end):
        return []

    open_idx = _find_column_list_open_paren(stmt, header_end)
    if open_idx < 0:
        return []

    column_block = _extract_column_list_paren(stmt, open_idx)
    if column_block is None:
        return []

    bad: list[tuple[str, str]] = []
    for line in column_block.splitlines():
        stripped = line.strip()
        if not stripped:
            continue
        upper = stripped.upper()
        if upper.startswith(
            ("INDEX ", "PRIMARY ", "CONSTRAINT ", "PROJECTION ", "SETTINGS ")
        ):
            continue
        m = _COLUMN_NAME_HEAD_RE.match(line)
        if not m:
            continue
        col = (m.group(1) or m.group(2) or "").strip()
        if not col:
            continue
        if _is_noncanonical_project_key_column(col):
            bad.append((table, line.strip()))
    return bad


def lint_file(path: Path) -> list[tuple[str, str, str]]:
    """Returns list of (table, line, relpath)."""
    text = path.read_text(encoding="utf-8")
    cleaned = _strip_line_comments(text)
    issues: list[tuple[str, str, str]] = []
    rel = str(path.relative_to(_repo_root()))
    for stmt in _split_sql_statements(cleaned):
        for table, line in _violations_in_statement(stmt, path):
            issues.append((table, line, rel))
    return issues


def main() -> int:
    root = _repo_root()
    db_root = root / "backend" / "db"
    if not db_root.is_dir():
        print(f"error: backend/db directory not found: {db_root}", file=sys.stderr)
        return 2

    all_issues: list[tuple[str, str, str]] = []
    for path in sorted(root.glob(_SQL_GLOB)):
        if not path.is_file():
            continue
        all_issues.extend(lint_file(path))

    if all_issues:
        print(
            "otel ClickHouse DDL lint failed: project-scoping column must be exactly "
            f"{CANONICAL_PROJECT_COLUMN} (not project_id, projectId, proj_id, …)",
            file=sys.stderr,
        )
        print(
            "(legacy tables exempt: %s)\n"
            % ", ".join(sorted(PROJECT_KEY_COLUMN_LEGACY_TABLES)),
            file=sys.stderr,
        )
        for table, line, rel in all_issues:
            print(f"  {rel}  table=otel.{table}\n    {line}", file=sys.stderr)
        return 1

    print("otel ClickHouse ProjectId column lint: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
