import { z } from "zod";
import { getClient } from "../client.js";
export function registerFunnelTools(server) {
    server.tool("list_funnels", "List funnels for a project with optional search, status, and tag filters", {
        projectId: z.string().describe("Project ID"),
        search: z.string().optional(),
        status: z.string().optional().describe("Filter by status e.g. ACTIVE, COMPLETED"),
        tags: z.array(z.string()).optional(),
        page: z.number().int().default(1),
        pageSize: z.number().int().default(10),
    }, async ({ projectId, search, status, tags, page, pageSize }) => {
        const data = await getClient().get("/v1/funnels", projectId, {
            search, status, tags: tags?.join(","), page, pageSize,
        });
        return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    });
    server.tool("get_funnel", "Get details of a specific funnel", {
        projectId: z.string().describe("Project ID"),
        funnelId: z.number().int().describe("Funnel ID"),
    }, async ({ projectId, funnelId }) => {
        const data = await getClient().get(`/v1/funnels/${funnelId}`, projectId);
        return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    });
    server.tool("get_funnel_tags", "List all tags used across funnels and journeys in a project", { projectId: z.string().describe("Project ID") }, async ({ projectId }) => {
        const data = await getClient().get("/v1/funnels/tags", projectId);
        return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    });
    server.tool("get_funnel_events", "List events available for use in funnel steps", { projectId: z.string().describe("Project ID") }, async ({ projectId }) => {
        const data = await getClient().get("/v1/funnels/events", projectId);
        return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    });
    server.tool("get_funnel_filters", "List available filter fields for funnel analysis", { projectId: z.string().describe("Project ID") }, async ({ projectId }) => {
        const data = await getClient().get("/v1/funnels/filters", projectId);
        return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    });
}
