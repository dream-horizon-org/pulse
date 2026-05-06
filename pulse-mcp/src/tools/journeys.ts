import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { getClient } from "../client.js";

export function registerJourneyTools(server: McpServer): void {
  server.tool(
    "list_journeys",
    "List user journeys for a project with optional search, status, and tag filters",
    {
      projectId: z.string().describe("Project ID"),
      search: z.string().optional(),
      status: z.string().optional(),
      tags: z.array(z.string()).optional(),
      page: z.number().int().default(1),
      pageSize: z.number().int().default(10),
    },
    async ({ projectId, search, status, tags, page, pageSize }) => {
      const data = await getClient().get<unknown>("/v1/journeys", projectId, {
        search, status, tags: tags?.join(","), page, pageSize,
      });
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );

  server.tool(
    "get_journey",
    "Get details of a specific user journey",
    {
      projectId: z.string().describe("Project ID"),
      journeyId: z.number().int().describe("Journey ID"),
    },
    async ({ projectId, journeyId }) => {
      const data = await getClient().get<unknown>(`/v1/journeys/${journeyId}`, projectId);
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );
}
