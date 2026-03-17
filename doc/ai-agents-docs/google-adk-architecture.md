# Google ADK Architecture — How It Works Under the Hood

*A deep-dive reference based on the ADK v1.26.0 source code.*

---

## Table of Contents

1. [High-Level Overview](#1-high-level-overview)
2. [Request Lifecycle](#2-request-lifecycle)
3. [Core Classes](#3-core-classes)
4. [Agent Types and Hierarchy](#4-agent-types-and-hierarchy)
5. [Agent Communication](#5-agent-communication)
6. [The Streaming Pipeline (yield)](#6-the-streaming-pipeline)
7. [Callbacks and Hooks](#7-callbacks-and-hooks)
8. [Agent Transfer and AutoFlow](#8-agent-transfer-and-autoflow)
9. [Session Services and Concurrency](#9-session-services-and-concurrency)
10. [Plugins](#10-plugins)

---

## 1. High-Level Overview

Google ADK (Agent Development Kit) is a framework for building multi-agent AI systems. At its core, it connects three things:

```
┌─────────┐     ┌────────┐     ┌─────────┐
│  User   │────→│ Runner │────→│ Agents  │
│ Message │     │        │     │  (tree) │
└─────────┘     └────┬───┘     └────┬────┘
                     │              │
                ┌────┴───┐    ┌────┴────┐
                │Session │    │  LLM    │
                │Service │    │(Gemini) │
                └────────┘    └─────────┘
```

- **Runner**: The orchestrator. Receives a user message, finds the right agent, executes it, persists events.
- **Agents**: Stateless definitions that describe behavior. They don't store data — all mutable state lives in Session.
- **Session**: The persistent conversation state. Stores events (message history) and key-value state.
- **Events**: The universal data unit. Every action (user message, LLM response, tool call, state change) is an Event.

### The Core Principle

Agents are stateless config. Sessions hold all state. Events are the atoms of communication. The Runner glues everything together.

---

## 2. Request Lifecycle

When a user sends a message, here is the exact sequence from the source code:

```
User sends HTTP request
         │
         ▼
┌─ Runner.run_async() ─────────────────────────────────────────────┐
│                                                                   │
│  1. Get or create Session from SessionService                     │
│  2. Create InvocationContext (fresh per request)                   │
│  3. Append user message as Event to session                       │
│  4. Find agent to run (root or resumed sub-agent)                 │
│                                                                   │
│  ┌─ _exec_with_plugin() ───────────────────────────────────────┐  │
│  │                                                              │  │
│  │  Step 1: before_run plugins (can abort entire invocation)    │  │
│  │                                                              │  │
│  │  Step 2: execute_fn → agent.run_async(ctx)                   │  │
│  │          │                                                   │  │
│  │          ├─ before_agent_callback (can skip agent)           │  │
│  │          ├─ _run_async_impl (agent-specific logic)           │  │
│  │          │    ├─ before_model_callback (can skip LLM)        │  │
│  │          │    ├─ LLM call → Gemini API                       │  │
│  │          │    ├─ after_model_callback (can modify response)  │  │
│  │          │    ├─ Tool calls if requested                     │  │
│  │          │    └─ yield Event for each action                 │  │
│  │          └─ after_agent_callback (can append extra content)  │  │
│  │                                                              │  │
│  │  For each Event:                                             │  │
│  │    ├─ session_service.append_event() → persist to DB         │  │
│  │    ├─ on_event plugin callback (can modify event)            │  │
│  │    └─ yield event → flows to caller                          │  │
│  │                                                              │  │
│  │  Step 4: after_run plugins (cleanup, no events)              │  │
│  │                                                              │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                   │
│  5. Run event compaction if configured                             │
│                                                                   │
└───────────────────────────────────────────────────────────────────┘
         │
         ▼
Events streamed to caller (e.g. SSE to browser)
```

### The Invocation Hierarchy

The source code defines clear nesting levels:

```
┌─────────────────────── invocation ──────────────────────────┐
┌──────────── llm_agent_call_1 ────────────┐ ┌─ agent_call_2 ─┐
┌──── step_1 ────────┐ ┌───── step_2 ──────┐
[call_llm] [call_tool] [call_llm] [transfer]
```

- **Invocation**: One user message → final response. Handled by `runner.run_async()`.
- **Agent Call**: One agent executing. Handled by `agent.run_async()`.
- **Step**: One LLM call + its tool calls (if any). An LlmAgent can have multiple steps in a loop.

---

## 3. Core Classes

### 3.1 Session

*Source: `google/adk/sessions/session.py`*

The persistent conversation container. Everything mutable lives here.

```python
class Session(BaseModel):
    id: str                          # unique session identifier
    app_name: str                    # which app this session belongs to
    user_id: str                     # which user owns this session
    state: dict[str, Any]            # key-value state (agent communication)
    events: list[Event]              # full conversation history
    last_update_time: float          # for concurrency detection
```

A session is identified by the tuple `(app_name, user_id, session_id)`.

**State scopes** — keys with special prefixes are shared across sessions:

| Prefix | Scope | Example |
|---|---|---|
| (none) | Session-local | `"plan"`, `"result_b"` |
| `app:` | Shared across all sessions in the app | `"app:global_config"` |
| `user:` | Shared across all sessions for a user | `"user:preferences"` |
| `temp:` | Not persisted (cleared after each event) | `"temp:scratch"` |

### 3.2 InvocationContext (ctx)

*Source: `google/adk/agents/invocation_context.py`*

Created fresh for every `runner.run_async()` call. Holds everything needed for a single invocation.

```python
class InvocationContext(BaseModel):
    invocation_id: str                    # unique ID for this invocation
    agent: BaseAgent                      # the agent currently running
    session: Session                      # the session (shared state)
    user_content: Optional[Content]       # the original user message

    # Services
    session_service: BaseSessionService
    artifact_service: Optional[BaseArtifactService]
    memory_service: Optional[BaseMemoryService]
    credential_service: Optional[BaseCredentialService]

    # Control flow
    end_invocation: bool = False          # set True to abort everything
    branch: Optional[str] = None          # isolation branch for parallel agents

    # Agent lifecycle tracking
    agent_states: dict[str, dict]         # per-agent checkpoint state
    end_of_agents: dict[str, bool]        # which agents have finished

    # Runtime config
    run_config: Optional[RunConfig]       # max_llm_calls, modalities, etc.
    plugin_manager: PluginManager
```

Key behaviors:
- **`end_invocation = True`**: Any callback or tool can set this to immediately stop the entire pipeline.
- **`branch`**: Used by `ParallelAgent` to isolate sub-agent conversations so they don't see each other's history.
- **`agent_states` / `end_of_agents`**: Used for pause/resume (resumability) to track where each agent left off.
- **`increment_llm_call_count()`**: Enforces `RunConfig.max_llm_calls` limit to prevent runaway loops.

### 3.3 Event

*Source: `google/adk/events/event.py`*

The universal atom of communication. Every action in ADK produces an Event.

```python
class Event(LlmResponse):
    invocation_id: str          # which invocation produced this
    author: str                 # who created it: "user", "model", or agent name
    actions: EventActions       # side effects (state changes, transfers, etc.)
    branch: Optional[str]       # which parallel branch (if any)
    content: Optional[Content]  # the actual text/data payload
    partial: Optional[bool]     # True = streaming chunk, not yet complete
    timestamp: float            # when it was created
```

Examples of events in a typical flow:

| # | Author | Content | Actions |
|---|---|---|---|
| 1 | `user` | "Analyze my app" | — |
| 2 | `planner` | "I'll analyze performance and UX" | `state_delta: {"plan": "..."}` |
| 3 | `executor` | "Performance results: ..." | `state_delta: {"results": "..."}` |
| 4 | `summary` | "Here's the summary" | `state_delta: {"summary": "..."}` |
| 5 | `report` | "# Report\n..." | — |

### 3.4 EventActions

*Source: `google/adk/events/event_actions.py`*

Side effects attached to an Event. This is how events carry more than just text.

```python
class EventActions(BaseModel):
    state_delta: dict[str, object]           # state changes to apply
    artifact_delta: dict[str, int]           # artifact version changes
    transfer_to_agent: Optional[str]         # hand off to another agent
    escalate: Optional[bool]                 # escalate to parent (LoopAgent exit)
    end_of_agent: Optional[bool]             # this agent is done
    agent_state: Optional[dict]              # checkpoint for resume
    requested_auth_configs: dict[str, ...]   # OAuth requests from tools
    requested_tool_confirmations: dict[...]  # human-in-the-loop confirmations
    compaction: Optional[EventCompaction]    # compressed history
```

The most commonly used:
- **`state_delta`**: Written via `output_key` or `callback_context.state["key"] = value`. Applied to session state when the event is persisted.
- **`transfer_to_agent`**: Set by `AutoFlow` when the LLM calls `transfer_to_agent("agent_name")`.
- **`escalate`**: Used by `LoopAgent` — when a sub-agent sets this, the loop stops.

### 3.5 CallbackContext (Context)

*Source: `google/adk/agents/context.py`*

The object passed to all callbacks. Extends `ReadonlyContext` with write capabilities.

```python
class Context(ReadonlyContext):
    @property
    def state(self) -> State:        # read/write session state (delta-aware)
    @property
    def actions(self) -> EventActions # attach side effects

    # Services
    async def load_artifact(filename, version=None) -> Part
    async def save_artifact(filename, artifact) -> int
    async def save_credential(auth_config)
    async def load_credential(auth_config) -> AuthCredential
    async def search_memory(query) -> SearchMemoryResponse
    async def add_session_to_memory()

    # Read-only (inherited)
    @property
    def user_content -> Content      # original user message
    @property
    def invocation_id -> str
    @property
    def agent_name -> str
    @property
    def session -> Session
    @property
    def user_id -> str
```

The `State` object is delta-aware — writes go to both the current value and a pending delta that gets committed when the event is persisted:

```python
class State:
    def __setitem__(self, key, value):
        self._value[key] = value   # immediate read-back
        self._delta[key] = value   # pending commit to DB
```

---

## 4. Agent Types and Hierarchy

### 4.1 BaseAgent

The abstract base class. All agents inherit from this.

```python
class BaseAgent(BaseModel):
    name: str
    description: str = ''
    sub_agents: list[BaseAgent] = []
    before_agent_callback: Optional[BeforeAgentCallback]
    after_agent_callback: Optional[AfterAgentCallback]

    # Must implement:
    async def _run_async_impl(self, ctx) -> AsyncGenerator[Event, None]: ...
```

The `run_async()` method (which you call) wraps `_run_async_impl()` with callbacks:

```
run_async(ctx):
    before_agent_callback → can skip
    _run_async_impl(ctx)  → agent-specific logic
    after_agent_callback  → can append content
```

### 4.2 LlmAgent

The core agent type. Calls an LLM (Gemini) and optionally uses tools.

```python
class LlmAgent(BaseAgent):
    model: str                              # e.g. "gemini-2.5-flash"
    instruction: str | InstructionProvider  # system prompt (supports {placeholders})
    description: str                        # used for routing by parent agents
    tools: list[ToolUnion]                  # functions the LLM can call
    output_key: Optional[str]               # write response to session state
    output_schema: Optional[type[BaseModel]]# force structured JSON output

    # Transfer control
    disallow_transfer_to_parent: bool = False
    disallow_transfer_to_peers: bool = False

    # 6 callbacks (in addition to 2 from BaseAgent)
    before_model_callback   # before LLM call
    after_model_callback    # after LLM call
    on_model_error_callback # on LLM error
    before_tool_callback    # before tool call
    after_tool_callback     # after tool call
    on_tool_error_callback  # on tool error
```

**Internal loop**: An LlmAgent runs in a loop of "steps" until it produces a final response or transfers:

```
while True:
    response = call_llm()
    if response is text → yield event, DONE
    if response is transfer → yield event, DONE
    if response is tool_call:
        result = call_tool()
        feed result back to LLM → next iteration
```

### 4.3 SequentialAgent

Runs sub-agents one after another. No LLM call for orchestration — pure Python iteration.

```python
class SequentialAgent(BaseAgent):
    async def _run_async_impl(self, ctx):
        for sub_agent in self.sub_agents:
            async for event in sub_agent.run_async(ctx):
                yield event
```

### 4.4 ParallelAgent

Runs sub-agents concurrently using `asyncio.Task`. Each sub-agent gets its own `branch` so they don't see each other's conversation history.

```python
class ParallelAgent(BaseAgent):
    async def _run_async_impl(self, ctx):
        tasks = []
        for sub_agent in self.sub_agents:
            sub_ctx = create_branch_context(ctx, sub_agent)
            task = asyncio.create_task(collect_events(sub_agent, sub_ctx))
            tasks.append(task)
        results = await asyncio.gather(*tasks)
        for events in results:
            for event in events:
                yield event
```

### 4.5 LoopAgent

Runs its sub-agents in a loop until one of them sets `escalate=True` in their event actions, or `max_iterations` is reached.

```python
class LoopAgent(BaseAgent):
    max_iterations: Optional[int] = None

    async def _run_async_impl(self, ctx):
        iteration = 0
        while max_iterations is None or iteration < max_iterations:
            for sub_agent in self.sub_agents:
                async for event in sub_agent.run_async(ctx):
                    yield event
                    if event.actions.escalate:
                        return  # exit the loop
            iteration += 1
```

### 4.6 Custom Agent (BaseAgent subclass)

For orchestration logic that doesn't fit the built-in types:

```python
class MyCustomAgent(BaseAgent):
    async def _run_async_impl(self, ctx):
        # Any Python logic to decide what to run
        planner = self.find_sub_agent("planner")
        async for event in planner.run_async(ctx):
            yield event

        if ctx.session.state.get("needs_analysis"):
            analyzer = self.find_sub_agent("analyzer")
            async for event in analyzer.run_async(ctx):
                yield event
```

### Agent Type Summary

| Agent | LLM Calls for Orchestration | Use Case |
|---|---|---|
| `LlmAgent` | 1+ (the agent IS the LLM) | Any task requiring intelligence |
| `SequentialAgent` | 0 | Fixed pipelines (A → B → C) |
| `ParallelAgent` | 0 | Independent tasks run concurrently |
| `LoopAgent` | 0 | Iterative refinement |
| Custom `BaseAgent` | 0 | Complex conditional/dynamic routing |

---

## 5. Agent Communication

Agents in ADK communicate through **session state**, not by passing messages directly.

### 5.1 Writing: `output_key`

When an agent has `output_key="plan"`, its final text response is automatically written to `session.state["plan"]`:

```python
planner = LlmAgent(
    name="planner",
    output_key="plan",   # LLM's text response → state["plan"]
    ...
)
```

### 5.2 Reading: `{placeholder}` in Instructions

Other agents read from state using `{key}` placeholders in their `instruction` string:

```python
executor = LlmAgent(
    name="executor",
    instruction="""\
    You receive an analysis plan from the Planner.

    ## Plan
    {plan}          ← replaced with state["plan"] at runtime
    """,
    ...
)
```

ADK replaces `{plan}` with the actual value from `session.state["plan"]` before sending the prompt to the LLM.

### 5.3 Writing from Callbacks/Tools

Callbacks and tools can write to state directly:

```python
def my_callback(callback_context):
    callback_context.state["my_flag"] = True   # writes to state + delta
    return None

def my_tool(tool_context):
    tool_context.state["result"] = {"score": 95}
    return {"status": "done"}
```

### 5.4 State Scopes

| Prefix | Shared Across | Persisted? |
|---|---|---|
| (none) | Only this session | Yes |
| `app:` | All sessions in the app | Yes |
| `user:` | All sessions for this user | Yes |
| `temp:` | Not shared | No (stripped before persistence) |

### 5.5 Communication Flow Example

```
planner (output_key="plan")
    │
    │  state["plan"] = "Analyze performance and UX"
    │
    ▼
executor (instruction includes {plan})
    │
    │  Sees: "Analyze performance and UX"
    │  state["results"] = "Performance is good, UX needs work"
    │
    ▼
summary (instruction includes {results})
    │
    │  Sees: "Performance is good, UX needs work"
    │  state["summary"] = "Overall healthy with UX improvements needed"
    │
    ▼
report (instruction includes {summary})
```

---

## 6. The Streaming Pipeline

ADK uses Python's `async yield` to stream events through a chain of generators, so the user sees results incrementally rather than waiting for the entire pipeline to finish.

### 6.1 How `yield` Works in ADK

Each layer consumes events from the layer below and re-yields them upward:

```
LlmAgent._run_async_impl:
    response = await call_gemini()
    yield Event(content=response)          ← produces event

SequentialAgent._run_async_impl:
    for sub_agent in self.sub_agents:
        async for event in sub_agent.run_async(ctx):
            yield event                    ← passes through

Runner._exec_with_plugin:
    async for event in execute_fn(ctx):
        await session_service.append_event(session, event)  ← persists
        yield event                        ← passes through

Your route handler:
    async for event in runner.run_async(...):
        yield f"data: {event_to_sse(event)}"  ← sends to browser
```

### 6.2 Why This Matters

Without `yield` (batch approach):
```
[Planner runs 3s] [Executor runs 5s] [Summary runs 2s] [Report runs 2s]
                                                         ↓
                                           User sees everything after 12s
```

With `yield` (streaming approach):
```
[Planner runs 3s → events stream to user]
          [Executor runs 5s → events stream to user]
                    [Summary runs 2s → events stream]
                              [Report runs 2s → done]
↓           ↓           ↓           ↓
User sees chunks arrive continuously over 12s
```

### 6.3 The `Aclosing` Wrapper

ADK wraps every generator consumption with `Aclosing` (Python's `contextlib.aclosing`):

```python
async with Aclosing(agent.run_async(ctx)) as agen:
    async for event in agen:
        yield event
```

This guarantees the generator's cleanup code runs even if an exception occurs or the consumer stops early. Without it, resources (DB connections, API sessions) could leak.

### 6.4 Concurrency with `async/await`

Python is single-threaded but async. When one agent `await`s a Gemini API call (network I/O taking 2-10 seconds), the event loop can serve other requests:

```
Request A: [prep] → await Gemini ─────────────→ [process] → await Gemini ───→
Request B:    [prep] → await Gemini ─────────────→ [process] → await Gemini →
                ↑                                    ↑
          A is waiting, so B runs              B is waiting, so A runs
```

Both requests make progress concurrently on a single thread.

---

## 7. Callbacks and Hooks

ADK has 8 callbacks organized in 3 tiers, plus a dynamic instruction provider.

### 7.1 Agent-Level (BaseAgent — all agent types)

| Callback | Signature | Return Effect |
|---|---|---|
| `before_agent_callback` | `(callback_context) → Optional[Content]` | Return Content → **skip entire agent** |
| `after_agent_callback` | `(callback_context) → Optional[Content]` | Return Content → **append extra event** |

### 7.2 Model-Level (LlmAgent only)

| Callback | Signature | Return Effect |
|---|---|---|
| `before_model_callback` | `(callback_context, llm_request) → Optional[LlmResponse]` | Return response → **skip LLM call**. Can mutate `llm_request`. |
| `after_model_callback` | `(callback_context, llm_response) → Optional[LlmResponse]` | Return response → **override LLM response** |
| `on_model_error_callback` | `(callback_context, llm_request, error) → Optional[LlmResponse]` | Return response → **suppress error** |

### 7.3 Tool-Level (LlmAgent only)

| Callback | Signature | Return Effect |
|---|---|---|
| `before_tool_callback` | `(tool, args, tool_context) → Optional[dict]` | Return dict → **skip tool, use as result** |
| `after_tool_callback` | `(tool, args, tool_context, tool_response) → Optional[dict]` | Return dict → **override tool result** |
| `on_tool_error_callback` | `(tool, args, tool_context, error) → Optional[dict]` | Return dict → **suppress error** |

### 7.4 Execution Flow with Callbacks

```
before_agent_callback ──returns Content?──→ SKIP AGENT
        │ (None)
        ▼
   ┌─ LLM Loop ──────────────────────────────────────┐
   │                                                  │
   │  before_model_callback ──returns?──→ skip LLM    │
   │          │ (None)                                │
   │          ▼                                       │
   │     [Gemini API Call]                            │
   │          │                                       │
   │      error? ──→ on_model_error_callback          │
   │          │                                       │
   │          ▼                                       │
   │  after_model_callback ──returns?──→ override     │
   │          │                                       │
   │    tool call requested?                          │
   │          │                                       │
   │  before_tool_callback ──returns?──→ skip tool    │
   │          │ (None)                                │
   │          ▼                                       │
   │     [Tool Execution]                             │
   │          │                                       │
   │      error? ──→ on_tool_error_callback           │
   │          │                                       │
   │          ▼                                       │
   │  after_tool_callback ──returns?──→ override      │
   │          │                                       │
   │  (loop back for next LLM step)                   │
   └──────────────────────────────────────────────────┘
        │
        ▼
after_agent_callback ──returns Content?──→ append extra event
```

### 7.5 Callback Lists

All callbacks accept a single function or a list. With a list, callbacks execute in order until one returns a non-None value:

```python
agent = LlmAgent(
    before_model_callback=[check_cache, add_context, rate_limit],
    ...
)
# check_cache runs first. If it returns None, add_context runs. And so on.
```

### 7.6 InstructionProvider

Not a callback, but a callable that dynamically generates the instruction string:

```python
def dynamic_instruction(readonly_context):
    role = readonly_context.state.get("user_role", "analyst")
    return f"You are helping a {role}."

agent = LlmAgent(instruction=dynamic_instruction, ...)
```

Called every time the agent runs (before each LLM call), with a read-only view of state.

---

## 8. Agent Transfer and AutoFlow

### 8.1 How Transfer Works

When an `LlmAgent` has sub-agents (or peers), ADK automatically enables LLM-driven routing via `AutoFlow`:

```python
@property
def _llm_flow(self) -> BaseLlmFlow:
    if (self.disallow_transfer_to_parent
        and self.disallow_transfer_to_peers
        and not self.sub_agents):
        return SingleFlow()   # no transfer capability
    else:
        return AutoFlow()     # injects transfer_to_agent tool
```

`AutoFlow` does two things:
1. Injects each potential target's `name` and `description` into the LLM's context
2. Adds a `transfer_to_agent(agent_name)` function tool

The LLM reads the descriptions and decides who to transfer to.

### 8.2 Transfer Targets

From `_get_transfer_targets()`:

```python
def _get_transfer_targets(agent):
    result = []
    result.extend(agent.sub_agents)             # always: children

    if not agent.disallow_transfer_to_parent:
        result.append(agent.parent_agent)       # if allowed: parent

    if not agent.disallow_transfer_to_peers:
        result.extend(sibling agents)           # if allowed: siblings

    return result
```

### 8.3 Transfer Direction Controls

| Setting | Can Transfer To |
|---|---|
| Both `False` (defaults) | Parent + Peers + Children |
| `disallow_transfer_to_peers=True` | Parent + Children only (hub-and-spoke) |
| `disallow_transfer_to_parent=True` | Peers + Children only (one-way, risky) |
| Both `True` + no children | Nobody → `SingleFlow` (no transfer tool) |

### 8.4 Transfer is a One-Way Handoff

When transfer happens, the current agent yields the sub-agent's events directly. It does NOT get them back:

```python
# In base_llm_flow.py
transfer_to_agent = function_response_event.actions.transfer_to_agent
if transfer_to_agent:
    agent_to_run = self._get_agent_to_run(invocation_context, transfer_to_agent)
    async with Aclosing(agent_to_run.run_async(invocation_context)) as agen:
        async for event in agen:
            yield event    # events go directly to caller, not back to parent
```

The parent agent is done. The sub-agent responds directly to the user.

### 8.5 Transfer Back to Parent

If `disallow_transfer_to_parent=False` (the default), ADK injects this instruction into the sub-agent:

> "If neither you nor the other agents are best for the question, transfer to your parent agent {parent_name}."

This allows a sub-agent to voluntarily hand control back — but it's LLM-driven, not deterministic.

---

## 9. Session Services and Concurrency

### 9.1 Session Service Implementations

| Service | Storage | Locking | Production Use |
|---|---|---|---|
| `InMemorySessionService` | Python dicts | None | Development/testing only |
| `DatabaseSessionService` (SQLite) | File-based DB | No row-level locks | Single-instance only |
| `DatabaseSessionService` (PostgreSQL/MySQL) | Shared DB | `asyncio.Lock` + `SELECT FOR UPDATE` | Production multi-instance |

### 9.2 Concurrency: Different Sessions

Completely isolated. Each request gets its own `Session` object, its own `InvocationContext`. Agents are stateless singletons — safe to share.

```
Request A (session_X)          Request B (session_Y)
       │                              │
  InvocationContext_A            InvocationContext_B
  session=X, state={}           session=Y, state={}
       │                              │
  [Fully independent]           [Fully independent]
```

### 9.3 Concurrency: Same Session

Two requests to the same session simultaneously:

**InMemorySessionService**: No locks. Race conditions on `session.state`. Data corruption possible.

**DatabaseSessionService**: Three layers of protection:

1. **In-process asyncio.Lock** per session — serializes `append_event` within one Python process
2. **SQL `SELECT ... FOR UPDATE`** — row-level DB lock for cross-process safety
3. **Stale detection** — if `last_update_time` in DB is newer than the in-memory session, reload from DB

Events from both invocations are serialized at the event level but interleaved in the session history.

### 9.4 Distributed Deployment

For multiple server instances behind a load balancer:

- Use `DatabaseSessionService` with PostgreSQL/MySQL (not SQLite, not InMemory)
- State consistency is handled by the DB + row-level locking
- Consider preventing same-session concurrent requests at the application level to avoid confusing interleaved event history

---

## 10. Plugins

Plugins are global hooks that run at the Runner level (above individual agent callbacks).

### 10.1 Plugin Lifecycle

```
_exec_with_plugin():
    ├── before_run  → can abort entire invocation (return Content)
    ├── [agent execution]
    │     for each event:
    │       └── on_event → can modify/replace individual events
    └── after_run   → cleanup (no events emitted)
```

### 10.2 Plugin vs Agent Callback

| Aspect | Plugin | Agent Callback |
|---|---|---|
| Scope | Entire invocation (all agents) | Single agent |
| Registered on | `Runner` / `App` | Individual agent |
| Access to | `InvocationContext` | `CallbackContext` |
| Can abort invocation | Yes (`before_run`) | Yes (`before_agent` → skip one agent) |
| Can modify events | Yes (`on_event`) | No (callbacks return override content) |

Plugins are for cross-cutting concerns (auth, logging, rate limiting). Callbacks are for agent-specific behavior.

---

## Quick Reference: Where Things Live

| Concept | Stateless (Definition) | Stateful (Runtime) |
|---|---|---|
| Agent behavior | `LlmAgent(instruction=..., tools=...)` | — |
| Conversation history | — | `Session.events` |
| Key-value data | — | `Session.state` |
| Per-invocation context | — | `InvocationContext` |
| Agent communication | `output_key` / `{placeholder}` | `Session.state` |
| Event persistence | — | `SessionService.append_event()` |
| Routing decisions | `description` field | LLM at runtime |

---

## Related Documents

- [Sequential Pipeline Pattern](./sequential-pipeline-pattern.md)
- [Coordinator / Dispatcher Pattern](./coordinator-dispatcher-pattern.md)
- [Parallel Fan-Out Pattern](./parallel-fan-out-pattern.md)
- [Callback Conditional Skipping](./callback-conditional-skipping.md)
- [Custom Agent Pattern](./custom-agent-pattern.md)
- [Cost & Latency Analysis](./cost-latency-analysis.md)
- [Pulse AI Recommendations](./pulse-ai-recommendations.md)
