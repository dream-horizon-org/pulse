---
name: fatal-five
description: Fatal Five — triages large-scale production failures by grounding hypotheses in repository code, ranking the top five failure modes by likelihood (P×I×E) with evidence and disconfirmers. Use when production is degraded at scale, SEV/incident response, post-incident review, outage, spike, or widespread regression; also when the user says Fatal Five or fatal-five.
disable-model-invocation: true
---

# Fatal Five (code-grounded production triage)

## When to use

- Symptom is **production**, **at scale** (many users/tenants/requests), or **high blast radius**.
- User wants **code-linked** reasons, not generic advice.
- Goal: **exactly five** ranked hypotheses with a **repeatable scoring** rubric.

## Communication

Follow project `.cursor/rules/caveman.mdc`: terse, high signal, no filler. Full sentences only when ambiguity would cause wrong action.

## Inputs to lock first (ask if missing)

Minimum facts before deep code dive:

1. **Symptom** — error shape, HTTP codes, timeouts, partial writes, data wrongness, etc.
2. **Scope** — which surfaces (web, API, worker), regions, % of traffic, start time.
3. **Change window** — deploys, config, flags, migrations, dependency bumps.
4. **Evidence** — sample trace IDs, log lines, metric names/dashboards (if user has them).

If unknown, state assumptions explicitly and mark hypotheses **speculative**.

## Workflow

```
- [ ] Freeze facts: symptom, scope, change window, evidence (or assumptions)
- [ ] Map blast radius to subsystems (read code: entrypoints, deps, data paths)
- [ ] Enumerate failure modes (code + ops), cap deliverable at 5 for ranking
- [ ] Score each with rubric below; sort descending
- [ ] Output using template; list fastest disconfirmers per rank
```

## Code analysis focus

Search and read with intent:

- **Hot paths** for the failing feature: controllers/handlers, queues, schedulers, SDK paths.
- **Scale knobs**: timeouts, retries, pools, concurrency limits, batch sizes, rate limits.
- **Partial failure modes**: async gaps, missing `await`, fire-and-forget, idempotency holes.
- **Data contracts**: schema drift, nullable fields, serialization, cache keys, TTLs.
- **Operational coupling**: feature flags, env vars, external APIs, broker lag, backpressure.

Prefer citations to repo (`path` + symbol or line range). If codebase is not the failure domain (pure infra), say so and pivot evidence to ops.

## Ranking rubric (required)

For each hypothesis compute:

| Field | Meaning |
|-------|---------|
| **P** | Probability 1–5 (1 rare, 5 very plausible given symptoms + code path) |
| **I** | Impact if true 1–5 (1 minor, 5 matches observed blast radius) |
| **E** | Evidence strength 1–5 (1 guess, 5 direct code/config/logs referenced) |

**Score = P × I × E** (range 1–125). Sort **descending**. If two tie, higher **E** wins, then higher **P**.

In prose next to each row: one line on why **P** and **E** were chosen.

## Deliverable template (copy verbatim structure)

```markdown
## Incident summary
- Symptom:
- Scope:
- Change window:
- Assumptions (if any):

## Fatal Five (ranked)

### 1) [Short title] — score P×I×E = __
- **Hypothesis**:
- **Code / config evidence**:
- **Why likely at scale**:
- **Disconfirm fast** (specific check):

### 2) …
… through ### 5)

## Not ranked / deprioritized
- Bullets with one-line reason (e.g. contradicted by X, or E too low)

## Next actions
- Ordered list: smallest experiments to validate #1–#3 (logs, flags, canary, query, replay)
```

## Quality bar

- Exactly **five** ranked items unless codebase truly yields fewer; if fewer, explain gap.
- No hand-wavy “maybe database” without **which** query/path and **why** scale triggers it.
- Each top hypothesis must have **disconfirm** — how to prove false in <30 min when possible.

## Optional deep dive

For long-running incidents, add a short appendix: timeline table (UTC), deploy IDs, and “ruled out” with reason — still keep main body to five ranked hypotheses.
