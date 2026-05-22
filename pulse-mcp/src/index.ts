#!/usr/bin/env node
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { registerProjectTools } from "./tools/projects.js";
import { registerInteractionTools } from "./tools/interactions.js";
import { registerEventTools } from "./tools/events.js";
import { registerMetricsTools } from "./tools/metrics.js";
import { registerSessionTools } from "./tools/sessions.js";
import { registerFunnelTools } from "./tools/funnels.js";
import { registerJourneyTools } from "./tools/journeys.js";
import { registerAlertTools } from "./tools/alerts.js";
import { registerHeatmapTools } from "./tools/heatmap.js";
import { registerSdkConfigTools } from "./tools/sdkConfig.js";
import { registerAppVitalsTools } from "./tools/appVitals.js";
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
  version: "0.0.1-beta.1",
});

registerProjectTools(server);
registerInteractionTools(server);
registerEventTools(server);
registerMetricsTools(server);
registerSessionTools(server);
registerFunnelTools(server);
registerJourneyTools(server);
registerAlertTools(server);
registerHeatmapTools(server);
registerSdkConfigTools(server);
registerAppVitalsTools(server);

const transport = new StdioServerTransport();
await server.connect(transport);
process.stderr.write("Pulse MCP server running on stdio\n");
