import { z } from "zod";
import { getClient } from "../client.js";
export function registerHeatmapTools(server) {
    server.tool("get_heatmap_data", "Get touch heatmap data for a specific screen", {
        projectId: z.string().describe("Project ID"),
        screenName: z.string().describe("Screen name"),
        from: z.string().describe("Start time (ISO 8601)"),
        to: z.string().describe("End time (ISO 8601)"),
        platform: z.string().optional().describe("Platform filter e.g. android, ios"),
        appVersion: z.string().optional(),
        breakpoint: z.string().optional(),
        geographicalRegion: z.string().optional(),
    }, async ({ projectId, screenName, from, to, platform, appVersion, breakpoint, geographicalRegion }) => {
        const data = await getClient().get("/v1/heatmap/data", projectId, {
            screenName, from, to, platform, app_version: appVersion,
            breakpoint, geographical_region: geographicalRegion,
        });
        return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    });
}
