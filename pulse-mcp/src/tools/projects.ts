import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { getClient } from "../client.js";

export function registerProjectTools(server: McpServer): void {
  server.tool(
    "list_projects",
    "List all Pulse projects accessible to the authenticated user",
    {},
    async () => {
      const data = await getClient().get<unknown>("/v1/users/me/projects");
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );

  server.tool(
    "get_project",
    "Get details of a specific Pulse project",
    { projectId: z.string().describe("Project ID") },
    async ({ projectId }) => {
      const data = await getClient().get<unknown>(`/v1/projects/${projectId}`, projectId);
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );

  server.tool(
    "list_project_members",
    "List members of a Pulse project with their roles",
    { projectId: z.string().describe("Project ID") },
    async ({ projectId }) => {
      const data = await getClient().get<unknown>(`/v1/projects/${projectId}/members`, projectId);
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );
}
