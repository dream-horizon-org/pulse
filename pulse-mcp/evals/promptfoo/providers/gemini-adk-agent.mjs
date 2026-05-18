import { LlmAgent, MCPToolset, InMemoryRunner, toStructuredEvents, EventType } from "@google/adk";

// Gemini 2.5 Flash pricing (per token). Source: Google AI pricing page.
const PRICING = {
  "gemini-2.5-flash":        { input: 0.30 / 1_000_000, output: 2.50 / 1_000_000 },
  "gemini-2.5-pro":          { input: 1.25 / 1_000_000, output: 10.00 / 1_000_000 },
  "gemini-2.0-flash":        { input: 0.10 / 1_000_000, output: 0.40 / 1_000_000 },
  "gemini-1.5-flash":        { input: 0.075 / 1_000_000, output: 0.30 / 1_000_000 },
};

function calculateCost(model, promptTokens, completionTokens) {
  const price = PRICING[model] ?? PRICING["gemini-2.5-flash"];
  return price.input * promptTokens + price.output * completionTokens;
}

const DEFAULT_INSTRUCTION =
  "You are an assistant for Pulse, a real-time mobile and web observability platform. " +
  "You have access to Pulse MCP tools. When the user makes a request, call the appropriate " +
  "tool(s) to answer it. Call only the tools that are needed. " +
  'Use projectId "fancode" unless the user specifies otherwise.';

function buildToolsets(servers) {
  return servers.map((server) => {
    if (server.command) {
      return new MCPToolset({
        type: "StdioConnectionParams",
        serverParams: {
          command: server.command,
          args: server.args ?? [],
          // merge process.env so the child process inherits PATH etc.
          env: { ...process.env, ...(server.env ?? {}) },
        },
      });
    }
    // HTTP / SSE transport
    return new MCPToolset({
      type: "StreamableHTTPConnectionParams",
      url: server.url,
      ...(server.headers ? { header: server.headers } : {}),
    });
  });
}

export default class GeminiAdkProvider {
  constructor(options) {
    const cfg = options?.config ?? {};

    this._model = cfg.model ?? "gemini-2.5-flash";
    this._instruction = cfg.instruction ?? DEFAULT_INSTRUCTION;
    this._maxLlmCalls = cfg.max_turns ?? 10;

    const servers = cfg.mcp?.servers ?? [];
    if (servers.length === 0) {
      throw new Error(
        "GeminiAdkProvider: config.mcp.servers must contain at least one server"
      );
    }

    const agent = new LlmAgent({
      name: "pulse_eval_agent",
      model: this._model,
      instruction: this._instruction,
      tools: buildToolsets(servers),
    });

    // Runner is reused across all callApi() invocations — one agent instance
    // per provider config, not per test case.
    this._runner = new InMemoryRunner({ agent, appName: "pulse-eval" });
  }

  id() {
    return "gemini-adk-agent";
  }

  async callApi(prompt) {
    const toolCalls = [];
    const pendingById = new Map();
    let finalText = "";
    let promptTokens = 0;
    let completionTokens = 0;

    for await (const rawEvent of this._runner.runEphemeral({
      userId: `eval-${Date.now()}-${Math.random().toString(36).slice(2)}`,
      newMessage: { role: "user", parts: [{ text: prompt }] },
      runConfig: { maxLlmCalls: this._maxLlmCalls },
    })) {
      // Accumulate token usage from every LLM turn in the agentic loop
      if (rawEvent.usageMetadata) {
        const u = rawEvent.usageMetadata;
        promptTokens += u.promptTokenCount ?? 0;
        // thinking tokens are billed as output — match built-in Gemini provider behaviour
        completionTokens += (u.candidatesTokenCount ?? 0) + (u.thoughtsTokenCount ?? 0);
      }

      for (const event of toStructuredEvents(rawEvent)) {
        switch (event.type) {
          case EventType.TOOL_CALL: {
            const entry = {
              name: event.call.name ?? "",
              input: event.call.args ?? {},
              output: "",
              is_error: false,
            };
            toolCalls.push(entry);
            if (event.call.id) pendingById.set(event.call.id, entry);
            break;
          }

          case EventType.TOOL_RESULT: {
            const entry =
              (event.result.id && pendingById.get(event.result.id)) ||
              [...toolCalls]
                .reverse()
                .find((c) => c.name === event.result.name && c.output === "");
            if (entry) {
              entry.output = JSON.stringify(event.result.response ?? "");
              if (event.result.id) pendingById.delete(event.result.id);
            }
            break;
          }

          case EventType.CONTENT:
            finalText += event.content;
            break;

          case EventType.ERROR:
            return {
              output: toolCalls.length > 0 ? JSON.stringify(toolCalls) : "",
              error: event.error?.message ?? "unknown error",
              tokenUsage: { prompt: promptTokens, completion: completionTokens, total: promptTokens + completionTokens },  // includes thinking tokens
              cost: calculateCost(this._model, promptTokens, completionTokens),
              metadata: { toolCalls },
            };
        }
      }
    }

    return {
      // tool-call-f1 reads from `output` via extractToolNames() — it does NOT read
      // metadata.toolCalls. Return JSON-serialised toolCalls so it can parse them.
      // Fall back to finalText only when no tools were called (e.g. smoke test).
      output: toolCalls.length > 0 ? JSON.stringify(toolCalls) : finalText,
      tokenUsage: {
        prompt: promptTokens,
        completion: completionTokens,
        total: promptTokens + completionTokens,   // includes thinking tokens
      },
      cost: calculateCost(this._model, promptTokens, completionTokens),
      metadata: { toolCalls },
    };
  }
}
