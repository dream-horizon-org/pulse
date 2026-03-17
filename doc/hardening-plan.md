# Pulse AI — Hardening Plan

Three focused improvements to production-readiness: chart data validation, session persistence, and frontend structure alignment.

---

## 1. Chart Data Validation & Normalization

### Problem

The LLM generates raw ECharts option objects via the `create_chart` tool. There is no validation between the LLM output and the frontend chart components. Known failure modes:

| Failure | Effect |
|---|---|
| `chart_type` is not one of `line/bar/pie/area` | `AiChartCard` returns `null` — chart silently disappears |
| `series` is missing or empty | Chart renders with empty canvas |
| Pie chart gets `xAxis/yAxis` structure instead of `[{name, value}]` | Blank pie chart |
| `series[].data` contains strings instead of numbers | ECharts misrenders silently |
| Deeply malformed JSON | `AiChartCard` could crash the entire message bubble |

### Solution: Two-layer defense

#### Layer 1 — Backend normalization (`pulse_ai/tools/create_chart.py`)

**New function:** `_normalize_chart_data(chart_type, data) -> dict`

Responsibilities:
- Clamp `chart_type` to `{"line", "bar", "pie", "area"}`, default to `"line"`
- For line/bar/area: ensure `xAxis` exists with `type: "category"`, ensure `yAxis` exists with `type: "value"`, inject `type` into each series item
- For pie: ensure `series[].data` is `[{name, value}]` format; convert flat arrays if needed
- Coerce numeric strings in `series[].data` to actual numbers
- Strip any keys that aren't valid ECharts top-level options (prevent prompt injection into the DOM)

Call this after `json.loads(data)` and before building `chart_config`.

**New validation:** reject unknown `chart_type` with a safe fallback:

```python
VALID_CHART_TYPES = {"line", "bar", "pie", "area"}
if chart_type not in VALID_CHART_TYPES:
    chart_type = "line"
```

#### Layer 2 — Frontend error boundary (`AiChartCard`)

**New component:** `ChartErrorBoundary` (React class component)

Wrap `AiChartCard`'s rendering in an error boundary so a bad config doesn't crash the entire `ChatMessage` component. On error, render a muted fallback:

```
┌──────────────────────────────┐
│ ⚠ Chart could not be rendered│
└──────────────────────────────┘
```

Log the error + chart config to console for debugging.

**File:** `pulse-ui/src/screens/AiChat/components/AiChartCard/ChartErrorBoundary.tsx`

#### Optional future improvement — JSON Schema validation in the tool

ADK supports declaring function parameter schemas. The `data` parameter is currently `str` (free-form JSON). A stricter approach would define a limited schema for the `data` field so Gemini's function-calling layer validates the structure before the tool even runs. This is a larger change and can be deferred.

### Files to modify

| File | Change |
|---|---|
| `pulse_ai/tools/create_chart.py` | Add `_normalize_chart_data()`, add `VALID_CHART_TYPES` check |
| `pulse-ui/src/screens/AiChat/components/AiChartCard/ChartErrorBoundary.tsx` | New file — React error boundary |
| `pulse-ui/src/screens/AiChat/components/AiChartCard/AiChartCard.tsx` | Wrap chart render in `ChartErrorBoundary` |

---

## 2. Session Persistence

### Problem

Sessions and artifacts are stored in `InMemorySessionService` and `InMemoryArtifactService` (`pulse_ai/server.py` lines 39-40). This means:

- All sessions are lost on server restart
- Cannot run multiple server replicas (no shared state)
- No chat history survives deployment

### Solution: Environment-aware service factory

#### Step 1 — Database session service

Replace the hardcoded in-memory instantiation with a factory function:

```python
def _create_session_service():
    db_url = os.getenv("SESSION_DB_URL")
    if db_url:
        from google.adk.sessions import DatabaseSessionService
        return DatabaseSessionService(db_url=db_url)
    from google.adk.sessions import InMemorySessionService
    return InMemorySessionService()
```

- **Local dev (no env var):** keeps using in-memory — zero setup, no database needed
- **Production (env var set):** uses `DatabaseSessionService` backed by PostgreSQL or SQLite

#### Step 2 — Environment configuration

Add to `pulse_ai/.env.example`:

```
# Session persistence (optional, defaults to in-memory)
# SQLite:     sqlite:///pulse_ai_sessions.db
# PostgreSQL: postgresql://user:pass@host:5432/pulse_ai
SESSION_DB_URL=
```

For local dev with persistence, use SQLite:

```
SESSION_DB_URL=sqlite:///pulse_ai_sessions.db
```

#### Step 3 — Dependencies

Add to `pulse_ai/requirements.txt`:

```
aiosqlite    # for SQLite async support
```

For PostgreSQL in production, add `asyncpg` instead.

#### Step 4 — Artifact persistence (deferred)

Artifacts (saved chart configs) are currently in-memory too. For production on GCP:

```python
from google.adk.artifacts import GcsArtifactService
artifact_service = GcsArtifactService(bucket_name="pulse-ai-artifacts")
```

This is **lower priority** because chart data is already embedded in the SSE response and stored in session events. Artifact persistence is mainly useful for allowing users to re-download or re-render past charts independently of the session. Defer until the feature is needed.

#### Future considerations

| Concern | Approach |
|---|---|
| Session TTL / cleanup | Add a background task or cron that deletes sessions older than N days |
| Multi-replica | `DatabaseSessionService` with PostgreSQL works across replicas out of the box |
| User auth on session endpoints | Validate JWT/token in FastAPI middleware; currently `user_id` is trusted from the cookie |
| Session migration on schema changes | ADK uses SQLAlchemy + Alembic; standard migration workflow |

### Files to modify

| File | Change |
|---|---|
| `pulse_ai/server.py` | Replace inline `InMemorySessionService()` with factory function |
| `pulse_ai/.env.example` | Add `SESSION_DB_URL` with comment |
| `pulse_ai/requirements.txt` | Add `aiosqlite` |

---

## 3. Frontend Structure Alignment

### Problem

The `AiChat` screen uses a deeply nested `components/` subfolder structure that is unique in the codebase. Every other screen follows a flat pattern:

```
screens/Home/
├── index.ts
├── Home.tsx
├── Home.interface.ts
├── Home.module.css
└── Home.constants.ts     (optional)
```

AiChat has:
```
screens/AiChat/
├── AiChat.tsx            ← no index.ts barrel
├── AiChat.constants.ts
├── AiChat.module.css     ← no .interface.ts
└── components/           ← unique to AiChat
    ├── ChatSidebar/
    ├── ChatMessageList/
    ├── ChatMessage/
    ├── ChatInput/
    ├── AiChartCard/
    ├── EmptyState/
    └── TypingIndicator/
```

Missing conventions:
1. No `index.ts` barrel export (every other screen has one)
2. No `AiChat.interface.ts` (every other screen has one)
3. Import in `Constants.ts` goes directly to `AiChat/AiChat` instead of `AiChat/`
4. The `components/` subfolder is justified by complexity but not used elsewhere

### Solution: Align with conventions, keep justified nesting

#### Step 1 — Add barrel export

**New file:** `pulse-ui/src/screens/AiChat/index.ts`

```typescript
export { AiChat } from "./AiChat";
```

#### Step 2 — Add interface file

**New file:** `pulse-ui/src/screens/AiChat/AiChat.interface.ts`

Re-export the chat-specific types that `AiChat.tsx` consumes, so the screen folder is self-documenting:

```typescript
export type { ChatMessage, ChatSession, AiChartConfig } from "../../types/chat";
```

#### Step 3 — Update import in Constants.ts

Change:
```typescript
import { AiChat } from "../screens/AiChat/AiChat";
```
To:
```typescript
import { AiChat } from "../screens/AiChat";
```

#### Step 4 — Move `AiChartCard` to shared components (optional)

`AiChartCard` is a thin wrapper over the shared `Charts/` components. It could live in `src/components/AiChartCard/` since it has no dependency on chat-specific state. This makes it reusable if AI charts appear in other contexts (dashboards, reports).

This is optional and can be done when/if the component is needed outside AiChat.

### Decision: Keep `components/` subfolder

The nested structure is justified because:
- AiChat has 7 sub-components with their own CSS modules and tests
- Flattening would put 15+ files in a single folder
- Other screens don't have this level of component complexity

The key alignment items are the barrel export, interface file, and import path — not the folder structure itself.

### Files to modify

| File | Change |
|---|---|
| `pulse-ui/src/screens/AiChat/index.ts` | New file — barrel export |
| `pulse-ui/src/screens/AiChat/AiChat.interface.ts` | New file — re-export types |
| `pulse-ui/src/constants/Constants.ts` | Update import path |

---

## Execution Order

| Priority | Item | Effort | Risk if skipped |
|---|---|---|---|
| 1 | Chart validation (backend + frontend error boundary) | ~1 hour | Broken charts in production, potential React crashes |
| 2 | Session persistence | ~30 min | All chat history lost on every deploy |
| 3 | Frontend structure alignment | ~15 min | Inconsistent codebase, confusing for new contributors |

Items 1 and 2 can be done in parallel. Item 3 is a quick follow-up.
