import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { registerProjectTools } from "./tools/projects.js";
import { registerRegisterTool } from "./tools/register.js";
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

const server = new McpServer({
  name: "pulse-mcp",
  version: "0.1.0",
});

registerProjectTools(server);
registerRegisterTool(server);

const transport = new StdioServerTransport();
await server.connect(transport);
process.stderr.write("Pulse MCP server running on stdio\n");
