import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import axios from "axios";
import { z } from "zod";
import { getClient } from "../client.js";

export function registerHeatmapTools(server: McpServer): void {
  server.tool(
    "get_heatmap_data",
    "Touch heatmap for a screen. HTTP 403 usually means heatmaps are disabled in the active SDK config (heatmap feature + sessionSampleRate > 0); check get_active_sdk_config — not an auth failure.",
    {
      projectId: z.string().describe("Project ID"),
      screenName: z.string().describe("Screen name"),
      from: z.string().describe("Start time (ISO 8601)"),
      to: z.string().describe("End time (ISO 8601)"),
      platform: z
        .string()
        .optional()
        .describe("Platform filter e.g. android, ios"),
      appVersion: z.string().optional(),
      breakpoint: z.string().optional(),
      geographicalRegion: z.string().optional(),
    },
    async ({
      projectId,
      screenName,
      from,
      to,
      platform,
      appVersion,
      breakpoint,
      geographicalRegion,
    }) => {
      try {
        const data = await getClient().get<unknown>(
          "/v1/heatmap/data",
          projectId,
          {
            screenName,
            from,
            to,
            platform,
            app_version: appVersion,
            breakpoint,
            geographical_region: geographicalRegion,
          },
        );
        return {
          content: [{ type: "text", text: JSON.stringify(data, null, 2) }],
        };
      } catch (e: unknown) {
        if (axios.isAxiosError(e)) {
          const payload = e.response?.data;
          const text = JSON.stringify(
            {
              httpStatus: e.response?.status,
              pulseError: payload ?? e.message,
            },
            null,
            2,
          );
          return { content: [{ type: "text", text }] };
        }
        throw e;
      }
    },
  );
}
