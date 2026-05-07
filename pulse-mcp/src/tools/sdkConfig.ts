import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { getClient } from "../client.js";

export function registerSdkConfigTools(server: McpServer): void {
  server.tool(
    "get_active_sdk_config",
    "Get the currently active SDK configuration for a project",
    { projectId: z.string().describe("Project ID") },
    async ({ projectId }) => {
      const data = await getClient().get<unknown>("/v1/configs/active", projectId, undefined, true);
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );

  server.tool(
    "list_sdk_configs",
    "List all SDK configuration versions for a project",
    { projectId: z.string().describe("Project ID") },
    async ({ projectId }) => {
      const data = await getClient().get<unknown>("/v1/configs", projectId);
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );

  server.tool(
    "get_sdk_config",
    "Get a specific SDK configuration version",
    {
      projectId: z.string().describe("Project ID"),
      version: z.number().int().describe("Config version number"),
    },
    async ({ projectId, version }) => {
      const data = await getClient().get<unknown>(`/v1/configs/${version}`, projectId);
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );

  server.tool(
    "get_sdk_rules_features",
    "Get available SDK rules and feature flags",
    { projectId: z.string().describe("Project ID") },
    async ({ projectId }) => {
      const data = await getClient().get<unknown>("/v1/configs/rules-features", projectId);
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );
}
