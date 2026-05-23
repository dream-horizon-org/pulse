#!/usr/bin/env node
import { createServer } from "node:http";
import { randomUUID } from "node:crypto";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { registerProjectTools } from "./tools/projects.js";
import {
  registerRegisterTool,
  registerActiveCategories,
  resetRegisteredCategories,
} from "./tools/register.js";
import { exchangeApiKeyForTokens, saveCredentials } from "./auth.js";

const baseUrl = process.env.PULSE_BASE_URL;
if (!baseUrl) throw new Error("PULSE_BASE_URL env var is required");

const apiKey = process.env.PULSE_API_KEY;
if (!apiKey) throw new Error("PULSE_API_KEY env var is required");

try {
  process.stderr.write("Exchanging API key for tokens...\n");
  const creds = await exchangeApiKeyForTokens(baseUrl, apiKey);
  saveCredentials(creds);
  process.stderr.write("Authentication successful.\n");
} catch (e) {
  process.stderr.write(`Failed to exchange API key: ${e}\n`);
  process.exit(1);
}

const port = parseInt(process.env.PORT ?? "3001");

// Session registry: mcp-session-id → transport
const sessions = new Map<string, StreamableHTTPServerTransport>();

async function createMcpSession(): Promise<StreamableHTTPServerTransport> {
  const mcpServer = new McpServer({ name: "pulse-mcp", version: "0.1.0" });

  // Core tools always available
  registerProjectTools(mcpServer);
  // Re-register any categories unlocked in previous turns of this eval test
  registerActiveCategories(mcpServer);
  // Register/reset meta-tools (register_tools, reset_tools)
  registerRegisterTool(mcpServer);

  const transport = new StreamableHTTPServerTransport({
    sessionIdGenerator: () => randomUUID(),
    onsessioninitialized: (sessionId) => {
      sessions.set(sessionId, transport);
    },
  });

  transport.onclose = () => {
    for (const [id, t] of sessions) {
      if (t === transport) {
        sessions.delete(id);
        break;
      }
    }
  };

  await mcpServer.connect(transport);
  return transport;
}

const httpServer = createServer(async (req, res) => {
  try {
    // /reset — clears registeredCategories between eval test cases
    if (req.url === "/reset" && req.method === "POST") {
      resetRegisteredCategories();
      // Also clean up any lingering sessions from the previous test
      sessions.clear();
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ reset: true }));
      return;
    }

    // /mcp — MCP Streamable HTTP protocol
    if (req.url === "/mcp") {
      const sessionId = req.headers["mcp-session-id"] as string | undefined;

      let transport: StreamableHTTPServerTransport;
      if (sessionId && sessions.has(sessionId)) {
        transport = sessions.get(sessionId)!;
      } else {
        // New ADK connection — create a fresh McpServer seeded with current state
        transport = await createMcpSession();
      }

      let body: unknown;
      if (req.method === "POST") {
        const chunks: Buffer[] = [];
        for await (const chunk of req) chunks.push(chunk as Buffer);
        const raw = Buffer.concat(chunks).toString("utf-8");
        if (raw) {
          try {
            body = JSON.parse(raw);
          } catch {
            res.writeHead(400);
            res.end("Invalid JSON");
            return;
          }
        }
      }

      await transport.handleRequest(req, res, body);
      return;
    }

    res.writeHead(404);
    res.end("Not found");
  } catch (e) {
    process.stderr.write(`HTTP handler error: ${e}\n`);
    if (!res.headersSent) {
      res.writeHead(500);
      res.end("Internal server error");
    }
  }
});

httpServer.listen(port, () => {
  process.stderr.write(`Pulse MCP HTTP server running on :${port}\n`);
  process.stderr.write(`  MCP endpoint : http://localhost:${port}/mcp\n`);
  process.stderr.write(`  Reset endpoint: http://localhost:${port}/reset\n`);
});
