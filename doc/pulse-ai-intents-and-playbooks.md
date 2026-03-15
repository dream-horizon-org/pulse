# Pulse AI: Granular Intents and Playbooks

This document defines the granular intent taxonomy and maps each intent to a playbook for the Executor.

---

## Table of Contents

1. [Intent Structure](#intent-structure)
2. [Granular Intent Taxonomy](#granular-intent-taxonomy)
3. [Intent → Playbook Mapping](#intent--playbook-mapping)
4. [Playbook Definitions](#playbook-definitions)
5. [Shared Playbook Templates](#shared-playbook-templates)
6. [Intent Classifier Output](#intent-classifier-output)
7. [Router Logic](#router-logic)

---

## Intent Structure

Intents are broken into three dimensions:

| Dimension | Values |
|-----------|--------|
| **Intent type** | `greeting` \| `vague` \| `analytical` \| `out_of_scope` |
| **Domain** (if analytical) | `funnel` \| `journey` \| `screen` \| `interactions` \| `crash` \| `performance` \| `network` \| `ui` \| `session_replay` |
| **Question type** | `single` \| `comparative` \| `root_cause` \| `overview` |

---

## Granular Intent Taxonomy

### Non-analytical intents (no playbook)

| Intent | Examples | Action |
|--------|----------|--------|
| `greeting` | "hi", "hello", "hey" | Response Generator |
| `vague` | "tell me about my app", "what's going on?" | Clarification |
| `out_of_scope` | "what's the weather?", unrelated | Polite decline |

### Analytical intents (map to playbooks)

| Granular intent | Domain | Question type | Example queries |
|-----------------|--------|---------------|-----------------|
| `funnel_single` | funnel | single | "conversion rate", "funnel step 3" |
| `funnel_comparative` | funnel | comparative | "compare funnel A to B" |
| `journey_single` | journey | single | "user paths", "common flows" |
| `screen_single` | screen | single | "screen views", "most visited screens" |
| `interactions_single` | interactions | single | "button clicks", "tap events" |
| `crash_single` | crash | single | "crash rate", "ANR" |
| `performance_single` | performance | single | "app vitals", "startup time" |
| `network_single` | network | single | "API latency", "network errors" |
| `ui_single` | ui | single | "heatmap", "where do users tap?" |
| `session_replay_single` | session_replay | single | "replay session" |
| `funnel_root_cause` | funnel | root_cause | "why did conversion drop?" |
| `crash_root_cause` | crash | root_cause | "why are crashes spiking?" |
| `performance_root_cause` | performance | root_cause | "why is this screen slow?" |
| `multi_root_cause` | multi | root_cause | "why did X happen?" (cross-domain) |
| `multi_overview` | multi | overview | "app health overview", "key metrics" |

---

## Intent → Playbook Mapping

| Granular intent | Playbook | Personas | Order |
|----------------|----------|----------|-------|
| `greeting` | — | — | respond |
| `vague` | — | — | clarify |
| `out_of_scope` | — | — | decline |
| `funnel_single` | FUNNEL_SINGLE | PA | PA |
| `funnel_comparative` | FUNNEL_COMPARATIVE | PA | PA |
| `journey_single` | JOURNEY_SINGLE | PA | PA |
| `screen_single` | SCREEN_SINGLE | PA | PA |
| `interactions_single` | INTERACTIONS_SINGLE | PA | PA |
| `crash_single` | CRASH_SINGLE | EM | EM |
| `performance_single` | PERFORMANCE_SINGLE | EM | EM |
| `network_single` | NETWORK_SINGLE | EM | EM |
| `ui_single` | UI_SINGLE | DE | DE |
| `session_replay_single` | SESSION_REPLAY_SINGLE | DE | DE |
| `funnel_root_cause` | ROOT_CAUSE_FUNNEL | PA, EM | PA → EM |
| `crash_root_cause` | ROOT_CAUSE_CRASH | EM, PA | EM → PA |
| `performance_root_cause` | ROOT_CAUSE_PERFORMANCE | EM, PA | EM → PA |
| `multi_root_cause` | ROOT_CAUSE_MULTI | PA, EM, DE | PA → EM → DE |
| `multi_overview` | OVERVIEW | PA, EM, DE | PA → EM → DE |

---

## Playbook Definitions

### Single-domain playbooks (PA)

**FUNNEL_SINGLE**
- Call PA only
- Task: "Analyze funnel. Focus on: {user_query}"

**FUNNEL_COMPARATIVE**
- Call PA only
- Task: "Compare funnel metrics. Focus on: {user_query}"

**JOURNEY_SINGLE**
- Call PA only
- Task: "Analyze user journey. Focus on: {user_query}"

**SCREEN_SINGLE**
- Call PA only
- Task: "Analyze screen metrics. Focus on: {user_query}"

**INTERACTIONS_SINGLE**
- Call PA only
- Task: "Analyze interactions/events. Focus on: {user_query}"

### Single-domain playbooks (EM)

**CRASH_SINGLE**
- Call EM only
- Task: "Analyze crash/stability. Focus on: {user_query}"

**PERFORMANCE_SINGLE**
- Call EM only
- Task: "Analyze performance/app vitals. Focus on: {user_query}"

**NETWORK_SINGLE**
- Call EM only
- Task: "Analyze network/API. Focus on: {user_query}"

### Single-domain playbooks (DE)

**UI_SINGLE**
- Call DE only
- Task: "Analyze UI/heatmap. Focus on: {user_query}"

**SESSION_REPLAY_SINGLE**
- Call DE only
- Task: "Session replay analysis. Focus on: {user_query}"

### Root-cause playbooks

**ROOT_CAUSE_FUNNEL**
- Call PA first: "Analyze funnel dropoff/conversion. Identify where and why."
- Call EM with PA output: "PA found: {prior_findings}. Investigate technical causes: crashes, performance, errors."

**ROOT_CAUSE_CRASH**
- Call EM first: "Analyze crash/stability. Identify patterns."
- Call PA with EM output: "EM found: {prior_findings}. Correlate with user behavior/funnel."

**ROOT_CAUSE_PERFORMANCE**
- Call EM first: "Analyze performance. Identify bottlenecks."
- Call PA with EM output: "EM found: {prior_findings}. Correlate with user impact."

**ROOT_CAUSE_MULTI**
- Call PA first: "Analyze user query. Focus on metrics, funnel, dropoff."
- Call EM with PA output: "PA found: {prior_findings}. Investigate technical causes."
- Call DE with PA + EM output: "PA and EM found: {prior_findings}. Analyze UI/session context."

### Overview playbook

**OVERVIEW**
- Call PA, EM, DE in sequence
- Each gets: task = "Contribute your domain's perspective on: {user_query}"
- Pass prior findings to each subsequent persona for coherence

---

## Shared Playbook Templates

To reduce duplication, similar intents can share a template:

| Template | Intents | Persona |
|----------|---------|---------|
| `SINGLE_PA` | funnel_single, journey_single, screen_single, interactions_single | PA |
| `SINGLE_EM` | crash_single, performance_single, network_single | EM |
| `SINGLE_DE` | ui_single, session_replay_single | DE |

Implementation: Router resolves granular intent → template + domain for task formulation.

---

## Intent Classifier Output

### Option A: Single granular label

```json
{
  "intent": "funnel_root_cause",
  "confidence": 0.92
}
```

### Option B: Structured (Router combines)

```json
{
  "intent_type": "analytical",
  "domain": "funnel",
  "question_type": "root_cause",
  "confidence": 0.92
}
```

Router maps `(domain, question_type)` → `playbook_key`.

---

## Router Logic

```python
INTENT_TO_PLAYBOOK = {
    "greeting": None,  # respond
    "vague": None,     # clarify
    "out_of_scope": None,  # decline
    "funnel_single": "FUNNEL_SINGLE",
    "funnel_comparative": "FUNNEL_COMPARATIVE",
    "journey_single": "JOURNEY_SINGLE",
    "screen_single": "SCREEN_SINGLE",
    "interactions_single": "INTERACTIONS_SINGLE",
    "crash_single": "CRASH_SINGLE",
    "performance_single": "PERFORMANCE_SINGLE",
    "network_single": "NETWORK_SINGLE",
    "ui_single": "UI_SINGLE",
    "session_replay_single": "SESSION_REPLAY_SINGLE",
    "funnel_root_cause": "ROOT_CAUSE_FUNNEL",
    "crash_root_cause": "ROOT_CAUSE_CRASH",
    "performance_root_cause": "ROOT_CAUSE_PERFORMANCE",
    "multi_root_cause": "ROOT_CAUSE_MULTI",
    "multi_overview": "OVERVIEW",
}

def route(intent: str) -> dict:
    if intent in ("greeting", "vague", "out_of_scope"):
        return {"action": "respond", "playbook_key": None}
    return {
        "action": "execute",
        "playbook_key": INTENT_TO_PLAYBOOK[intent],
        "intent": intent,
    }
```

---

## Extending

To add a new intent and playbook:

1. Add the granular intent to the taxonomy
2. Add the intent → playbook mapping
3. Define the playbook (personas, order, task formulation)
4. Update the Router mapping
