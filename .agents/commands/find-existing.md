---
description: Search the monorepo for existing implementations before you build something new. Pass what you're about to build (a util, hook, service, DAO, component, screen, registry entry, etc.).
argument-hint: <thing you're about to build>
---

The user is about to build: **$ARGUMENTS**

Goal: stop them from reinventing the wheel. Find any existing code that already does (or partially does) this, and report back so they can decide to reuse, extend, or build new.

## Step 1 — Classify the request

Pick the most likely categories. Multiple is fine:

- Backend Java util / helper → `backend/server/src/main/java/.../helper/` `.../util/`
- Backend service → `backend/server/src/main/java/.../service/<domain>/`
- Backend DAO / SQL query → `backend/server/src/main/java/.../dao/` and `Queries.java` files
- Backend DTO / mapper → `backend/server/src/main/java/.../resources/<domain>/`
- Backend ServiceError code → `backend/server/src/main/java/.../error/ServiceError.java`
- Alerts cron piece → `backend/pulse-alerts-cron/`
- React hook → `pulse-ui/src/hooks/`
- React component → `pulse-ui/src/components/`
- React screen → `pulse-ui/src/screens/`
- React util / helper → `pulse-ui/src/helpers/` `pulse-ui/src/utils/`
- React store → `pulse-ui/src/stores/`
- API route constant → `pulse-ui/src/constants/`
- AI agent tool → `pulse_ai/agent.py` (FunctionTool definitions)
- Web SDK instrumentation → `pulse-web-otel/`
- Android SDK instrumentation → `pulse-android-otel/`
- React Native bridge call → `pulse-react-native-otel/`
- Docker / deploy script → `deploy/scripts/`
- ClickHouse query/table → `backend/db/migrations/clickhouse/prod/`, `backend/server/.../Queries.java`

## Step 2 — Search

For each chosen category, run targeted searches. Use:
- `Grep` for keyword/regex (function names, similar-sounding identifiers, related domain words)
- `Explore` agent (subagent_type=Explore) when category is broad or you need multiple naming conventions tried (e.g., camelCase + snake_case + abbreviations + synonyms)
- `find` via `Bash` when you need files by name pattern

Search for:
1. Exact name match (`makeFooBar`, `FooBarService`)
2. Partial/synonym match (if request is "format date helper", also try `formatTime`, `dateFormat`, `humanizeDate`, `toRelative`)
3. Function/method whose body looks like what the user described (grep for the keywords they used)

## Step 3 — Rank candidates

For each match, judge:
- **Strong match** — same intent, can reuse directly. Cite `path:line`.
- **Partial match** — does ~50% of what's asked. Suggest extending vs forking.
- **Tangential** — different but related. Mention so user knows it exists.

Skip noise: tests, generated code, vendored deps, anything in `node_modules/`, `target/`, `dist/`, `build/`.

## Step 4 — Report

Output format:

```
## Existing implementations for: <thing>

### Strong matches (likely reuse)
- `<path>:<line>` — <function/class name> — <one-line why it matches>

### Partial matches (extend or fork)
- `<path>:<line>` — <name> — <what overlaps, what's missing>

### Tangential (FYI)
- `<path>:<line>` — <name> — <how it relates>

### Recommendation
<reuse / extend / build new>, with one-sentence rationale.
```

If nothing meaningful found, say so explicitly: *"No existing implementation found in the searched categories. Safe to build new."* Don't pad with weak matches.

## Notes

- Keep the search bounded — don't chase every possible synonym. 2–3 search passes is plenty.
- Always include `path:line` so the user can jump straight there.
- The recommendation is opinionated — don't say "you decide". Give a default; the user can override.
