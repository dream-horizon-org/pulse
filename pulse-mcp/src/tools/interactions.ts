import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { getClient } from "../client.js";

export function registerInteractionTools(server: McpServer): void {
  server.tool(
    "list_interactions",
    "List user interactions (critical interactions) for a project. Supports filtering by name, status, user email, and pagination.",
    {
      projectId: z.string().describe("Project ID"),
      name: z.string().optional().describe("Filter by interaction name"),
      status: z.string().optional().describe("Filter by status (e.g. ACTIVE, STOPPED)"),
      userEmail: z.string().optional().describe("Filter by creator email"),
      page: z.number().int().default(0).describe("Page number (0-based)"),
      size: z.number().int().default(20).describe("Page size"),
    },
    async ({ projectId, name, status, userEmail, page, size }) => {
      const data = await getClient().get<unknown>("/v1/interactions", projectId, {
        name, status, userEmail, page, size,
      });
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );

  server.tool(
    "list_suggested_interactions",
    "List AI-suggested interactions for a project",
    { projectId: z.string().describe("Project ID") },
    async ({ projectId }) => {
      const data = await getClient().get<unknown>("/v1/interactions/suggestions", projectId);
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );

  server.tool(
    "get_interaction_filter_options",
    "Get available filter options for the interaction list (e.g. available statuses, user emails)",
    { projectId: z.string().describe("Project ID") },
    async ({ projectId }) => {
      const data = await getClient().get<unknown>("/v1/interactions/filter-options", projectId);
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );

  server.tool(
    "get_interaction_telemetry_filters",
    "Get available telemetry filters for the dashboard (platform, app version, OS, etc.)",
    { projectId: z.string().describe("Project ID") },
    async ({ projectId }) => {
      const data = await getClient().get<unknown>("/v1/interactions/telemetry-filters", projectId);
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );

  server.tool(
    "get_interaction_root_cause",
    "Get root cause analysis (RCA) for a specific interaction",
    {
      projectId: z.string().describe("Project ID"),
      interactionId: z.string().describe("Interaction ID"),
    },
    async ({ projectId, interactionId }) => {
      const data = await getClient().get<unknown>(
        `/v1/interactions/${interactionId}/root-cause`,
        projectId
      );
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );
}
