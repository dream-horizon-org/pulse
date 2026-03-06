# Cost & Latency Analysis

*Per-pattern comparison of LLM calls, latency, quality, and complexity*

---

## Assumptions

- **1 LLM call = 1 cost unit** (actual cost depends on model, tokens, and provider)
- **Pulse AI has 5 personas**: 3 core (Product Analytics, Engineering Manager, Designer) + 2 dependent (Customer Success, Business Leaders)
- **4 pipeline stages**: Planner → Executor → Summary → Report
- **Latency** is measured in sequential LLM call hops (parallel calls count as 1 hop)

---

## Pattern 1: Sequential Pipeline (Current Architecture)

```
Planner → Executor → Summary → Report
```

| Scenario | Planner | Executor | Summary | Report | Total Calls | Latency (hops) |
|---|---|---|---|---|---|---|
| Normal query | 1 | 1 | 1 | 1 | **4** | 4 |
| Unclear intent | 1 | 1 | 1 | 1 | **4** | 4 |
| Simple query | 1 | 1 | 1 | 1 | **4** | 4 |

**Summary**: Fixed cost. Every query costs exactly 4 LLM calls regardless of complexity.

---

## Pattern 2: Sequential + Callback Skipping

```
Planner → [gate] Executor → [gate] Summary → Report
```

| Scenario | Planner | Executor | Summary | Report | Total Calls | Latency (hops) |
|---|---|---|---|---|---|---|
| Normal query | 1 | 1 | 1 | 1 | **4** | 4 |
| Unclear intent | 1 | skip | skip | 1 | **2** | 2 |
| Skip summary only | 1 | 1 | skip | 1 | **3** | 3 |

**Summary**: Same ceiling as Pattern 1, but lower floor. Callbacks are free (pure Python).

---

## Pattern 3: Coordinator / Dispatcher

```
Coordinator → [routes to] Planner → Executor → Summary → Report
```

| Scenario | Coordinator | Planner | Executor | Summary | Report | Total Calls | Latency (hops) |
|---|---|---|---|---|---|---|---|
| Normal query | 1 | 1 | 1 | 1 | 1 | **5** | 5 |
| Unclear intent | 1 (responds directly) | — | — | — | — | **1** | 1 |
| Simple query | 1 | 1 | skip | skip | 1 | **3** | 3 |

**Summary**: Most flexible but adds 1 LLM call overhead to every request.

---

## Pattern 4: Parallel Fan-Out (Per-Persona Agents)

```
Planner → ParallelAgent[persona agents] → Synthesizer → Report
```

| Scenario | Planner | Persona Agents | Synthesizer | Report | Total Calls | Latency (hops) |
|---|---|---|---|---|---|---|
| All 5 personas | 1 | 5 (parallel) | 1 | 1 | **8** | 4 |
| 3 core personas | 1 | 3 (parallel) | 1 | 1 | **6** | 4 |
| 1 persona | 1 | 1 | 1 | 1 | **4** | 4 |
| Unclear (with callbacks) | 1 | 0 (all skipped) | skip | 1 | **2** | 2 |

**Summary**: Higher cost ceiling but latency stays flat (parallel execution). Best quality per persona.

---

## Pattern 5: Custom Agent (Full Python Control)

```
CustomAgent (Python routing) → [selected agents only]
```

| Scenario | Planner | Routing | Selected Agents | Summary | Report | Total Calls | Latency (hops) |
|---|---|---|---|---|---|---|---|
| All 5 personas | 1 | 0 | 5 (could be parallel) | 1 | 1 | **8** | 4 |
| 3 core personas | 1 | 0 | 3 | 1 | 1 | **6** | 4 |
| 1 persona | 1 | 0 | 1 | 1 | 1 | **4** | 4 |
| Unclear intent | 1 | 0 | 0 | 0 | 1 | **2** | 2 |
| Simple (skip to report) | 1 | 0 | 0 | 0 | 1 | **2** | 2 |

**Summary**: Same cost as Parallel Fan-Out but with zero routing overhead. Most efficient.

---

## Comparison Matrix

| Pattern | Min Cost | Typical Cost | Max Cost | Min Latency | Typical Latency | Complexity | Quality |
|---|---|---|---|---|---|---|---|
| **Sequential** | 4 | 4 | 4 | 4 hops | 4 hops | Low | Adequate |
| **Sequential + Callbacks** | 2 | 4 | 4 | 2 hops | 4 hops | Low-Medium | Adequate |
| **Coordinator / Dispatcher** | 1 | 5 | 5 | 1 hop | 5 hops | Medium | Adequate |
| **Parallel Fan-Out** | 2 | 6 | 8 | 2 hops | **4 hops** | Medium | **Best** |
| **Custom Agent** | 2 | 4-6 | 8 | 2 hops | **4 hops** | High | **Best** |

---

## Cost Breakdown by Query Type

### "Show me app performance trends" (3 personas)

| Pattern | LLM Calls | Latency |
|---|---|---|
| Sequential | 4 | 4 hops |
| Sequential + Callbacks | 4 | 4 hops |
| Coordinator | 5 | 5 hops |
| Parallel Fan-Out | 6 | 4 hops |
| Custom Agent | 6 | 4 hops |

### "hello" (unclear intent)

| Pattern | LLM Calls | Latency |
|---|---|---|
| Sequential | **4** (wasted) | 4 hops |
| Sequential + Callbacks | **2** | 2 hops |
| Coordinator | **1** | 1 hop |
| Parallel Fan-Out + Callbacks | **2** | 2 hops |
| Custom Agent | **2** | 2 hops |

### "What's our crash rate?" (1 persona — Engineering only)

| Pattern | LLM Calls | Latency |
|---|---|---|
| Sequential | 4 | 4 hops |
| Sequential + Callbacks | 3-4 | 3-4 hops |
| Coordinator | 3 | 3 hops |
| Parallel Fan-Out + Callbacks | **4** | 4 hops |
| Custom Agent | **4** | 4 hops |

---

## The Trade-Off Triangle

```
           Cost
          /    \
         /      \
        /        \
   Quality ─── Latency
```

**Optimize for cost** → Custom Agent or Sequential + Callbacks
- Lowest total LLM calls across all scenarios
- Zero routing overhead

**Optimize for quality** → Parallel Fan-Out
- Each persona gets a dedicated, focused LLM call
- Avoids context dilution from cramming all personas in one prompt

**Optimize for latency** → Parallel Fan-Out or Custom Agent (with parallel execution)
- Parallel persona calls reduce wall-clock time
- 3 personas in parallel ≈ 1 persona in latency

**Balanced approach** → Sequential + Callbacks
- Easiest upgrade from current architecture
- No overhead for normal queries
- Saves cost on edge cases (unclear, simple queries)

---

## Monthly Cost Estimation

Assuming **1,000 queries/month** with the following distribution:
- 60% normal (3 personas)
- 20% simple (1 persona)
- 10% complex (5 personas)
- 10% unclear intent

| Pattern | Normal (600) | Simple (200) | Complex (100) | Unclear (100) | **Monthly Total** |
|---|---|---|---|---|---|
| **Sequential** | 2,400 | 800 | 400 | 400 | **4,000 calls** |
| **Seq + Callbacks** | 2,400 | 600 | 400 | 200 | **3,600 calls** |
| **Coordinator** | 3,000 | 600 | 500 | 100 | **4,200 calls** |
| **Parallel Fan-Out** | 3,600 | 800 | 800 | 200 | **5,400 calls** |
| **Custom Agent** | 3,600 | 800 | 800 | 200 | **5,400 calls** |

> Note: Parallel/Custom Agent cost more in total but produce higher quality output per persona. The additional calls are for focused, per-persona analysis that improves the end result.

---

## Recommendation

See [Pulse AI Recommendations](./pulse-ai-recommendations.md) for the concrete migration path.
