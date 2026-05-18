# Google ADK Python — Evals Reference

Complete reference for the Google ADK Python evaluation framework.
Version: `google-adk 1.26.0` (installed at time of writing).

> **TypeScript note:** The eval framework is Python-only. ADK TS says "coming soon."
> This document covers Python ADK exclusively.

---

## Overview

ADK evals address the probabilistic nature of LLM agents — traditional pass/fail unit tests
are insufficient. The framework evaluates two dimensions:

1. **Trajectory** — did the agent call the right tools in the right order?
2. **Final response** — is the output accurate, relevant, and safe?

---

## File formats

### `.test.json` — unit test style (one eval set per file)

Used during active development. `AgentEvaluator.evaluate()` recursively discovers
all `.test.json` files in a directory.

```json
{
  "eval_set_id": "pulse-mcp-single-tool",
  "name": "pulse-mcp-single-tool",
  "eval_cases": [
    {
      "eval_id": "EVAL-001-list-projects",
      "conversation": [
        {
          "user_content": {
            "role": "user",
            "parts": [{ "text": "List all the projects I have access to." }]
          },
          "intermediate_data": {
            "tool_uses": [
              { "name": "list_projects", "args": {} }
            ],
            "tool_responses": []
          },
          "final_response": {
            "role": "model",
            "parts": [{ "text": "Here are your projects..." }]
          }
        }
      ]
    }
  ]
}
```

### `.evalset.json` — integration style (run via `adk eval` CLI)

Same schema as `.test.json`. Difference is intent: evalsets are larger suites run
in CI or via the web UI; test files are per-feature unit tests.

---

## Data model

```
EvalSet
└── eval_cases: EvalCase[]
    ├── eval_id: str                  # unique identifier
    ├── conversation: Invocation[]    # static multi-turn conversation
    ├── session_input: SessionInput   # optional initial state
    ├── rubrics: Rubric[]             # optional per-case rubrics
    └── final_session_state: dict     # expected session state at end

Invocation
├── user_content: Content            # the user's message
├── intermediate_data: IntermediateData
│   ├── tool_uses: FunctionCall[]    # expected tool trajectory
│   └── tool_responses: FunctionResponse[]
└── final_response: Content          # reference/golden response

SessionInput
├── app_name: str
├── user_id: str
└── state: dict                      # e.g. bearer_token, project_id
```

---

## All built-in metrics

### `tool_trajectory_avg_score`

Compares the agent's actual tool calls against `tool_uses` in the eval file.
Score per invocation: `1.0` if match, `0.0` if not. Final score is the average.

Three match types via `ToolTrajectoryCriterion.MatchType`:

| Match type | Behaviour |
|---|---|
| `EXACT` (default) | Perfect multiset match — no extras, no missing |
| `IN_ORDER` | All expected tools appear in order; extras allowed |
| `ANY_ORDER` | All expected tools appear; order and extras don't matter |

Default threshold: `1.0` (perfect score required).

### `response_match_score`

ROUGE-1 similarity between actual final response and `final_response` in eval file.
Default threshold: `0.8`.

### `final_response_match_v2`

LLM-as-judge semantic equivalence check. Calls a judge model (default: `gemini-2.5-flash`)
5 times and aggregates scores.

### `response_evaluation_score`

General LLM-graded quality. Marked unstable — always optional unless explicitly specified.

### `safety_v1`

Harmlessness check via LLM judge.

### `rubric_based_final_response_quality_v1`

Custom rubrics defined per eval case or per invocation. The judge model scores each rubric.

### `rubric_based_tool_use_quality_v1`

LLM judges tool usage quality against custom rubrics.

### `hallucinations_v1`

Checks final response (and optionally intermediate responses) for hallucinated claims.

### `per_turn_user_simulator_quality_v1`

For multi-turn evals with an LLM-backed user simulator. Scores conversation quality per turn.

---

## Default criteria

When no `test_config.json` is present alongside a `.test.json` file, these defaults apply:

```json
{
  "criteria": {
    "tool_trajectory_avg_score": 1.0,
    "response_match_score": 0.8
  }
}
```

---

## `test_config.json` — per-suite criteria override

Place alongside your `.test.json` file. `AgentEvaluator` auto-discovers it.

```json
{
  "criteria": {
    "tool_trajectory_avg_score": 1.0,
    "response_match_score": 0.5,
    "final_response_match_v2": {
      "threshold": 0.7,
      "judgeModelOptions": {
        "judgeModel": "gemini-2.5-flash",
        "numSamples": 5
      }
    }
  }
}
```

### `ToolTrajectoryCriterion` with match type

```json
{
  "criteria": {
    "tool_trajectory_avg_score": {
      "threshold": 1.0,
      "matchType": "IN_ORDER"
    }
  }
}
```

---

## Running evals

### CLI

```bash
# Run a single evalset file
adk eval path/to/agent_module/ path/to/evals.evalset.json

# Run all .test.json files in a directory
adk eval path/to/agent_module/ path/to/tests/

# With specific agent name (for multi-agent systems)
adk eval path/to/agent_module/ path/to/evals.evalset.json --agent_name em_agent
```

`agent_module` must be a directory containing `__init__.py` that exposes `root_agent`
or `get_agent_async`.

### pytest integration

```python
# tests/test_evals.py
import pytest
from google.adk.evaluation.agent_evaluator import AgentEvaluator

@pytest.mark.asyncio
async def test_single_tool_selection():
    await AgentEvaluator.evaluate(
        agent_module="pulse_mcp_eval_agent",        # dir with __init__.py
        eval_dataset_file_path_or_dir="evals/adk/single-tool.test.json",
        num_runs=1,
    )

@pytest.mark.asyncio
async def test_all_evals():
    await AgentEvaluator.evaluate(
        agent_module="pulse_mcp_eval_agent",
        eval_dataset_file_path_or_dir="evals/adk/",  # discovers all .test.json
        num_runs=2,
    )
```

### Web UI

```bash
adk web path/to/agent_module/
# Opens browser — Trace tab shows tool call trajectory visually
```

---

## Test case patterns

### A — Single tool, exact match

```json
{
  "eval_id": "EVAL-001-list-projects",
  "conversation": [{
    "user_content": { "role": "user", "parts": [{ "text": "List all the projects I have access to." }] },
    "intermediate_data": {
      "tool_uses": [{ "name": "list_projects", "args": {} }]
    },
    "final_response": { "role": "model", "parts": [{ "text": "You have access to the following projects..." }] }
  }]
}
```

Metric: `tool_trajectory_avg_score` with `EXACT` match type.

---

### B — Multi-step chain (exact order)

```json
{
  "eval_id": "EVAL-016-top-crash",
  "conversation": [{
    "user_content": { "role": "user", "parts": [{ "text": "Explain our top crash from last week for project fancode." }] },
    "intermediate_data": {
      "tool_uses": [
        { "name": "list_app_vitals_crash_issues", "args": { "projectId": "fancode" } },
        { "name": "get_app_vitals_issue_summary", "args": { "projectId": "fancode", "groupId": "G789" } },
        { "name": "get_app_vitals_issue_stack_traces", "args": { "projectId": "fancode", "groupId": "G789" } }
      ]
    },
    "final_response": { "role": "model", "parts": [{ "text": "The top crash last week was a NullPointerException..." }] }
  }]
}
```

---

### C — Multi-step with IN_ORDER (extras allowed)

Use when the chain must happen in order but intermediate tool calls are acceptable.
Set `matchType: "IN_ORDER"` in `test_config.json`.

```json
{
  "eval_id": "EVAL-021-rca-slowest-interaction",
  "conversation": [{
    "user_content": { "role": "user", "parts": [{ "text": "Show me the root cause analysis for our slowest interaction." }] },
    "intermediate_data": {
      "tool_uses": [
        { "name": "list_interactions", "args": { "projectId": "fancode" } },
        { "name": "get_interaction_root_cause", "args": { "projectId": "fancode", "interactionId": "42" } }
      ]
    },
    "final_response": { "role": "model", "parts": [{ "text": "The root cause of the slowest interaction..." }] }
  }]
}
```

---

### D — Multi-turn conversation

Multiple `Invocation` entries in `conversation`. Each turn's tool calls are evaluated independently.

```json
{
  "eval_id": "EVAL-multi-turn-crash-then-sessions",
  "conversation": [
    {
      "user_content": { "role": "user", "parts": [{ "text": "List my projects." }] },
      "intermediate_data": {
        "tool_uses": [{ "name": "list_projects", "args": {} }]
      },
      "final_response": { "role": "model", "parts": [{ "text": "You have 3 projects." }] }
    },
    {
      "user_content": { "role": "user", "parts": [{ "text": "Show crashes for fancode." }] },
      "intermediate_data": {
        "tool_uses": [{ "name": "list_app_vitals_crash_issues", "args": { "projectId": "fancode" } }]
      },
      "final_response": { "role": "model", "parts": [{ "text": "Found 5 crash groups." }] }
    }
  ]
}
```

---

### E — With session state (auth / project context)

```json
{
  "eval_id": "EVAL-session-auth",
  "session_input": {
    "appName": "pulse-mcp-eval",
    "userId": "test-user",
    "state": {
      "bearer_token": "test-jwt-token",
      "project_id": "fancode"
    }
  },
  "conversation": [{
    "user_content": { "role": "user", "parts": [{ "text": "List interactions for fancode." }] },
    "intermediate_data": {
      "tool_uses": [{ "name": "list_interactions", "args": { "projectId": "fancode" } }]
    },
    "final_response": { "role": "model", "parts": [{ "text": "Here are the interactions..." }] }
  }]
}
```

---

### F — Response quality with rubric

```json
{
  "eval_id": "EVAL-rca-response-quality",
  "conversation": [{
    "user_content": { "role": "user", "parts": [{ "text": "What caused the top crash this week?" }] },
    "intermediate_data": {
      "tool_uses": [
        { "name": "list_app_vitals_crash_issues", "args": { "projectId": "fancode" } },
        { "name": "get_app_vitals_issue_summary", "args": { "projectId": "fancode", "groupId": "G789" } }
      ]
    },
    "final_response": {
      "role": "model",
      "parts": [{ "text": "The top crash this week is a NullPointerException in PaymentActivity affecting 142 users." }]
    },
    "rubrics": [
      { "criterion": "Response references a specific crash name and affected user count." },
      { "criterion": "Response does not fabricate metric values not present in tool results." }
    ]
  }]
}
```

Requires `rubric_based_final_response_quality_v1` in `test_config.json`.

---

### G — Custom metric (Python function)

```python
# evals/adk/metrics/tool_precision.py
from google.adk.evaluation.evaluator import EvaluationResult, PerInvocationResult, EvalStatus
from google.adk.evaluation.eval_case import Invocation, get_all_tool_calls

async def tool_precision(
    actual_invocations: list[Invocation],
    expected_invocations: list[Invocation],
) -> EvaluationResult:
    results = []
    for actual, expected in zip(actual_invocations, expected_invocations):
        actual_calls = {c.name for c in get_all_tool_calls(actual.intermediate_data)}
        expected_calls = {c.name for c in get_all_tool_calls(expected.intermediate_data)}
        extra = actual_calls - expected_calls
        score = 1.0 if not extra else 0.0
        results.append(PerInvocationResult(
            actual_invocation=actual,
            expected_invocation=expected,
            score=score,
            eval_status=EvalStatus.PASSED if score == 1.0 else EvalStatus.FAILED,
        ))
    return EvaluationResult(per_invocation_results=results)
```

Register in `test_config.json`:

```json
{
  "criteria": {
    "tool_trajectory_avg_score": 1.0,
    "tool_precision": {
      "threshold": 1.0,
      "customFunctionPath": "evals.adk.metrics.tool_precision.tool_precision"
    }
  }
}
```

---

## Agent module convention

`AgentEvaluator` loads `root_agent` (or `get_agent_async`) from `__init__.py`:

```python
# pulse_mcp_eval_agent/__init__.py
from google.adk.agents import LlmAgent
from google.adk.tools.mcp_tool.mcp_toolset import MCPToolset, StdioConnectionParams
from mcp import StdioServerParameters
import os

root_agent = LlmAgent(
    name="pulse_mcp_eval_agent",
    model="gemini-2.5-flash",
    instruction=(
        'You are an assistant for Pulse, a real-time mobile and web observability platform. '
        'Use projectId "fancode" unless the user specifies otherwise.'
    ),
    tools=[
        MCPToolset(
            connection_params=StdioConnectionParams(
                server_params=StdioServerParameters(
                    command="node",
                    args=["../pulse-mcp/dist/index.js"],
                    env={
                        "PULSE_BASE_URL": os.environ["PULSE_BASE_URL"],
                        "PULSE_API_KEY": os.environ["PULSE_API_KEY"],
                    },
                )
            )
        )
    ],
)
```

---

## Directory layout

```
evals/adk/
├── pulse_mcp_eval_agent/
│   └── __init__.py               # root_agent definition
├── test_config.json              # default criteria for all .test.json files
├── 01-single-tool.test.json
├── 02-multi-step.test.json
├── 03-semantic-traps.test.json
├── 04-distractor-traps.test.json
├── 05-disambiguation.test.json
├── 06-scenarios.test.json
├── metrics/
│   └── tool_precision.py         # custom metric functions
└── pulse-mcp-full.evalset.json   # full suite for adk eval CLI / CI
```

---

## Metrics comparison: ADK vs promptfoo

| Capability | ADK Python | promptfoo |
|---|---|---|
| Exact tool set match | `tool_trajectory_avg_score` (EXACT) | `tool-call-f1 threshold: 1` |
| Ordered tool chain | `tool_trajectory_avg_score` (IN_ORDER) | `trajectory:tool-sequence` |
| Any-order tool set | `tool_trajectory_avg_score` (ANY_ORDER) | `tool-call-f1` (set semantics) |
| Must-not-call guard | Custom metric | `javascript` assertion |
| Response similarity | `response_match_score` (ROUGE-1) | `rouge-n`, `similar` |
| LLM response quality | `final_response_match_v2`, `rubric_based_*` | `llm-rubric`, `factuality` |
| Hallucination check | `hallucinations_v1` | `context-faithfulness` |
| Safety check | `safety_v1` | `moderation` |
| Custom metric | Python function | `javascript` / `python` |
| Multi-turn | Yes (multiple `Invocation` in `conversation`) | Yes (multi-turn chat format) |
| Session state | `session_input.state` | provider `vars` |
| CI integration | pytest + `AgentEvaluator.evaluate()` | `promptfoo eval -o output.json` |
| Web UI | `adk web` | `promptfoo view` |
| Reporting | Text output + Vertex AI (paid) | Built-in HTML dashboard |

---

## Quick reference

```bash
# Run all test files in a directory
adk eval evals/adk/pulse_mcp_eval_agent/ evals/adk/

# Run a specific evalset
adk eval evals/adk/pulse_mcp_eval_agent/ evals/adk/pulse-mcp-full.evalset.json

# Interactive web UI
adk web evals/adk/pulse_mcp_eval_agent/
```

```python
# pytest
import pytest
from google.adk.evaluation.agent_evaluator import AgentEvaluator

@pytest.mark.asyncio
async def test_evals():
    await AgentEvaluator.evaluate(
        agent_module="evals/adk/pulse_mcp_eval_agent",
        eval_dataset_file_path_or_dir="evals/adk/",
        num_runs=1,
    )
```

```json
// test_config.json — minimal recommended config
{
  "criteria": {
    "tool_trajectory_avg_score": 1.0,
    "response_match_score": 0.5
  }
}
```
