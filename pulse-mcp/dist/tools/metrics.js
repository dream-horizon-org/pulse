import { z } from "zod";
import { getClient } from "../client.js";
const MetricsParams = {
    projectId: z.string().describe("Project ID"),
    interactionId: z.string().describe("Interaction ID (useCaseId)"),
    startTime: z.string().describe("Start time (ISO 8601)"),
    endTime: z.string().describe("End time (ISO 8601)"),
    appVersion: z.string().optional(),
    platform: z.string().optional().describe("Platform e.g. android, ios"),
    osVersion: z.string().optional(),
    networkProvider: z.string().optional(),
    deviceModel: z.string().optional(),
    state: z.string().optional(),
};
function buildMetricsBody(params) {
    const body = {
        useCaseId: params.interactionId,
        startTime: params.startTime,
        endTime: params.endTime,
    };
    if (params.appVersion)
        body.appVersion = params.appVersion;
    if (params.platform)
        body.platform = params.platform;
    if (params.osVersion)
        body.osVersion = params.osVersion;
    if (params.networkProvider)
        body.networkProvider = params.networkProvider;
    if (params.deviceModel)
        body.deviceModel = params.deviceModel;
    if (params.state)
        body.state = params.state;
    return body;
}
export function registerMetricsTools(server) {
    server.tool("get_apdex_score", "Get APDEX score time-series data for an interaction", MetricsParams, async ({ projectId, ...rest }) => {
        const data = await getClient().post("/v3/metric/getApdexScore", buildMetricsBody(rest), projectId);
        return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    });
    server.tool("get_error_rate", "Get error rate time-series data for an interaction", MetricsParams, async ({ projectId, ...rest }) => {
        const data = await getClient().post("/v3/metric/getErrorRate", buildMetricsBody(rest), projectId);
        return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    });
    server.tool("get_interaction_time", "Get interaction time percentile data (P50/P75/P90/P95/P99)", MetricsParams, async ({ projectId, ...rest }) => {
        const data = await getClient().post("/v3/metric/composite/getInteractionTime", buildMetricsBody(rest), projectId);
        return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    });
    server.tool("get_interaction_categorization", "Get user categorization breakdown (good/acceptable/poor) for an interaction", MetricsParams, async ({ projectId, ...rest }) => {
        const data = await getClient().post("/v3/metric/composite/getInteractionCategory", buildMetricsBody(rest), projectId);
        return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    });
}
