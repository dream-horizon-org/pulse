import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import axios from "axios";
import { z } from "zod";
import { getClient } from "../client.js";

const SESSION_LISTING_TIMEOUT_MS = 120_000;

function sessionListingExtraHeaders(): Record<string, string> | undefined {
  try {
    const email = getClient().requireUserEmailFromToken();
    return { "user-email": email };
  } catch {
    return undefined;
  }
}

function rewriteSessionListingError(e: unknown): never {
  if (!axios.isAxiosError(e)) {
    throw e;
  }
  const status = e.response?.status;
  const code = e.code;
  if (
    code === "ECONNABORTED" ||
    code === "ETIMEDOUT" ||
    e.message.toLowerCase().includes("timeout")
  ) {
    throw new Error(
      "Session listing: client-side timeout before a response was received (not an HTTP 500 from Pulse). Increase timeout or narrow timeRange.",
    );
  }
  if (status === 504 || status === 408) {
    throw new Error(
      `Session listing: HTTP ${status} (gateway/proxy timeout), not a Pulse application 500.`,
    );
  }
  throw e;
}

function defaultRangeIso(): { from: string; to: string } {
  const to = new Date();
  const from = new Date(to.getTime() - 7 * 24 * 60 * 60 * 1000);
  return { from: from.toISOString(), to: to.toISOString() };
}

export function registerSessionTools(server: McpServer): void {
  server.tool(
    "list_session_replays",
    "List sessions via POST /v1/sessions/listing (cursor pagination). Sends sort START_TIME/DESC by default, user-email when JWT includes email (matches UI). Narrow timeRange if slow.",
    {
      projectId: z.string().describe("Project ID"),
      interactionName: z
        .string()
        .optional()
        .describe("Optional — forwarded as listing search query"),
      startTime: z
        .string()
        .optional()
        .describe(
          "Range start as an absolute ISO 8601 datetime string e.g. '2026-05-20T15:00:00Z'. Must be an absolute timestamp — do NOT use duration format (PT1H, PT-1H etc.). Compute from current wall-clock time. Required together with endTime; omit both for default last-7-days window.",
        ),
      endTime: z
        .string()
        .optional()
        .describe(
          "Range end as an absolute ISO 8601 datetime string e.g. '2026-05-20T16:00:00Z'. Must be an absolute timestamp — do NOT use duration format. Required together with startTime; omit both for default last-7-days window.",
        ),
      pageSize: z.number().int().min(1).max(100).default(20),
      sortBy: z
        .string()
        .optional()
        .describe(
          "Optional — SessionListingSortField name (default START_TIME). E.g. DURATION, QUALITY_SCORE.",
        ),
      sortDirection: z
        .enum(["ASC", "DESC"])
        .optional()
        .describe("Optional — default DESC"),
      cursor: z
        .string()
        .optional()
        .describe("Opaque cursor from previous response page.nextCursor"),
      device: z
        .string()
        .optional()
        .describe("Reserved — not mapped to listing filters in this tool"),
      eventTypes: z
        .array(z.string())
        .optional()
        .describe("Reserved — not mapped to listing filters in this tool"),
    },
    async ({
      projectId,
      interactionName,
      startTime,
      endTime,
      pageSize,
      sortBy,
      sortDirection,
      cursor,
    }) => {
      if ((startTime && !endTime) || (!startTime && endTime)) {
        throw new Error(
          "Provide both startTime and endTime together, or omit both for default last-7-days window.",
        );
      }
      const range =
        startTime && endTime
          ? { from: startTime, to: endTime }
          : defaultRangeIso();

      const body: Record<string, unknown> = {
        timeRange: { from: range.from, to: range.to },
        page: { limit: pageSize, ...(cursor ? { cursor } : {}) },
        sortBy: sortBy ?? "START_TIME",
        sortDirection: sortDirection ?? "DESC",
      };

      if (interactionName?.trim()) {
        body.query = interactionName.trim();
      }

      try {
        const data = await getClient().post<unknown>(
          "/v1/sessions/listing",
          body,
          projectId,
          false,
          sessionListingExtraHeaders(),
          SESSION_LISTING_TIMEOUT_MS,
        );
        return {
          content: [{ type: "text", text: JSON.stringify(data, null, 2) }],
        };
      } catch (e: unknown) {
        rewriteSessionListingError(e);
      }
    },
  );
}
