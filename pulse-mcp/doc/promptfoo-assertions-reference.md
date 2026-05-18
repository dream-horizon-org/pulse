# Promptfoo Assertions Reference

Complete reference for all promptfoo assertion types and test case patterns.
Grounded in the pulse-mcp eval suite at `evals/promptfoo/`.

---

## All assertion types

### 1. String matching

| Type | Checks | Example value |
|---|---|---|
| `equals` | Exact match | `"yes"` |
| `contains` | Substring (case-sensitive) | `'"groupId"'` |
| `icontains` | Substring (case-insensitive) | `"error"` |
| `contains-all` | All substrings present | `["tool", "crash"]` |
| `contains-any` | At least one present | `["crash", "ANR"]` |
| `icontains-all` | Case-insensitive all | |
| `icontains-any` | Case-insensitive any | |
| `starts-with` | Prefix match | `"{"` |
| `regex` | Regex pattern | `"G[0-9]+"` |
| `not-*` | Negation of any above | `not-contains`, `not-regex` |

---

### 2. Structured format

| Type | Checks |
|---|---|
| `is-json` | Valid JSON; optional JSON Schema in `value` |
| `contains-json` | Output contains JSON somewhere |
| `is-xml` | Valid XML |
| `contains-xml` | Contains XML |
| `is-sql` | Valid SQL |
| `contains-sql` | Contains SQL |
| `is-html` | Valid HTML |
| `contains-html` | Contains HTML |

---

### 3. Custom logic

| Type | How it works |
|---|---|
| `javascript` | `value: "output.length < 200"` — return bool, number, or `{pass, score, reason}` |
| `python` | Same as javascript but Python |
| `webhook` | POST output to external URL; expects `{pass}` back |

---

### 4. Tool / agent

Most relevant for pulse-mcp.

| Type | Checks | Notes |
|---|---|---|
| `tool-call-f1` | Actual tool calls vs expected multiset | `threshold: 1` = exact match |
| `is-valid-openai-tools-call` | Response is valid OpenAI tool call format | |
| `is-valid-openai-function-call` | Valid OpenAI function call format | |
| `is-valid-function-call` | Generic function call validity | |
| `trajectory:tool-used` | Specific tool was called at any point | `value: "list_projects"` |
| `trajectory:tool-args-match` | Tool was called with specific args | |
| `trajectory:tool-sequence` | Tools called in a specific order | |
| `trajectory:step-count` | Number of steps in the agentic loop | |
| `trajectory:goal-success` | LLM judges if the goal was achieved (model-graded) | |

#### `tool-call-f1` threshold values

| Threshold | Meaning |
|---|---|
| `1.0` | Exact multiset match — no missing tools, no extra tools |
| `0.5` | Partial credit acceptable |
| `0.0` | Always passes (useful for logging only) |

---

### 5. Similarity / distance

| Type | Checks |
|---|---|
| `similar` | Cosine similarity via embeddings; `threshold: 0.8` |
| `levenshtein` | Edit distance; `threshold: 5` (max edits allowed) |
| `rouge-n` | ROUGE score (recall-oriented; good for summaries) |
| `bleu` | BLEU score (precision-oriented; good for translations) |
| `gleu` | Sentence-level BLEU |
| `meteor` | Semantic matching with synonym support |

---

### 6. Model-graded (costs money, non-deterministic — use sparingly)

| Type | When to use |
|---|---|
| `llm-rubric` | Custom criteria in plain English |
| `factuality` | Check factual accuracy against a reference answer |
| `answer-relevance` | Is the response relevant to the query |
| `context-faithfulness` | Is the response grounded in provided context |
| `context-recall` | Does the context contain the needed information |
| `context-relevance` | Is the retrieved context relevant |
| `conversation-relevance` | Multi-turn conversation relevance |
| `model-graded-closedqa` | Closed-domain QA scoring |
| `g-eval` | G-Eval framework (coherence, consistency, fluency) |
| `pi` | General-purpose model-graded rubric |
| `select-best` | Compare all provider outputs, pick best |
| `max-score` | Highest score across multiple criteria |
| `trajectory:goal-success` | LLM judges if the agentic goal was achieved |

Always pin the grader model explicitly:

```yaml
defaultTest:
  options:
    provider: google:gemini-2.5-flash
```

---

### 7. Safety / classification

| Type | Checks |
|---|---|
| `is-refusal` | Model refused the prompt |
| `moderation` | OpenAI moderation API check |
| `classifier` | HuggingFace text classification model |
| `guardrails` | Security/safety guardrails |

---

### 8. Performance

| Type | Checks |
|---|---|
| `cost` | Max cost in dollars; `threshold: 0.01` |
| `latency` | Max response time in ms; `threshold: 5000` |
| `perplexity` | Model confidence (lower = more confident) |
| `perplexity-score` | Normalized perplexity |

---

### 9. Tracing (OpenTelemetry)

| Type | Checks |
|---|---|
| `trace-span-count` | Number of spans in the trace |
| `trace-span-duration` | Duration of a specific span |
| `trace-error-spans` | No error spans present |
| `skill-used` | Specific skill was invoked |

---

### 10. Special

| Type | Use |
|---|---|
| `human` | Manual grading via the promptfoo web UI |
| `select-best` | Pick best response across multiple providers |

---

### Assertion options

All assertion types accept these optional fields:

```yaml
assert:
  - type: icontains
    value: "expected text"
    weight: 2          # relative importance for scoring (default: 1)
    threshold: 0.8     # type-specific: min score for graded; max for cost/latency
    metric: relevance  # named metric shown in the report
```

---

## Test case patterns

### A — Single tool, unambiguous

```yaml
- description: "EVAL-001 — list_projects [single]"
  vars:
    prompt: "List all the projects I have access to."
  assert:
    - type: tool-call-f1
      threshold: 1
      metric: pulse_tool_calls_f1
      value:
        - list_projects
```

### B — Multi-step chain (exact sequence)

```yaml
- description: "EVAL-016 — top crash investigation [multi-step]"
  vars:
    prompt: "Explain our top crash from last week for project fancode."
  assert:
    - type: tool-call-f1
      threshold: 1
      metric: pulse_tool_calls_f1
      value:
        - list_app_vitals_crash_issues
        - get_app_vitals_issue_summary
        - get_app_vitals_issue_stack_traces
```

### C — Multi-step chain (ordered but extras allowed)

```yaml
assert:
  - type: trajectory:tool-sequence
    value:
      - list_app_vitals_crash_issues
      - get_app_vitals_issue_summary
```

### D — Tool was called at least once (subset check)

```yaml
assert:
  - type: trajectory:tool-used
    value: list_session_replays
```

### E — Tool called with specific args

```yaml
assert:
  - type: trajectory:tool-args-match
    value:
      name: get_interaction_root_cause
      args:
        projectId: "fancode"
```

### F — Must NOT call certain tools (distractor guard)

```yaml
assert:
  - type: javascript
    value: |
      const tc = context.providerResponse?.metadata?.toolCalls ?? [];
      const called = tc.map(c => c.name ?? c.function?.name ?? '').filter(Boolean);
      const mustNot = ['list_interactions', 'list_app_vitals_crash_issues'];
      const violated = mustNot.filter(t => called.includes(t));
      if (violated.length > 0) return { pass: false, score: 0, reason: 'must_not_pick: ' + violated.join(', ') };
      return { pass: true, score: 1, reason: 'called: ' + called.join(', ') };
```

### G — Acceptable alternatives with weighted preference

```yaml
assert:
  - type: javascript
    value: |
      const tc = context.providerResponse?.metadata?.toolCalls ?? [];
      const called = tc.map(c => c.name ?? c.function?.name ?? '').filter(Boolean);
      const hasPreferred = called.includes('search_events');
      const hasAlternate = called.includes('list_event_definitions');
      if (!hasPreferred && !hasAlternate) return { pass: false, score: 0, reason: 'neither tool called' };
      return { pass: true, score: hasPreferred ? 1.0 : 0.9, reason: 'called: ' + called.join(', ') };
```

### H — Zero tool calls acceptable (clarifying question)

```yaml
assert:
  - type: javascript
    value: |
      const tc = context.providerResponse?.metadata?.toolCalls ?? [];
      const called = tc.map(c => c.name ?? c.function?.name ?? '').filter(Boolean);
      if (called.length === 0) return { pass: true, score: 1, reason: 'model asked clarifying question' };
      if (called.includes('get_interaction_error_rate')) return { pass: true, score: 1, reason: 'correct tool' };
      return { pass: false, score: 0, reason: 'wrong tool: ' + called.join(', ') };
```

### I — Pagination (same tool called N times)

```yaml
assert:
  - type: javascript
    value: |
      const tc = context.providerResponse?.metadata?.toolCalls ?? [];
      const called = tc.map(c => c.name ?? c.function?.name ?? '').filter(Boolean);
      const sessionCalls = called.filter(t => t === 'list_session_replays').length;
      if (sessionCalls === 0) return { pass: false, score: 0, reason: 'list_session_replays never called' };
      if (sessionCalls === 1) return { pass: false, score: 0.5, reason: 'only one page fetched' };
      return { pass: true, score: 1, reason: 'list_session_replays called ' + sessionCalls + ' times' };
```

### J — Response content validation

```yaml
assert:
  - type: is-json
  - type: javascript
    value: "JSON.parse(output).crashes.length > 0"
  - type: not-contains
    value: '"error"'
```

### K — Response content with JSON Schema

```yaml
assert:
  - type: is-json
    value:
      type: object
      required: [groupId, title, affectedUsers]
      properties:
        groupId: { type: string }
        affectedUsers: { type: number }
```

### L — LLM quality rubric

```yaml
assert:
  - type: llm-rubric
    threshold: 0.8
    value: |
      The response explains the root cause clearly.
      It references specific interaction or crash names from the tool results.
      It does not fabricate metric values not present in the tool responses.
```

### M — Faithfulness (inline context in rubric)

```yaml
assert:
  - type: llm-rubric
    value: |
      The response only states facts from this tool output:
      "{{tool_output}}"
      It does not add, infer, or fabricate any claims.
```

### N — Cost + latency gate (shared via defaultTest)

```yaml
defaultTest:
  assert:
    - type: cost
      threshold: 0.005
    - type: latency
      threshold: 8000
    - type: not-icontains
      value: "As an AI"
```

### O — Transform output before assertions

```yaml
- description: "Parses JSON from markdown-fenced output"
  options:
    transform: "output.replace(/```json\\n?|```/g, '').trim()"
  assert:
    - type: is-json
```

### P — Weighted scoring across multiple criteria

```yaml
assert:
  - type: llm-rubric
    value: "Technically accurate"
    weight: 3
    metric: accuracy
  - type: llm-rubric
    value: "Concise — under 200 words"
    weight: 1
    metric: conciseness
  - type: javascript
    value: "output.split(' ').length <= 200"
    weight: 1
    metric: word_count
```

---

## Test case categories for pulse-mcp

| Category | # Cases | Assertion pattern |
|---|---|---|
| Single tool, unambiguous | 15 | `tool-call-f1 threshold: 1` |
| Multi-step chain | 10 | `tool-call-f1 threshold: 1` |
| Semantic traps | 15 | `tool-call-f1 threshold: 1` |
| Distractor traps | 10 | `tool-call-f1` or `javascript` |
| Disambiguation | 8 | `tool-call-f1 threshold: 1` |
| Scenarios | 6 | `tool-call-f1` or `javascript` |
| **Total** | **64** | |

### When to use `tool-call-f1` vs `javascript`

Use `tool-call-f1` when the expected tool set is fixed and exact.

Use `javascript` when:
- There are acceptable alternate tools (partial credit)
- A tool must NOT be called (distractor guard)
- Zero tool calls is a valid response (ambiguous prompt)
- The same tool must be called N times (pagination)
- You need both a must-call and a must-not-call check in the same test

---

## Quick reference card

```yaml
# Exact tool set
- type: tool-call-f1
  threshold: 1
  value: [tool_a, tool_b]

# Tool must appear
- type: trajectory:tool-used
  value: tool_a

# Tools in order
- type: trajectory:tool-sequence
  value: [tool_a, tool_b]

# Custom scoring
- type: javascript
  value: |
    const called = (context.providerResponse?.metadata?.toolCalls ?? [])
      .map(c => c.name ?? c.function?.name ?? '');
    return { pass: called.includes('tool_a'), score: 1, reason: called.join(', ') };

# JSON response shape
- type: is-json
  value:
    type: object
    required: [id, name]

# Quality rubric
- type: llm-rubric
  threshold: 0.8
  value: "Response is accurate and references specific data from tool results"

# Cost gate (in defaultTest)
- type: cost
  threshold: 0.005
```
