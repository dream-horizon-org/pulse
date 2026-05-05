import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { getClient } from "../client.js";

export function registerQueryTools(server: McpServer): void {
  server.tool(
    "get_query_tables",
    "List available tables in the Pulse query builder",
    { projectId: z.string().describe("Project ID") },
    async ({ projectId }) => {
      const data = await getClient().get<unknown>("/query/tables", projectId);
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );

  server.tool(
    "get_query_history",
    "Get query history for the current user in a project",
    {
      projectId: z.string().describe("Project ID"),
      limit: z.number().int().default(20),
      offset: z.number().int().default(0),
    },
    async ({ projectId, limit, offset }) => {
      const data = await getClient().get<unknown>("/query/history", projectId, { limit, offset });
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );

  server.tool(
    "get_query_stats",
    "Get query execution statistics for a project",
    { projectId: z.string().describe("Project ID") },
    async ({ projectId }) => {
      const data = await getClient().get<unknown>("/query/stats", projectId);
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );

  server.tool(
    "get_universal_query_tables",
    "List all tables available for universal SQL querying",
    { projectId: z.string().describe("Project ID") },
    async ({ projectId }) => {
      const data = await getClient().get<unknown>("/v2/getListOfTables", projectId);
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );

  server.tool(
    "get_universal_query_columns",
    "List columns for a specific table in universal query",
    {
      projectId: z.string().describe("Project ID"),
      tableName: z.string().describe("Table name"),
    },
    async ({ projectId, tableName }) => {
      const data = await getClient().get<unknown>("/v2/getColumnNamesOfTable", projectId, {
        tableName,
      });
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );
}
