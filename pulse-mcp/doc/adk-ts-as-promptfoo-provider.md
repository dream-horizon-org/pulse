# Google ADK TypeScript as a Promptfoo Provider

How to replicate `anthropic:claude-agent-sdk` behaviour using `@google/adk` TypeScript
as a custom promptfoo provider for pulse-mcp evals.

---

## How `anthropic:claude-agent-sdk` works

```yaml
providers:
  - id: anthropic:claude-agent-sdk
    config:
      mcp:
        servers:
          - command: node
            args: ['dist/index.js']
            name: pulse-mcp
            env:
              PULSE_BASE_URL: '{{env.PULSE_BASE_URL}}'
              PULSE_API_KEY: '{{env.PULSE_API_KEY}}'
      max_turns: 10
      strict_mcp_config: true
```

Internally, it:
1. Connects to the MCP server via stdio on startup
2. Receives NL prompt
3. Runs full agentic loop: model picks tool → SDK executes it via MCP → result fed back → repeat
4. Emits every tool invocation into `response.metadata.toolCalls`

Each entry in `metadata.toolCalls`:

```js
{
  id: "toolu_01...",
  name: "list_app_vitals_crash_issues",
  input: { projectId: "fancode" },
  output: "[{ groupId: 'G789', title: '...' }]",
  is_error: false,
  parentToolUseId: null
}
```

`tool-call-f1` and `trajectory:tool-used` assertions read from this array.

---

## Google ADK TypeScript equivalent

`@google/adk` v1.1.0 has the same building blocks:

| `anthropic:claude-agent-sdk` | `@google/adk` |
|---|---|
| MCP stdio connection | `MCPToolset` + `StdioConnectionParams` |
| Full agentic loop | `InMemoryRunner.runEphemeral()` |
| Tool call events | `ToolCallEvent` (`event.type === EventType.TOOL_CALL`) |
| Tool result events | `ToolResultEvent` (`event.type === EventType.TOOL_RESULT`) |
| `metadata.toolCalls` | You populate it from the event stream |

Key types (from `@google/adk` v1.1.0):

```ts
// MCPToolset connection — stdio
const toolset = new MCPToolset({
  type: 'StdioConnectionParams',
  serverParams: {               // StdioServerParameters from @modelcontextprotocol/sdk
    command: 'node',
    args: ['dist/index.js'],
    env: { PULSE_BASE_URL: '...', PULSE_API_KEY: '...' }
  }
});

// Event types emitted by runner
enum EventType {
  TOOL_CALL    = 'tool_call',   // { call: FunctionCall }
  TOOL_RESULT  = 'tool_result', // { result: FunctionResponse }
  CONTENT      = 'content',     // text delta
  THOUGHT      = 'thought',     // reasoning trace
  FINISHED     = 'finished',    // done
  ERROR        = 'error',       // runtime error
}

// Convert raw Event → structured via ADK utility
import { toStructuredEvents } from '@google/adk';
```

---

## The custom provider

```ts
// evals/promptfoo/providers/gemini-adk-agent.mts
import {
  LlmAgent,
  MCPToolset,
  InMemoryRunner,
  toStructuredEvents,
  EventType,
} from "@google/adk";
import type { StdioConnectionParams } from "@google/adk";

// Build once — shared across all callApi() invocations
function buildAgent(): LlmAgent {
  const connectionParams: StdioConnectionParams = {
    type: "StdioConnectionParams",
    serverParams: {
      command: "node",
      args: ["dist/index.js"],
      env: {
        PULSE_BASE_URL: process.env.PULSE_BASE_URL ?? "",
        PULSE_API_KEY: process.env.PULSE_API_KEY ?? "",
      },
    },
  };

  return new LlmAgent({
    name: "pulse_eval_agent",
    model: "gemini-2.5-flash",
    instruction:
      'You are an assistant for Pulse, a real-time mobile and web observability platform. ' +
      'Use projectId "fancode" unless the user specifies otherwise. ' +
      'Call only the tools that are needed.',
    tools: [new MCPToolset(connectionParams)],
  });
}

const agent = buildAgent();
const runner = new InMemoryRunner({ agent, appName: "pulse-eval" });

export default {
  id: () => "gemini-adk-agent",

  async callApi(prompt: string) {
    const toolCalls: Array<{
      name: string;
      input: Record<string, unknown>;
      output: string;
      is_error: boolean;
    }> = [];

    let finalText = "";

    for await (const rawEvent of runner.runEphemeral({
      userId: "eval",
      newMessage: { role: "user", parts: [{ text: prompt }] },
    })) {
      for (const event of toStructuredEvents(rawEvent)) {
        switch (event.type) {
          case EventType.TOOL_CALL:
            toolCalls.push({
              name: event.call.name ?? "",
              input: (event.call.args ?? {}) as Record<string, unknown>,
              output: "",        // filled in when TOOL_RESULT arrives
              is_error: false,
            });
            break;

          case EventType.TOOL_RESULT:
            // Match result back to the last call with the same name
            const match = [...toolCalls]
              .reverse()
              .find((c) => c.name === event.result.name && c.output === "");
            if (match) {
              match.output = JSON.stringify(event.result.response);
              match.is_error = false;
            }
            break;

          case EventType.CONTENT:
            finalText += event.content;
            break;

          case EventType.ERROR:
            return {
              output: "",
              error: event.error.message,
              metadata: { toolCalls },
            };
        }
      }
    }

    return {
      output: finalText || JSON.stringify(toolCalls),
      metadata: { toolCalls },
    };
  },
};
```

---

## Wire it into providers.yaml

```yaml
# evals/promptfoo/providers.yaml
- file://providers/gemini-adk-agent.mts
```

No `tools.generated.yaml` needed. No schema generation step. The agent
discovers tools directly from the running MCP server at eval time.

---

## Assertions — nothing changes

All existing test assertions work unchanged because `metadata.toolCalls`
is populated in the same shape that `tool-call-f1` and the JS assertions expect.

```yaml
# tool-call-f1 — works as-is
assert:
  - type: tool-call-f1
    threshold: 1
    metric: pulse_tool_calls_f1
    value:
      - list_app_vitals_crash_issues
      - get_app_vitals_issue_summary
      - get_app_vitals_issue_stack_traces

# JS distractor guard — works as-is
assert:
  - type: javascript
    value: |
      const tc = context.providerResponse?.metadata?.toolCalls ?? [];
      const called = tc.map(c => c.name ?? c.function?.name ?? '').filter(Boolean);
      return !called.includes('list_interactions');
```

---

## Dependent chain behaviour — fixed

With the current Gemini + generated schema approach:

```
prompt: "Explain our top crash"
Gemini response: functionCall(list_crashes), functionCall(get_summary, groupId="???")
                                                                               ↑
                                                              hallucinated — no real data
```

With the ADK agent:

```
prompt: "Explain our top crash"
Step 1: model calls list_crashes → MCP executes → returns [{ groupId: "G789", ... }]
Step 2: model calls get_summary(groupId="G789") ← real value from step 1
Step 3: model calls get_stack_traces(groupId="G789") ← same real value
```

Tool args in `metadata.toolCalls` are now correct, not guessed.

---

## What changes vs current approach

| | Current | ADK TS provider |
|---|---|---|
| Provider | `google:gemini-2.5-flash` (built-in) | `file://providers/gemini-adk-agent.mts` |
| `tools.generated.yaml` | Required (gitignored) | **Not needed** |
| Schema gen step | `yarn generate:promptfoo-tools` | **Gone** |
| PULSE_BASE_URL/KEY at eval | No (only at gen time) | **Yes — always** |
| Tool execution | Never | **Real** |
| Dependent chain args | Guessed | **Real data** |
| `tool-call-f1` assertions | Works | Works (same shape) |
| JS assertions | Works | Works (same shape) |
| Model | Gemini 2.5 Flash | Gemini 2.5 Flash |
| API key | GOOGLE_API_KEY | GOOGLE_API_KEY |

---

## Setup steps

```bash
# 1. Add @google/adk to pulse-mcp dev deps
yarn add -D @google/adk

# 2. Create the provider file
mkdir -p evals/promptfoo/providers
# → write gemini-adk-agent.mts (above)

# 3. Update providers.yaml
# → replace google:gemini-2.5-flash entry with file://providers/gemini-adk-agent.mts

# 4. Remove tools.generated.yaml from .gitignore (no longer needed)

# 5. Run
export GOOGLE_API_KEY=...
export PULSE_BASE_URL=http://localhost:8080
export PULSE_API_KEY=...
yarn promptfoo eval -c evals/promptfoo/promptfooconfig.yaml --no-cache
```

---

## One known issue: MCPToolset spawns per session

`MCPToolset` opens a new MCP connection (`node dist/index.js`) each time
`runner.runEphemeral()` is called — 64 process spawns for 64 test cases.

Mitigation: reuse the runner at module level (done above). Each `runEphemeral`
creates a new in-memory session but reuses the same agent and toolset connection
if `MCPToolset` keeps the session alive between `getTools()` calls.

If the MCP server process exits between calls, `MCPToolset` reconnects automatically
on the next `getTools()` invocation.

---

## Summary

`anthropic:claude-agent-sdk` is a built-in provider that handles all of this
internally. The Google ADK TS custom provider replicates the same behaviour:
same MCP connection, same agentic loop, same `metadata.toolCalls` shape,
same assertions. The only difference is that you own the provider code
instead of getting it from promptfoo.
