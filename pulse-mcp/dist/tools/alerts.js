import { z } from "zod";
import { getClient } from "../client.js";
export function registerAlertTools(server) {
    server.tool("list_alerts", "List configured alerts for a project", {
        projectId: z.string().describe("Project ID"),
        search: z.string().optional().describe("Search by alert name"),
        severity: z.string().optional().describe("Filter by severity"),
        scope: z.string().optional().describe("Filter by scope"),
    }, async ({ projectId, search, severity, scope }) => {
        const data = await getClient().get("/v1/alert", projectId, {
            search, severity, scope,
        });
        return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    });
    server.tool("get_alert_evaluation_history", "Get the evaluation history for a specific alert", {
        projectId: z.string().describe("Project ID"),
        alertId: z.string().describe("Alert ID"),
    }, async ({ projectId, alertId }) => {
        const data = await getClient().get(`/v1/alert/${alertId}/evaluationHistory`, projectId);
        return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    });
    server.tool("get_alert_filters", "Get available filter options for alerts (scopes, metrics, severities)", { projectId: z.string().describe("Project ID") }, async ({ projectId }) => {
        const data = await getClient().get("/v1/alert/filters", projectId);
        return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    });
    server.tool("get_alert_scopes", "List available alert scopes (what entity types can be alerted on)", { projectId: z.string().describe("Project ID") }, async ({ projectId }) => {
        const data = await getClient().get("/v1/alert/scopes", projectId);
        return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    });
    server.tool("get_alert_metrics", "List available metrics that can be used in alert conditions", { projectId: z.string().describe("Project ID") }, async ({ projectId }) => {
        const data = await getClient().get("/v1/alert/metrics", projectId);
        return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    });
    server.tool("get_alert_severities", "List available alert severity levels", { projectId: z.string().describe("Project ID") }, async ({ projectId }) => {
        const data = await getClient().get("/v1/alert/severity", projectId);
        return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    });
    server.tool("list_alert_notification_channels", "List notification channels configured for alerts in a project", { projectId: z.string().describe("Project ID") }, async ({ projectId }) => {
        const data = await getClient().get("/v1/alert/notificationChannels", projectId);
        return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    });
}
