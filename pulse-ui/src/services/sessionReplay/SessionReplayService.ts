import {
  GetSessionsRequest,
  GetSessionsResponse,
  GetSessionDetailRequest,
  SessionDetailApiResponse,
  BulkTagRequest,
  BulkDeleteRequest,
  ExportSessionsRequest,
  ExportSessionsResponse,
  GetFilterSchemaRequest,
  GetFilterSchemaResponse,
  GetDateRangeConfigResponse,
  GetQuickFiltersResponse,
  SessionListingRequest,
  SessionListingResponse,
  FilterConfigResponse,
} from "./types";
import type {
  SnapshotsSourceResponse,
  SnapshotsDataResponse,
} from "./sessionReplaySnapshotTypes";
import { makeRequestToServer } from "../../helpers/makeRequestToServer";
import { getCookies } from "../../helpers/cookies";
import { COOKIES_KEY } from "../../constants";
import {
  getMockSessionListingResponse,
  getMockSessionDetailApiResponse,
  getMockSnapshotsData,
} from "../../screens/SessionReplayDetail/mock/sessionReplayMock";

export class SessionReplayService {
  private baseURL: string;

  constructor(baseURL: string) {
    this.baseURL = baseURL;
  }

  /**
   * Get sessions with filtering, pagination, and sorting
   */
  async getSessions(request: GetSessionsRequest): Promise<GetSessionsResponse> {
    const url = new URL(`${this.baseURL}/api/v1/session-replay/sessions`);

    if (request.dateRange) {
      url.searchParams.append("dateRange", JSON.stringify(request.dateRange));
    }
    if (request.environment) {
      url.searchParams.append("environment", request.environment);
    }
    if (request.project) {
      url.searchParams.append("project", request.project);
    }
    if (request.searchQuery) {
      url.searchParams.append("searchQuery", request.searchQuery);
    }
    if (request.filters) {
      url.searchParams.append("filters", JSON.stringify(request.filters));
    }
    if (request.advancedFilters) {
      url.searchParams.append(
        "advancedFilters",
        JSON.stringify(request.advancedFilters),
      );
    }
    if (request.page) {
      url.searchParams.append("page", request.page.toString());
    }
    if (request.pageSize) {
      url.searchParams.append("pageSize", request.pageSize.toString());
    }
    if (request.sortBy) {
      url.searchParams.append("sortBy", request.sortBy);
    }
    if (request.sortOrder) {
      url.searchParams.append("sortOrder", request.sortOrder);
    }

    try {
      const response = await makeRequestToServer({
        url: url.toString(),
        init: {
          method: "GET",
        },
      });

      if (!response.ok) {
        throw new Error(`Failed to fetch sessions: ${response.statusText}`);
      }

      const json = await response.json();
      // MockServer wraps responses in {data: ..., error: ...}, unwrap for mock mode
      return json.data || json;
    } catch (error) {
      console.error("Error fetching sessions:", error);
      throw error;
    }
  }

  async getSessionDetail(
    request: GetSessionDetailRequest,
  ): Promise<SessionDetailApiResponse> {
    if (process.env.REACT_APP_USE_MOCK_SESSION_REPLAY === "true") {
      return getMockSessionDetailApiResponse(request.sessionId);
    }
    const path = `/v1/sessions/${encodeURIComponent(request.sessionId)}`;
    const includeParam = request.include?.length
      ? request.include.join(",")
      : undefined;
    const url = includeParam
      ? `${this.baseURL}${path}?include=${encodeURIComponent(includeParam)}`
      : `${this.baseURL}${path}`;

    try {
      const response = await makeRequestToServer({
        url,
        init: {
          method: "GET",
          headers: this.sessionListingHeaders(),
        },
      });

      if (!response.ok) {
        throw new Error(
          `Failed to fetch session detail: ${response.statusText}`,
        );
      }

      const json = await response.json();
      // MockServer wraps responses in {data: ..., error: ...}, unwrap for mock mode
      return json.data || json;
    } catch (error) {
      console.error("Error fetching session detail:", error);
      throw error;
    }
  }

  /**
   * Bulk tag sessions
   */
  async bulkTagSessions(
    request: BulkTagRequest,
  ): Promise<{ success: boolean }> {
    const url = `${this.baseURL}/api/v1/session-replay/sessions/bulk-tag`;

    try {
      const response = await makeRequestToServer({
        url,
        init: {
          method: "POST",
          body: JSON.stringify(request),
        },
      });

      if (!response.ok) {
        throw new Error(`Failed to tag sessions: ${response.statusText}`);
      }

      const json = await response.json();
      // MockServer wraps responses in {data: ..., error: ...}, unwrap for mock mode
      return json.data || json;
    } catch (error) {
      console.error("Error tagging sessions:", error);
      throw error;
    }
  }

  /**
   * Bulk delete sessions
   */
  async bulkDeleteSessions(
    request: BulkDeleteRequest,
  ): Promise<{ success: boolean }> {
    const url = `${this.baseURL}/api/v1/session-replay/sessions/bulk-delete`;

    try {
      const response = await makeRequestToServer({
        url,
        init: {
          method: "DELETE",
          body: JSON.stringify(request),
        },
      });

      if (!response.ok) {
        throw new Error(`Failed to delete sessions: ${response.statusText}`);
      }

      const json = await response.json();
      // MockServer wraps responses in {data: ..., error: ...}, unwrap for mock mode
      return json.data || json;
    } catch (error) {
      console.error("Error deleting sessions:", error);
      throw error;
    }
  }

  /**
   * Export sessions
   */
  async exportSessions(
    request: ExportSessionsRequest,
  ): Promise<ExportSessionsResponse> {
    const url = `${this.baseURL}/api/v1/session-replay/sessions/export`;

    try {
      const response = await makeRequestToServer({
        url,
        init: {
          method: "POST",
          body: JSON.stringify(request),
        },
      });

      if (!response.ok) {
        throw new Error(`Failed to export sessions: ${response.statusText}`);
      }

      const json = await response.json();
      // MockServer wraps responses in {data: ..., error: ...}, unwrap for mock mode
      return json.data || json;
    } catch (error) {
      console.error("Error exporting sessions:", error);
      throw error;
    }
  }

  async getSnapshotsSource(
    sessionId: string,
  ): Promise<SnapshotsSourceResponse["data"]> {
    if (process.env.REACT_APP_USE_MOCK_SESSION_REPLAY === "true") {
      return {
        sessionId: sessionId,
        snapshotSource: "android",
        sources: [
          {
            source: "blob",
            blobKey: "0",
            startTimestamp: "2026-03-13 12:17:28.354000",
            endTimestamp: "2026-03-13 12:17:36.197000",
          },
          {
            source: "blob",
            blobKey: "1",
            startTimestamp: "2026-03-13 12:17:37.197000",
            endTimestamp: "2026-03-13 12:17:46.219000",
          },
        ],
      };
    }

    const url = `${this.baseURL}/v1/sessions/${encodeURIComponent(sessionId)}/snapshots-source`;

    const response = await makeRequestToServer({
      url,
      init: {
        method: "GET",
        headers: this.sessionListingHeaders(),
      },
    });

    if (!response.ok) {
      throw new Error(
        `Failed to fetch snapshots source: ${response.statusText}`,
      );
    }

    const json: SnapshotsSourceResponse = await response.json();
    if (json.error) {
      throw new Error(json.error);
    }
    return json.data;
  }

  async getSnapshotsData(
    sessionId: string,
    startBlobKey: string,
    endBlobKey: string,
  ): Promise<SnapshotsDataResponse["data"]> {
    if (process.env.REACT_APP_USE_MOCK_SESSION_REPLAY === "true") {
      return getMockSnapshotsData(startBlobKey);
    }

    const url = new URL(
      `${this.baseURL}/v1/sessions/${encodeURIComponent(sessionId)}/snapshots-data`,
    );
    url.searchParams.set("start_blob_key", startBlobKey);
    url.searchParams.set("end_blob_key", endBlobKey);

    const response = await makeRequestToServer({
      url: url.toString(),
      init: {
        method: "GET",
        headers: this.sessionListingHeaders(),
      },
    });

    if (!response.ok) {
      throw new Error(`Failed to fetch snapshots data: ${response.statusText}`);
    }

    const json: SnapshotsDataResponse = await response.json();
    if (json.error) {
      throw new Error(json.error);
    }
    return json.data;
  }

  /**
   * Get filter schema configuration
   * Returns platform-specific filters based on project
   */
  async getFilterSchema(
    request: GetFilterSchemaRequest = {},
  ): Promise<GetFilterSchemaResponse> {
    const url = new URL(`${this.baseURL}/api/v1/session-replay/filters/schema`);

    if (request.projectId) {
      url.searchParams.append("projectId", request.projectId);
    }

    try {
      const response = await makeRequestToServer({
        url: url.toString(),
        init: {
          method: "GET",
        },
      });

      if (!response.ok) {
        throw new Error(
          `Failed to fetch filter schema: ${response.statusText}`,
        );
      }

      const json = await response.json();
      // MockServer wraps responses in {data: ..., error: ...}, unwrap for mock mode
      return json.data || json;
    } catch (error) {
      console.error("Error fetching filter schema:", error);
      throw error;
    }
  }

  /**
   * Get date range configuration
   * Returns available date range options and defaults
   */
  async getDateRangeConfig(): Promise<GetDateRangeConfigResponse> {
    const url = `${this.baseURL}/api/v1/session-replay/config/date-ranges`;

    try {
      const response = await makeRequestToServer({
        url,
        init: {
          method: "GET",
        },
      });

      if (!response.ok) {
        throw new Error(
          `Failed to fetch date range config: ${response.statusText}`,
        );
      }

      const json = await response.json();
      // MockServer wraps responses in {data: ..., error: ...}, unwrap for mock mode
      return json.data || json;
    } catch (error) {
      console.error("Error fetching date range config:", error);
      throw error;
    }
  }

  /**
   * Get quick filters configuration
   * Returns predefined quick filter options
   */
  async getQuickFilters(): Promise<GetQuickFiltersResponse> {
    const url = `${this.baseURL}/api/v1/session-replay/config/quick-filters`;

    try {
      const response = await makeRequestToServer({
        url,
        init: {
          method: "GET",
        },
      });

      if (!response.ok) {
        throw new Error(
          `Failed to fetch quick filters: ${response.statusText}`,
        );
      }

      const json = await response.json();
      // MockServer wraps responses in {data: ..., error: ...}, unwrap for mock mode
      return json.data || json;
    } catch (error) {
      console.error("Error fetching quick filters:", error);
      throw error;
    }
  }

  private sessionListingHeaders(): Record<string, string> {
    const tenantId = getCookies(COOKIES_KEY.TENANT_ID);
    const headers: Record<string, string> = {};
    if (tenantId) {
      headers["X-Tenant-ID"] = tenantId;
    }
    return headers;
  }

  /**
   * Sessions Listing API – cursor-paginated session list with filters, sort, search.
   * POST /v1/sessions/listing
   */
  async postSessionsListing(
    request: SessionListingRequest,
  ): Promise<SessionListingResponse> {
    if (process.env.REACT_APP_USE_MOCK_SESSION_REPLAY === "true") {
      return getMockSessionListingResponse(request);
    }
    const url = `${this.baseURL}/v1/sessions/listing`;

    try {
      const response = await makeRequestToServer({
        url,
        init: {
          method: "POST",
          headers: this.sessionListingHeaders(),
          body: JSON.stringify(request),
        },
      });

      if (!response.ok) {
        throw new Error(
          `Failed to fetch sessions listing: ${response.statusText}`,
        );
      }

      const json = await response.json();
      // MockServer wraps responses in {data: ..., error: ...}, unwrap for mock mode
      return json.data || json;
    } catch (error) {
      console.error("Error fetching sessions listing:", error);
      throw error;
    }
  }

  /**
   * Sessions Filters API – filter config for quick filters and advanced builder.
   * GET /v1/sessions/filters
   */
  async getSessionsFilters(): Promise<FilterConfigResponse> {
    const url = `${this.baseURL}/v1/sessions/filters`;

    try {
      const response = await makeRequestToServer({
        url,
        init: {
          method: "GET",
          headers: this.sessionListingHeaders(),
        },
      });

      if (!response.ok) {
        throw new Error(
          `Failed to fetch sessions filters: ${response.statusText}`,
        );
      }

      const json = await response.json();
      // MockServer wraps responses in {data: ..., error: ...}, unwrap for mock mode
      return json.data || json;
    } catch (error) {
      console.error("Error fetching sessions filters:", error);
      throw error;
    }
  }
}

// Create singleton instance
const API_BASE_URL =
  process.env.REACT_APP_PULSE_SERVER_URL || "http://localhost:8080";

export const sessionReplayService = new SessionReplayService(API_BASE_URL);
