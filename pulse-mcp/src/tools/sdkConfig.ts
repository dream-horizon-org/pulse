import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { getClient } from "../client.js";

export function registerSdkConfigTools(server: McpServer): void {
  server.tool(
    "get_active_sdk_config",
    "Get the currently active (deployed) SDK configuration — shows which capture features (heatmap, sessions, interactions, etc.) are enabled or disabled. Use this to diagnose why a feature such as heatmap is not collecting data.",
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
    "Get the catalog of available SDK rule types and configurable feature definitions (not the active state). Use this when the user asks what rules or feature options are available to configure, not to check whether a feature is currently enabled.",
    { projectId: z.string().describe("Project ID") },
    async ({ projectId }) => {
      const data = await getClient().get<unknown>("/v1/configs/rules-features", projectId);
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );
}
