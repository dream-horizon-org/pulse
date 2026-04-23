import { z } from "zod";
import { getClient } from "../client.js";
export function registerAnomalyTools(server) {
    server.tool("get_anomaly_details", "Get anomaly detection details for an interaction", {
        projectId: z.string().describe("Project ID"),
        interactionId: z.string().describe("Interaction ID"),
        from: z.string().optional().describe("Start time (ISO 8601)"),
        to: z.string().optional().describe("End time (ISO 8601)"),
    }, async ({ projectId, interactionId, from, to }) => {
        const data = await getClient().get("/anomaly/details", projectId, {
            interactionId, from, to,
        });
        return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    });
    server.tool("get_anomaly_apdex", "Get APDEX anomaly data for an interaction", {
        projectId: z.string().describe("Project ID"),
        interactionId: z.string().describe("Interaction ID"),
        from: z.string().optional().describe("Start time (ISO 8601)"),
        to: z.string().optional().describe("End time (ISO 8601)"),
    }, async ({ projectId, interactionId, from, to }) => {
        const data = await getClient().get("/anomaly/apdex", projectId, {
            interactionId, from, to,
        });
        return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    });
    server.tool("get_anomaly_error_rate", "Get error rate anomaly data for an interaction", {
        projectId: z.string().describe("Project ID"),
        interactionId: z.string().describe("Interaction ID"),
        from: z.string().optional().describe("Start time (ISO 8601)"),
        to: z.string().optional().describe("End time (ISO 8601)"),
    }, async ({ projectId, interactionId, from, to }) => {
        const data = await getClient().get("/anomaly/error-rate", projectId, {
            interactionId, from, to,
        });
        return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    });
}
