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
import { registerQueryTools } from "./tools/query.js";
import { registerSdkConfigTools } from "./tools/sdkConfig.js";
import { registerAnomalyTools } from "./tools/anomaly.js";

const server = new McpServer({
  name: "pulse-mcp",
  version: "0.1.0",
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
registerQueryTools(server);
registerSdkConfigTools(server);
registerAnomalyTools(server);

const transport = new StdioServerTransport();
await server.connect(transport);
process.stderr.write("Pulse MCP server running on stdio\n");
