import { z } from "zod";
import { getClient } from "../client.js";
export function registerSessionTools(server) {
    server.tool("list_session_replays", "List session replays for a project with optional interaction and time filters", {
        projectId: z.string().describe("Project ID"),
        interactionName: z.string().optional().describe("Filter by interaction name"),
        startTime: z.string().optional().describe("Start time (ISO 8601)"),
        endTime: z.string().optional().describe("End time (ISO 8601)"),
        page: z.number().int().default(0),
        pageSize: z.number().int().default(20),
        device: z.string().optional().describe("Filter by device"),
        eventTypes: z.array(z.string()).optional().describe("Filter by event types"),
    }, async ({ projectId, interactionName, startTime, endTime, page, pageSize, device, eventTypes }) => {
        const data = await getClient().get("/v1/session-replays", projectId, {
            interactionName,
            startTime,
            endTime,
            page,
            pageSize,
            device,
            eventTypes: eventTypes?.join(","),
        });
        return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    });
}
