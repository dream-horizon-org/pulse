/**
 * Shared in-memory incidents mock for Support Queries (GET/POST /v1/incidents).
 * Used by MockResponseGenerator and as a sync fallback in makeRequestToServer when
 * the async mock chunk fails to load (e.g. ad blockers, network).
 */
import type { MockResponse } from "./types";

export interface MockIncidentRecord {
  id: number;
  title: string;
  description: string;
  severity: string;
  status: string;
  reporterName: string;
  reporterEmail: string;
  createdAt: string;
  updatedAt: string | null;
}

let mockIncidentNextId = 3;
const mockIncidentsStore: MockIncidentRecord[] = [
  {
    id: 1,
    title: "Slow checkout on Android",
    description:
      "Users report 5+ second delay before payment confirmation screen.",
    severity: "HIGH",
    status: "ACKNOWLEDGED",
    reporterName: "Demo User",
    reporterEmail: "demo@example.com",
    createdAt: new Date(Date.now() - 2 * 86400000).toISOString(),
    updatedAt: null,
  },
  {
    id: 2,
    title: "Crash after login on iOS 17",
    description: "App terminates when opening profile tab after fresh login.",
    severity: "CRITICAL",
    status: "OPEN",
    reporterName: "QA Bot",
    reporterEmail: "qa@example.com",
    createdAt: new Date(Date.now() - 86400000).toISOString(),
    updatedAt: null,
  },
];

function incidentsPathname(urlString: string): string {
  try {
    return new URL(urlString).pathname;
  } catch {
    return new URL(urlString, "http://localhost").pathname;
  }
}

/**
 * Returns a mock response for /v1/incidents (Support Queries), or null if URL is not incidents.
 */
export function resolveIncidentsMock(
  method: string,
  urlString: string,
  body?: string | null,
): MockResponse | null {
  const pathname = incidentsPathname(urlString);
  if (
    !pathname.includes("/v1/incidents") ||
    pathname.includes("/v1/incidents/slack")
  ) {
    return null;
  }

  const m = (method || "GET").toUpperCase();

  if (m === "GET") {
    return {
      data: mockIncidentsStore.map((r) => ({ ...r })),
      status: 200,
    };
  }

  if (m === "POST") {
    try {
      const parsed = body
        ? (JSON.parse(body as string) as Record<string, unknown>)
        : {};
      const id = mockIncidentNextId++;
      const createdAt = new Date().toISOString();
      mockIncidentsStore.unshift({
        id,
        title: String(parsed.title ?? "Untitled"),
        description: String(parsed.description ?? ""),
        severity: "MEDIUM",
        status: "OPEN",
        reporterName: String(parsed.reporterName ?? "Unknown"),
        reporterEmail: String(parsed.reporterEmail ?? ""),
        createdAt,
        updatedAt: null,
      });
      return {
        data: {
          id,
          status: "OPEN",
          createdAt,
        },
        status: 200,
      };
    } catch {
      return {
        data: null,
        status: 400,
        error: {
          code: "BAD_REQUEST",
          message: "Invalid incident payload",
          cause: "JSON parse failed",
        },
      };
    }
  }

  return {
    data: null,
    status: 405,
    error: {
      code: "METHOD_NOT_ALLOWED",
      message: `Method ${method} not supported for /v1/incidents`,
      cause: "Use GET or POST",
    },
  };
}
