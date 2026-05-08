import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { getClient } from "../client.js";

export function registerEventTools(server: McpServer): void {
  server.tool(
    "list_event_definitions",
    "List event definitions in the event catalog with optional search and category filtering",
    {
      projectId: z.string().describe("Project ID"),
      search: z.string().optional().describe("Search term"),
      category: z.string().optional().describe("Filter by category"),
      limit: z.number().int().default(50),
      offset: z.number().int().default(0),
    },
    async ({ projectId, search, category, limit, offset }) => {
      const data = await getClient().get<unknown>("/v1/event-definitions", projectId, {
        search, category, limit, offset,
      });
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );

  server.tool(
    "get_event_definition",
    "Get a single event definition by ID",
    {
      projectId: z.string().describe("Project ID"),
      id: z.number().int().describe("Event definition ID"),
    },
    async ({ projectId, id }) => {
      const data = await getClient().get<unknown>(`/v1/event-definitions/${id}`, projectId);
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );

  server.tool(
    "list_event_categories",
    "List all distinct event categories in the project",
    { projectId: z.string().describe("Project ID") },
    async ({ projectId }) => {
      const data = await getClient().get<unknown>("/v1/event-definitions/categories", projectId);
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );

  server.tool(
    "search_events",
    "Search events by name (autocomplete-style)",
    {
      projectId: z.string().describe("Project ID"),
      searchString: z.string().default("").describe("Search string"),
      limit: z.number().int().default(10),
    },
    async ({ projectId, searchString, limit }) => {
      const data = await getClient().get<unknown>("/v1/events", projectId, {
        search_string: searchString,
        limit,
      });
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );
}
