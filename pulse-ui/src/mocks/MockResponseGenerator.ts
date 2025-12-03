/**
 * Mock Response Generator
 *
 * Generates realistic mock responses for different API endpoints
 */

import { MockResponse, MockRequest } from "./types";
import { MockDataStore } from "./MockDataStore";
import { MockConfigManager } from "./MockConfig";
import { generateDataQueryMockResponseV2 } from "./v2";
import { mockJobResponses } from "./responses/jobResponses";

export class MockResponseGenerator {
  private dataStore: MockDataStore;
  private config: MockConfigManager;

  constructor() {
    this.dataStore = MockDataStore.getInstance();
    this.config = MockConfigManager.getInstance();
  }

  async generateResponse(request: MockRequest): Promise<MockResponse> {
    const url = new URL(request.url);
    const pathname = url.pathname;
    const method = request.method;

    // Debug logging
    if (this.config.shouldLog()) {
      console.log(`[Mock Server] Processing request: ${method} ${pathname}`);
    }

    if (this.config.shouldLog()) {
      console.log(`[Mock Server] ${method} ${pathname}`, request);
    }

    // Add artificial delay
    await this.delay(this.config.getDelay());

    // Simulate random errors
    if (this.config.shouldSimulateError()) {
      return this.generateErrorResponse();
    }

    // Route to appropriate handler based on path and method
    return this.routeRequest(pathname, method, request);
  }

  private async delay(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  private generateErrorResponse(): MockResponse {
    return {
      data: null,
      status: 500,
      error: {
        code: "MOCK_ERROR",
        message: "Simulated mock server error",
        cause: "Random error simulation",
      },
    };
  }

  private createMockJWTToken(payload: any): string {
    // Create a simple mock JWT token structure
    // In a real scenario, this would be a proper JWT, but for mocking we'll create a simple structure
    const header = {
      alg: "HS256",
      typ: "JWT",
    };

    const now = Math.floor(Date.now() / 1000);
    const tokenPayload = {
      ...payload,
      iat: now,
      exp: now + 3600, // 1 hour expiration
      iss: "mock-server",
      aud: "pulse-client",
    };

    // Encode header and payload as base64url
    const encodedHeader = this.base64UrlEncode(JSON.stringify(header));
    const encodedPayload = this.base64UrlEncode(JSON.stringify(tokenPayload));

    // Create a mock signature (in real JWT this would be HMAC signature)
    const signature = this.base64UrlEncode("mock-signature-" + Date.now());

    return `${encodedHeader}.${encodedPayload}.${signature}`;
  }

  private base64UrlEncode(str: string): string {
    return btoa(str).replace(/\+/g, "-").replace(/\//g, "_").replace(/=/g, "");
  }

  private routeRequest(
    pathname: string,
    method: string,
    request: MockRequest,
  ): MockResponse {
    // Authentication endpoints
    if (pathname.includes("/auth/")) {
      return this.handleAuthEndpoints(pathname, method, request);
    }

    // User endpoints - removed to avoid conflict with activity tracking endpoints
    // if (pathname.includes('/user')) {
    //   return this.handleUserEndpoints(pathname, method, request);
    // }

    // Data Query endpoint (MUST come before /v1/interactions to avoid being caught by job endpoints)
    if (
      pathname.includes("/performance-metric/distribution") &&
      method === "POST"
    ) {
      return this.handleDataQueryEndpoint(pathname, method, request);
    }

    // Dashboard filters endpoint (MUST come before /v1/interactions to avoid being caught by job endpoints)
    if (
      (pathname.includes("/telemetry-filters") || pathname.includes("/filter-options")) &&
      method === "GET"
    ) {
      return this.handleDashboardFiltersEndpoint(pathname, method, request);
    }

    // Job endpoints
    if (
      pathname.includes("/job") ||
      pathname.includes("/getJobs") ||
      pathname.includes("/v1/interactions") ||
      pathname.includes("/getJobDetails") ||
      pathname.includes("/getJobStatus") ||
      pathname.includes("/getJobFilters") ||
      pathname.includes("/updateJobStatus")
    ) {
      return this.handleJobEndpoints(pathname, method, request);
    }

    // Permission endpoints
    if (pathname.includes("/permission/check")) {
      return this.handlePermissionEndpoints(pathname, method, request);
    }

    // Alert endpoints
    if (pathname.includes("/alert")) {
      return this.handleAlertEndpoints(pathname, method, request);
    }

    // Session Replays endpoints
    if (pathname.includes("/session-replays")) {
      return this.handleSessionReplaysEndpoints(pathname, method, request);
    }

    // Analytics endpoints
    if (
      pathname.includes("/getApdexScore") ||
      pathname.includes("/getErrorRate") ||
      pathname.includes("/getInteractionTime") ||
      pathname.includes("/getInteractionCategory")
    ) {
      return this.handleAnalyticsEndpoints(pathname, method, request);
    }

    // Interaction Insights endpoint
    if (pathname.includes("/api/v1/interaction/insights")) {
      return this.handleInteractionInsightsEndpoint(pathname, method, request);
    }

    // User events endpoints
    if (pathname.includes("/getUserEvent")) {
      return this.handleUserEventsEndpoints(pathname, method, request);
    }

    // Universal querying endpoints
    if (
      pathname.includes("/validateQuery") ||
      pathname.includes("/getQueryResult") ||
      pathname.includes("/fetchQueryData") ||
      pathname.includes("/cancelQueryRequest") ||
      pathname.includes("/getQuery/") ||
      pathname.includes("/getListOfTables") ||
      pathname.includes("/getColumnNamesOfTable")
    ) {
      return this.handleUniversalQueryEndpoints(pathname, method, request);
    }

    // Analytics report endpoints
    if (
      pathname.includes("/analytics-report") ||
      pathname.includes("/incident/generateReport")
    ) {
      return this.handleAnalyticsReportEndpoints(pathname, method, request);
    }

    // Anomaly detection endpoints
    if (pathname.includes("/anomaly/")) {
      return this.handleAnomalyEndpoints(pathname, method, request);
    }

    // Activity tracking endpoints (includes user details, AI, and events)
    if (
      pathname.includes("/user/") ||
      pathname.includes("/pulse-ai/") ||
      pathname.includes("/v2/events/") ||
      pathname.includes("/v1/events")
    ) {
      return this.handleActivityTrackingEndpoints(pathname, method, request);
    }

    // Job-based graph data endpoints (for real-time updates)
    if (
      pathname.includes("/job/") &&
      (pathname.includes("getApdexScore") ||
        pathname.includes("getErrorRate") ||
        pathname.includes("getInteractionTime") ||
        pathname.includes("getInteractionCategory"))
    ) {
      return this.handleJobBasedGraphEndpoints(pathname, method, request);
    }

    // Query endpoints
    if (
      pathname.includes("/query") ||
      pathname.includes("/getQueryResult") ||
      pathname.includes("/validateQuery")
    ) {
      return this.handleQueryEndpoints(pathname, method, request);
    }

    // Event endpoints
    if (pathname.includes("/events") || pathname.includes("/whitelist")) {
      return this.handleEventEndpoints(pathname, method, request);
    }

    // Default response
    return {
      data: { message: "Mock response not implemented" },
      status: 200,
    };
  }

  private handleAuthEndpoints(
    pathname: string,
    method: string,
    request: MockRequest,
  ): MockResponse {
    // Handle authentication endpoint
    if (pathname.includes("/authenticate") && method === "POST") {
      // Parse request body to check for dummy login token
      let requestBody: any = {};
      try {
        if (request.body) {
          requestBody = JSON.parse(request.body);
        }
      } catch (e) {
        // Ignore parse errors
      }

      // Handle dummy login token (dev-id-token) or any other identifier
      const identifier = requestBody.identifier || "";
      const isDummyLogin = identifier === "dev-id-token" || !identifier;

      // Create a mock JWT token that can be decoded
      const mockIdToken = this.createMockJWTToken({
        email: isDummyLogin ? "dev@dream11.com" : "mock@dream11.com",
        firstName: isDummyLogin ? "Dev" : "Mock",
        lastName: isDummyLogin ? "User" : "User",
        profilePicture: "https://via.placeholder.com/150",
      });

      return {
        data: {
          accessToken: "mock_access_token_" + Date.now(),
          refreshToken: "mock_refresh_token_" + Date.now(),
          idToken: mockIdToken,
          code: "mock_code_" + Date.now(),
          tokenType: "Bearer",
          expiresIn: 3600,
        },
        status: 200,
      };
    }

    // Handle token refresh endpoint
    if (pathname.includes("/refresh") && method === "POST") {
      // Create a mock JWT token for refresh
      const mockIdToken = this.createMockJWTToken({
        email: "mock@dream11.com",
        firstName: "Mock",
        lastName: "User",
        profilePicture: "https://via.placeholder.com/150",
      });

      return {
        data: {
          accessToken: "mock_refreshed_token_" + Date.now(),
          refreshToken: "mock_refresh_token_" + Date.now(),
          idToken: mockIdToken,
          tokenType: "Bearer",
          expiresIn: 3600,
        },
        status: 200,
      };
    }

    // Handle token verify endpoint
    if (pathname.includes("/token/verify") && method === "GET") {
      // Check if authorization header is present
      const authHeader = request.headers?.["authorization"] || 
                        request.headers?.["Authorization"] || "";
      
      // Extract token from "Bearer <token>" format
      const token = authHeader.replace(/^Bearer\s+/i, "").trim();
      
      // Validate token - consider it valid if it's a mock token or starts with "mock_"
      const isValid = token.length > 0 && (
        token.startsWith("mock_") || 
        token.startsWith("Bearer mock_") ||
        token.includes("access_token")
      );

      return {
        data: {
          isAuthTokenValid: isValid,
        },
        status: 200,
      };
    }

    return this.generateErrorResponse();
  }

  private handleUserEndpoints(
    pathname: string,
    method: string,
    request: MockRequest,
  ): MockResponse {
    const phoneNo = pathname.split("/").pop();

    if (pathname.includes("/experiments")) {
      const user = this.dataStore.getUsers().find((u) => u.phoneNo === phoneNo);
      return {
        data: user?.experiments || [],
        status: 200,
      };
    }

    if (pathname.includes("/active-today")) {
      const user = this.dataStore.getUsers().find((u) => u.phoneNo === phoneNo);
      return {
        data: { active: user?.lastActiveToday || false },
        status: 200,
      };
    }

    // Default user detail response
    const user = this.dataStore.getUsers().find((u) => u.phoneNo === phoneNo);
    if (user) {
      return {
        data: {
          teamName: user.teamName,
          userId: user.userId,
          emailId: user.emailId,
          commEmailId: user.commEmailId,
        },
        status: 200,
      };
    }

    return {
      data: null,
      status: 404,
      error: {
        code: "USER_NOT_FOUND",
        message: "User not found",
        cause: "Invalid phone number",
      },
    };
  }

  private handleDashboardFiltersEndpoint(
    pathname: string,
    method: string,
    request: MockRequest,
  ): MockResponse {
    if (
      pathname.includes("/telemetry-filters") &&
      method === "GET"
    ) {
      return mockJobResponses.getDashboardFilters;
    }

    // Handle interaction filter options endpoint (/v1/interactions/filter-options)
    if (
      pathname.includes("/filter-options") &&
      method === "GET"
    ) {
      // Get unique users from actual job data
      const jobs = this.dataStore.getJobs();
      const uniqueUsers = Array.from(
        new Set(
          jobs
            .map((job) => job.createdBy)
            .filter((user): user is string => Boolean(user)),
        ),
      );

      // Return statuses as per contract: RUNNING, STOPPED, DELETED
      const statuses = ["RUNNING", "STOPPED", "DELETED"];

      return {
        data: {
          statuses: statuses,
          createdBy: uniqueUsers.length > 0 ? uniqueUsers : [
            "user1@dream11.com",
            "user2@dream11.com",
            "user3@dream11.com",
          ],
        },
        status: 200,
      };
    }

    return {
      data: { message: "Dashboard filters endpoint not found" },
      status: 404,
    };
  }

  private handleJobEndpoints(
    pathname: string,
    method: string,
    request: MockRequest,
  ): MockResponse {
    if (
      pathname.includes("/getJobs") ||
      pathname.includes("/v1/interactions")
    ) {
      // Handle specific interaction endpoint: GET /v1/interactions/{InteractionName}
      const pathParts = pathname.split("/").filter((part) => part !== ""); // Remove empty parts
      console.log(`[Mock Server] Path parts:`, pathParts);
      console.log(`[Mock Server] Full pathname:`, pathname);

      if (
        pathParts.length >= 3 &&
        pathParts[0] === "v1" &&
        pathParts[1] === "interactions" &&
        pathParts[2]
      ) {
        const interactionName = decodeURIComponent(pathParts[2]);

        console.log(
          `[Mock Server] Looking for interaction: "${interactionName}"`,
        );
        console.log(
          `[Mock Server] Available interactions:`,
          this.dataStore.getJobs().map((j) => j.interactionName),
        );

        // Find job by interaction name
        const job = this.dataStore
          .getJobs()
          .find((j) => j.interactionName === interactionName);

        if (job) {
          console.log(
            `[Mock Server] Found interaction: ${job.interactionName}`,
          );
          
          // Transform eventSequence to match interface (eventName -> name, propName -> name in props)
          const transformedEvents = (job.eventSequence || []).map((event: any) => ({
            name: event.eventName || event.name,
            props: (event.props || []).map((prop: any) => ({
              name: prop.propName || prop.name,
              value: prop.propValue || prop.value,
              operator: prop.operator,
            })),
            isBlacklisted: event.isBlacklisted,
          }));

          // Transform globalBlacklistedEvents similarly
          const transformedGlobalBlacklistedEvents = (job.globalBlacklistedEvents || []).map((event: any) => ({
            name: event.eventName || event.name,
            props: (event.props || []).map((prop: any) => ({
              name: prop.propName || prop.name,
              value: prop.propValue || prop.value,
              operator: prop.operator,
            })),
            isBlacklisted: event.isBlacklisted,
          }));

          return {
            data: {
              name: job.interactionName,
              description: job.description || "",
              id: job.id,
              uptimeLowerLimitInMs: job.uptimeLowerLimit || 100,
              uptimeMidLimitInMs: job.uptimeMidLimit || 500,
              uptimeUpperLimitInMs: job.uptimeUpperLimit || 1000,
              thresholdInMs: job.interactionThreshold || 60000,
              status: job.status || "STOPPED", // Ensure status is always present
              events: transformedEvents,
              globalBlacklistedEvents: transformedGlobalBlacklistedEvents,
              createdAt: job.createdAt || Date.now(),
              createdBy: job.createdBy || "mock@dream11.com",
              updatedAt: job.updatedAt || Date.now(),
              updatedBy: job.updatedBy || "mock@dream11.com",
            },
            status: 200,
          };
        } else {
          console.log(
            `[Mock Server] Interaction '${interactionName}' not found`,
          );
          return {
            data: { error: `Interaction '${interactionName}' not found` },
            status: 404,
          };
        }
      }

      // Handle interactions list endpoint (GET /v1/interactions)
      if (
        (pathname === "/v1/interactions" ||
          pathname.endsWith("/v1/interactions")) &&
        method === "GET"
      ) {
        const url = new URL(request.url);
        const statusFilter = url.searchParams.get("status");
        const userEmailFilter = url.searchParams.get("userEmail");
        const interactionNameFilter = url.searchParams.get("interactionName");
        const pageParam = url.searchParams.get("page");
        const sizeParam = url.searchParams.get("size");

        let jobs = this.dataStore.getJobs();

        // Apply status filter
        if (statusFilter) {
          jobs = jobs.filter((job) => job.status === statusFilter);
        }

        // Apply userEmail filter (filter by createdBy)
        if (userEmailFilter) {
          jobs = jobs.filter(
            (job) => job.createdBy?.toLowerCase() === userEmailFilter.toLowerCase(),
          );
        }

        // Apply interactionName filter (search)
        if (interactionNameFilter) {
          const searchLower = interactionNameFilter.toLowerCase();
          jobs = jobs.filter(
            (job) =>
              job.interactionName?.toLowerCase().includes(searchLower),
          );
        }

        // Transform jobs to interaction format
        let interactions = jobs.map((job) => ({
          name: job.interactionName,
          interactionName: job.interactionName, // Keep for backward compatibility
          description: job.description || "",
          id: job.id,
          status: job.status || "STOPPED", // Ensure status is always present
          events: job.eventSequence || [],
          eventSequence: job.eventSequence || [], // Keep for backward compatibility
          globalBlacklistedEvents: job.globalBlacklistedEvents || [],
          createdAt: job.createdAt || Date.now(),
          createdBy: job.createdBy || "mock@dream11.com",
          updatedAt: job.updatedAt || Date.now(),
          updatedBy: job.updatedBy || "mock@dream11.com",
        }));

        // Apply pagination
        const totalInteractions = interactions.length;
        const page = pageParam ? parseInt(pageParam, 10) : 0;
        const size = sizeParam ? parseInt(sizeParam, 10) : 10;

        if (page >= 0 && size > 0) {
          const startIndex = page * size;
          const endIndex = startIndex + size;
          interactions = interactions.slice(startIndex, endIndex);
        }

        return {
          data: {
            interactions: interactions,
            totalInteractions: totalInteractions,
          },
          status: 200,
        };
      }

      // Handle create interaction endpoint (POST /v1/interactions)
      if (
        (pathname === "/v1/interactions" ||
          pathname.endsWith("/v1/interactions")) &&
        method === "POST"
      ) {
        const requestBody = JSON.parse(request.body || "{}");
        
        // Check if interaction with same name already exists
        const existingJob = this.dataStore.findJobByName(requestBody.name);
        if (existingJob) {
          return {
            data: null,
            status: 400,
            error: {
              code: "INTERACTION_EXISTS",
              message: "Interaction already exists",
              cause: `Interaction with name '${requestBody.name}' already exists`,
            },
          };
        }

        // Transform request body to job format
        const newJob = {
          id: Date.now(),
          interactionName: requestBody.name,
          description: requestBody.description || "",
          status: requestBody.status || "STOPPED",
          uptimeLowerLimit: requestBody.uptimeLowerLimitInMs || 100,
          uptimeMidLimit: requestBody.uptimeMidLimitInMs || 500,
          uptimeUpperLimit: requestBody.uptimeUpperLimitInMs || 1000,
          interactionThreshold: requestBody.thresholdInMs || 60000,
          eventSequence: (requestBody.events || []).map((event: any) => ({
            eventName: event.name,
            props: (event.props || []).map((prop: any) => ({
              propName: prop.name,
              propValue: prop.value,
              operator: prop.operator,
            })),
            isBlacklisted: event.isBlacklisted,
          })),
          globalBlacklistedEvents: (requestBody.globalBlacklistedEvents || []).map((event: any) => ({
            eventName: event.name,
            props: (event.props || []).map((prop: any) => ({
              propName: prop.name,
              propValue: prop.value,
              operator: prop.operator,
            })),
            isBlacklisted: event.isBlacklisted,
          })),
          createdAt: Date.now(),
          createdBy: requestBody.createdBy || "mock@dream11.com",
          updatedAt: Date.now(),
          updatedBy: requestBody.updatedBy || "mock@dream11.com",
        };

        this.dataStore.addJob(newJob);

        return {
          data: {
            name: newJob.interactionName,
            description: newJob.description,
            id: newJob.id,
            status: newJob.status,
            createdAt: newJob.createdAt,
            createdBy: newJob.createdBy,
          },
          status: 201,
        };
      }

      // Handle update interaction endpoint (PUT /v1/interactions/{name})
      if (
        pathParts.length >= 3 &&
        pathParts[0] === "v1" &&
        pathParts[1] === "interactions" &&
        pathParts[2] &&
        method === "PUT"
      ) {
        const interactionName = decodeURIComponent(pathParts[2]);
        const requestBody = JSON.parse(request.body || "{}");
        const jobDetails = requestBody.jobDetails || requestBody;

        const existingJob = this.dataStore.findJobByName(interactionName);
        if (!existingJob) {
          return {
            data: null,
            status: 404,
            error: {
              code: "INTERACTION_NOT_FOUND",
              message: "Interaction not found",
              cause: `Interaction with name '${interactionName}' not found`,
            },
          };
        }

        // Update the job with new data
        const updates: any = {
          description: jobDetails.description,
          uptimeLowerLimit: jobDetails.uptimeLowerLimitInMs,
          uptimeMidLimit: jobDetails.uptimeMidLimitInMs,
          uptimeUpperLimit: jobDetails.uptimeUpperLimitInMs,
          interactionThreshold: jobDetails.thresholdInMs,
          updatedAt: Date.now(),
          updatedBy: requestBody.user || existingJob.updatedBy,
          // Preserve status - use new status if provided, otherwise keep existing
          status: jobDetails.status || existingJob.status || "STOPPED",
        };

        if (jobDetails.events) {
          updates.eventSequence = jobDetails.events.map((event: any) => ({
            eventName: event.name,
            props: (event.props || []).map((prop: any) => ({
              propName: prop.name,
              propValue: prop.value,
              operator: prop.operator,
            })),
            isBlacklisted: event.isBlacklisted,
          }));
        }

        if (jobDetails.globalBlacklistedEvents) {
          updates.globalBlacklistedEvents = jobDetails.globalBlacklistedEvents.map((event: any) => ({
            eventName: event.name,
            props: (event.props || []).map((prop: any) => ({
              propName: prop.name,
              propValue: prop.value,
              operator: prop.operator,
            })),
            isBlacklisted: event.isBlacklisted,
          }));
        }

        this.dataStore.updateJobByName(interactionName, updates);

        return {
          data: {
            jobId: existingJob.id,
            status: 200,
          },
          status: 200,
        };
      }

      // Handle delete interaction endpoint (DELETE /v1/interactions/{name})
      if (
        pathParts.length >= 3 &&
        pathParts[0] === "v1" &&
        pathParts[1] === "interactions" &&
        pathParts[2] &&
        method === "DELETE"
      ) {
        const interactionName = decodeURIComponent(pathParts[2]);

        const existingJob = this.dataStore.findJobByName(interactionName);
        if (!existingJob) {
          return {
            data: null,
            status: 404,
            error: {
              code: "INTERACTION_NOT_FOUND",
              message: "Interaction not found",
              cause: `Interaction with name '${interactionName}' not found`,
            },
          };
        }

        this.dataStore.deleteJobByName(interactionName);

        return {
          data: {
            status: 200,
          },
          status: 200,
        };
      }
    }

    if (pathname.includes("/getJobDetails")) {
      const url = new URL(request.url);
      const jobId = url.searchParams.get("jobId");
      const useCaseId = url.searchParams.get("useCaseId");

      // Try to find job by jobId first, then by useCaseId (name)
      let job = null;
      if (jobId) {
        job = this.dataStore.getJobs().find((j) => j.id === parseInt(jobId));
      } else if (useCaseId) {
        job = this.dataStore
          .getJobs()
          .find((j) => j.name === useCaseId || j.useCaseName === useCaseId);
      }

      if (job) {
        if (this.config.shouldLog()) {
          console.log(`[Mock Server] GET_JOB_DETAILS - Found job:`, job);
        }

        // Transform job data to match JobDetailsResponse interface
        const jobDetailsResponse = {
          jobId: job.id,
          useCaseName: job.useCaseName || job.name,
          upTimeLowerLimit: job.upTimeLowerLimit,
          eventNameT1: job.eventNameT1,
          eventNameT2: job.eventNameT2,
          windowInterval: job.windowInterval,
          hopWindowInterval: job.hopWindowInterval,
          blacklistedEvents: job.blacklistedEvents || {},
          upTimeUpperLimit: job.upTimeUpperLimit,
          upTimeMidLimit: job.upTimeMidLimit,
          globalBlacklistedEvents: job.globalBlacklistedEvents || [],
          eventSeq: job.eventSeq || [],
          propsFilter: {
            eventT1: job.propsFilter?.eventT1 || {},
            eventT2: job.propsFilter?.eventT2 || {},
            globalBlackListedEvent:
              job.propsFilter?.globalBlackListedEvent || {},
            whiteListedEvent: job.propsFilter?.whiteListedEvent || {},
            blacklistedEvents: job.propsFilter?.blacklistedEvents || {},
          },
          metaData: {
            id: job.id,
            name: job.name,
            description: job.description,
            status: job.status,
            createdBy: job.createdBy,
            updatedBy: job.updatedBy,
            createdAt: job.createdAt,
            updatedAt: job.updatedAt,
            useCaseName: job.useCaseName || job.name,
            upTimeLowerLimit: job.upTimeLowerLimit,
            upTimeUpperLimit: job.upTimeUpperLimit,
            upTimeMidLimit: job.upTimeMidLimit,
            eventNameT1: job.eventNameT1,
            eventNameT2: job.eventNameT2,
            windowInterval: job.windowInterval,
            hopWindowInterval: job.hopWindowInterval,
            blacklistedEvents: job.blacklistedEvents || {},
            globalBlacklistedEvents: job.globalBlacklistedEvents || [],
            eventSeq: job.eventSeq || [],
            propsFilter: {
              eventT1: job.propsFilter?.eventT1 || {},
              eventT2: job.propsFilter?.eventT2 || {},
              globalBlackListedEvent:
                job.propsFilter?.globalBlackListedEvent || {},
              whiteListedEvent: job.propsFilter?.whiteListedEvent || {},
              blacklistedEvents: job.propsFilter?.blacklistedEvents || {},
            },
            jobVersion: job.jobVersion || "V1",
          },
          jobVersion: job.jobVersion || "V1",
          description: job.description,
        };

        return {
          data: jobDetailsResponse,
          status: 200,
        };
      }

      return {
        data: null,
        status: 404,
        error: {
          code: "JOB_NOT_FOUND",
          message: "Job not found",
          cause: "Invalid job ID or useCaseId",
        },
      };
    }

    if (pathname.includes("/getJobStatus")) {
      const url = new URL(request.url);
      const jobId = url.searchParams.get("jobId");

      if (this.config.shouldLog()) {
        console.log(`[Mock Server] GET_JOB_STATUS - Job ID: ${jobId}`);
      }

      const job = this.dataStore
        .getJobs()
        .find((j) => j.id === parseInt(jobId || "0"));

      if (job) {
        const response = {
          data: {
            status: job.status || "RUNNING",
          },
          status: 200,
        };

        if (this.config.shouldLog()) {
          console.log(`[Mock Server] GET_JOB_STATUS Response:`, response);
        }

        return response;
      }

      return {
        data: null,
        status: 404,
        error: {
          code: "JOB_NOT_FOUND",
          message: "Job not found",
          cause: "Invalid job ID",
        },
      };
    }

    if (pathname.includes("/getJobFilters")) {
      // Get unique users and statuses from actual job data
      const jobs = this.dataStore.getJobs();
      const uniqueUsers = Array.from(
        new Set(
          jobs
            .map((job) => job.createdBy)
            .filter((user): user is string => Boolean(user)),
        ),
      );
      const uniqueStatuses = Array.from(
        new Set(
          jobs
            .map((job) => job.status)
            .filter((status): status is string => Boolean(status)),
        ),
      );

      return {
        data: {
          users: uniqueUsers.length > 0 ? uniqueUsers : ["mock@dream11.com"],
          statuses: uniqueStatuses.length > 0 ? uniqueStatuses : ["RUNNING", "STOPPED"],
        },
        status: 200,
      };
    }

    if (pathname.includes("/updateJobStatus")) {
      const requestBody = JSON.parse(request.body || "{}");
      const { useCaseId, action, user } = requestBody;

      if (this.config.shouldLog()) {
        console.log(`[Mock Server] UPDATE_JOB_STATUS - Request:`, requestBody);
      }

      // Find the job by useCaseId (interaction name)
      const job = this.dataStore.findJobByName(useCaseId) ||
        this.dataStore.getJobs().find((j) => j.name === useCaseId || j.useCaseName === useCaseId);

      if (job) {
        // Update the job status
        const newStatus = action === "RUNNING" ? "RUNNING" : "STOPPED";
        const interactionName = job.interactionName || job.name || useCaseId;
        this.dataStore.updateJobByName(interactionName, {
          status: newStatus,
          updatedAt: Date.now(),
          updatedBy: user || "unknown",
        });

        const response = {
          data: {
            id: job.id,
            status: 200,
            message: `Successfully ${action === "RUNNING" ? "started" : "stopped"} tracking for ${useCaseId}`,
            job: {
              id: job.id,
              name: job.name,
              status: newStatus,
            },
          },
          status: 200,
        };

        if (this.config.shouldLog()) {
          console.log(`[Mock Server] UPDATE_JOB_STATUS - Response:`, response);
        }

        return response;
      }

      return {
        data: null,
        status: 404,
        error: {
          code: "JOB_NOT_FOUND",
          message: "Job not found",
          cause: "Invalid useCaseId",
        },
      };
    }

    if (pathname.includes("/create")) {
      const newJob = {
        id: Date.now(),
        ...JSON.parse(request.body || "{}"),
        status: "STOPPED",
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };
      this.dataStore.addJob(newJob);

      return {
        data: newJob,
        status: 201,
      };
    }

    if (pathname.includes("/deleteJob")) {
      const url = new URL(request.url);
      const jobId = url.searchParams.get("jobId");

      if (jobId) {
        this.dataStore.deleteJob(parseInt(jobId));
        return {
          data: { success: true, jobId: parseInt(jobId) },
          status: 200,
        };
      }

      return this.generateErrorResponse();
    }

    return {
      data: { message: "Job endpoint not implemented" },
      status: 200,
    };
  }

  private handleAlertEndpoints(
    pathname: string,
    method: string,
    request: MockRequest,
  ): MockResponse {
    if (pathname.includes("/alert/filters") && method === "GET") {
      return {
        data: {
          created_by: [
            "user1@dream11.com",
            "user2@dream11.com",
            "user3@dream11.com",
          ],
          updated_by: [
            "user1@dream11.com",
            "user2@dream11.com",
            "user3@dream11.com",
          ],
          job_ids: ["1", "2", "3", "4", "5"],
          current_states: [
            "FIRING",
            "NORMAL",
            "ERRORED",
            "SILENCED",
            "NO_DATA",
          ],
        },
        status: 200,
      };
    }

    if (
      pathname.includes("/alert/") &&
      pathname.includes("/evaluationHistory") &&
      method === "GET"
    ) {
      // Handle alert evaluation history - MUST come before general alert details
      console.log(
        "[Mock Server] EVALUATION_HISTORY - Pathname:",
        pathname,
        "Method:",
        method,
      );
      const alertIdMatch = pathname.match(/\/alert\/(\d+)\/evaluationHistory/);
      if (alertIdMatch) {
        const alertId = parseInt(alertIdMatch[1]);

        if (this.config.shouldLog()) {
          console.log(
            `[Mock Server] GET_ALERT_EVALUATION_HISTORY - Alert ID: ${alertId}`,
          );
        }

        // Generate mock evaluation history
        const history = [];
        const now = Date.now();
        for (let i = 0; i < 10; i++) {
          const isFiring = i % 3 === 0;
          const reading = Math.round((0.1 + Math.random() * 0.8) * 100) / 100; // Random value between 0.1 and 0.9, rounded to 2 decimal places
          const threshold = 0.5;
          const totalInteractions = 100 + Math.floor(Math.random() * 200);
          const successInteractions = Math.floor(
            totalInteractions * (0.7 + Math.random() * 0.3),
          );
          const errorInteractions = totalInteractions - successInteractions;

          history.push({
            reading: reading,
            threshold: threshold,
            evaluated_at: new Date(now - i * 3600000).toISOString(), // Every hour
            current_state: isFiring ? "FIRING" : "NORMAL",
            evaluation_time: Math.round((2 + Math.random() * 3) * 100) / 100, // 2-5 seconds, rounded to 2 decimal places
            total_interaction_count: totalInteractions,
            error_interaction_count: errorInteractions,
            success_interaction_count: successInteractions,
            min_interaction_count: 50,
            min_success_interaction_count: 40,
            min_error_interaction_count: 5,
          });
        }

        const response = {
          data: history, // Return the array directly - processServerResponse will wrap it
          status: 200,
        };

        console.log("[Mock Server] EVALUATION_HISTORY - Returning:", response);
        console.log(
          "[Mock Server] EVALUATION_HISTORY - History type:",
          typeof history,
        );
        console.log(
          "[Mock Server] EVALUATION_HISTORY - History isArray:",
          Array.isArray(history),
        );
        console.log(
          "[Mock Server] EVALUATION_HISTORY - History length:",
          history.length,
        );

        return response;
      }
    }

    if (pathname.includes("/alert/") && method === "GET") {
      // Handle individual alert details (e.g., /v1/alert/1) - MUST come after evaluation history
      const alertIdMatch = pathname.match(/\/alert\/(\d+)$/); // More specific pattern
      if (alertIdMatch) {
        const alertId = parseInt(alertIdMatch[1]);
        const alert = this.dataStore
          .getAlerts()
          .find((a) => a.alert_id === alertId);

        if (alert) {
          if (this.config.shouldLog()) {
            console.log(
              `[Mock Server] GET_ALERT_DETAILS - Found alert:`,
              alert,
            );
          }

          return {
            data: alert,
            status: 200,
          };
        }

        return {
          data: null,
          status: 404,
          error: {
            code: "ALERT_NOT_FOUND",
            message: "Alert not found",
            cause: "Invalid alert ID",
          },
        };
      }
    }

    if (pathname.includes("/alert") && method === "GET") {
      const alerts = this.dataStore.getAlerts();
      return {
        data: {
          total_alerts: alerts.length,
          alerts: alerts,
          limit: 10,
          offset: 0,
        },
        status: 200,
      };
    }

    if (pathname.includes("/alert") && method === "POST") {
      const requestBody = JSON.parse(request.body || "{}");

      if (this.config.shouldLog()) {
        console.log(`[Mock Server] CREATE_ALERT - Request body:`, requestBody);
      }

      const newAlert = {
        alert_id: Date.now(),
        ...requestBody,
        created_at: new Date().toISOString(),
        updated_at: new Date().toISOString(),
        is_snoozed: false,
        snoozed_from: 0,
        snoozed_until: 0,
        handleDuplicateAlert: async () => {},
      };

      // Remove alert_id from request body if it exists (for duplication)
      if (newAlert.alert_id === undefined) {
        newAlert.alert_id = Date.now();
      }

      this.dataStore.addAlert(newAlert);

      if (this.config.shouldLog()) {
        console.log(`[Mock Server] CREATE_ALERT - Created alert:`, newAlert);
      }

      return {
        data: newAlert,
        status: 201,
      };
    }

    if (pathname.includes("/alert") && method === "PUT") {
      const body = JSON.parse(request.body || "{}");
      const alertId = body.id;

      this.dataStore.updateAlert(alertId, body);

      return {
        data: { success: true, alertId },
        status: 200,
      };
    }

    if (pathname.includes("/alert") && method === "DELETE") {
      const url = new URL(request.url);
      const alertId = url.searchParams.get("id");

      if (alertId) {
        this.dataStore.deleteAlert(parseInt(alertId));
        return {
          data: { success: true, alertId: parseInt(alertId) },
          status: 200,
        };
      }

      return this.generateErrorResponse();
    }

    if (
      pathname.includes("/alert/") &&
      pathname.includes("/snooze") &&
      method === "POST"
    ) {
      // Handle snooze alert
      const alertIdMatch = pathname.match(/\/alert\/(\d+)\/snooze/);
      if (alertIdMatch) {
        const alertId = parseInt(alertIdMatch[1]);
        const requestBody = JSON.parse(request.body || "{}");

        if (this.config.shouldLog()) {
          console.log(
            `[Mock Server] SNOOZE_ALERT - Alert ID: ${alertId}`,
            requestBody,
          );
        }

        // Update alert to snoozed state
        this.dataStore.updateAlert(alertId, {
          is_snoozed: true,
          snoozed_from: requestBody.snoozeFrom || Date.now(),
          snoozed_until: requestBody.snoozeUntil || Date.now() + 3600000, // 1 hour default
          current_state: "SILENCED",
        });

        return {
          data: {
            isSnoozed: true,
            snoozedFrom: requestBody.snoozeFrom || Date.now(),
            snoozedUntil: requestBody.snoozeUntil || Date.now() + 3600000,
          },
          status: 200,
        };
      }
    }

    if (
      pathname.includes("/alert/") &&
      pathname.includes("/snooze") &&
      method === "DELETE"
    ) {
      // Handle resume alert (remove snooze)
      const alertIdMatch = pathname.match(/\/alert\/(\d+)\/snooze/);
      if (alertIdMatch) {
        const alertId = parseInt(alertIdMatch[1]);

        if (this.config.shouldLog()) {
          console.log(`[Mock Server] RESUME_ALERT - Alert ID: ${alertId}`);
        }

        // Update alert to remove snooze
        this.dataStore.updateAlert(alertId, {
          is_snoozed: false,
          snoozed_from: 0,
          snoozed_until: 0,
          current_state: "FIRING", // Resume to firing state
        });

        return {
          data: {
            isSnoozed: false,
            snoozedFrom: 0,
            snoozedUntil: 0,
          },
          status: 200,
        };
      }
    }

    return {
      data: { message: "Alert endpoint not implemented" },
      status: 200,
    };
  }

  private handleAnalyticsEndpoints(
    pathname: string,
    method: string,
    request: MockRequest,
  ): MockResponse {
    const requestBody = request.body ? JSON.parse(request.body) : {};
    const useCaseId = requestBody.useCaseId || "ContestJoinSuccess";

    // Generate realistic time-series data
    const timeSeriesData = this.generateTimeSeriesData(30, useCaseId); // 30 data points

    if (pathname.includes("/getApdexScore")) {
      return this.handleApdexScoreEndpoint(timeSeriesData, useCaseId);
    }

    if (pathname.includes("/getErrorRate")) {
      return this.handleErrorRateEndpoint(timeSeriesData, useCaseId);
    }

    if (pathname.includes("/getInteractionTime")) {
      return this.handleInteractionTimeEndpoint(timeSeriesData, useCaseId);
    }

    if (pathname.includes("/getInteractionCategory")) {
      return this.handleUserCategorizationEndpoint(timeSeriesData, useCaseId);
    }

    // Default analytics response
    return {
      data: { message: "Analytics endpoint not implemented" },
      status: 200,
    };
  }

  private generateTimeSeriesData(pointCount: number, useCaseId?: string) {
    const now = Date.now();
    const data = [];

    // Different patterns for different use cases
    const useCasePattern = this.getUseCasePattern(useCaseId);

    // Base values for consistent patterns
    let apdexScore = useCasePattern.apdexBase;
    let errorRate = useCasePattern.errorBase;
    let p50 = useCasePattern.p50Base;
    let p95 = useCasePattern.p95Base;
    let p99 = useCasePattern.p99Base;
    let excellent = useCasePattern.excellentBase;
    let good = useCasePattern.goodBase;
    let average = useCasePattern.averageBase;
    let poor = useCasePattern.poorBase;

    for (let i = 0; i < pointCount; i++) {
      const timestamp = Math.floor((now - (pointCount - i) * 60000) / 1000); // 1 minute intervals

      // Create realistic patterns with gradual changes
      // Apdex score: generally stable with occasional dips
      apdexScore = this.applyRealisticVariation(
        apdexScore,
        0.7,
        1.0,
        0.02,
        i,
        useCasePattern.pattern,
      );

      // Error rate: generally low with occasional spikes
      errorRate = this.applyRealisticVariation(
        errorRate,
        0.001,
        0.08,
        0.005,
        i,
        useCasePattern.pattern,
      );

      // Interaction times: generally stable with gradual trends
      p50 = this.applyRealisticVariation(
        p50,
        150,
        1200,
        20,
        i,
        useCasePattern.pattern,
      );
      p95 = this.applyRealisticVariation(
        p95,
        400,
        2500,
        50,
        i,
        useCasePattern.pattern,
      );
      p99 = this.applyRealisticVariation(
        p99,
        800,
        4000,
        100,
        i,
        useCasePattern.pattern,
      );

      // User categorization: maintain extremely stable proportions with minimal variation
      const totalUsers = 80 + Math.floor(Math.random() * 20); // 80-100 total users

      // Generate poor users as approximately 8% of total users
      poor = Math.floor(totalUsers * (0.07 + Math.random() * 0.02)); // 7-9% (around 8%)

      // Generate other categories with stable proportions
      excellent = Math.floor(
        this.applyStableVariation(
          excellent,
          30,
          60,
          0.1,
          i,
          useCasePattern.pattern,
        ),
      );
      good = Math.floor(
        this.applyStableVariation(good, 15, 35, 0.1, i, useCasePattern.pattern),
      );
      average = Math.floor(
        this.applyStableVariation(
          average,
          8,
          20,
          0.05,
          i,
          useCasePattern.pattern,
        ),
      );

      // Normalize remaining categories to total users minus poor users
      const remainingUsers = totalUsers - poor;
      const currentTotal = excellent + good + average;
      if (currentTotal > 0) {
        const scale = remainingUsers / currentTotal;
        excellent = Math.floor(excellent * scale);
        good = Math.floor(good * scale);
        average = Math.floor(average * scale);
      }

      data.push({
        timestamp,
        apdexScore: Math.max(0, Math.min(1, apdexScore)),
        errorRate: Math.max(0, Math.min(0.1, errorRate)),
        p50: Math.max(50, p50),
        p95: Math.max(200, p95),
        p99: Math.max(500, p99),
        excellent: Math.max(0, excellent),
        good: Math.max(0, good),
        average: Math.max(0, average),
        poor: Math.max(0, poor),
      });
    }

    return data;
  }

  private getUseCasePattern(useCaseId?: string) {
    const patterns = {
      ContestJoinSuccess: {
        apdexBase: 0.88, // High performance
        errorBase: 0.015, // Low errors
        p50Base: 250, // Fast response
        p95Base: 600,
        p99Base: 1200,
        excellentBase: 50,
        goodBase: 30,
        averageBase: 15,
        poorBase: 5,
        pattern: "stable", // Consistent performance
      },
      CreateTeamSuccess: {
        apdexBase: 0.82, // Good performance
        errorBase: 0.025, // Some errors
        p50Base: 400, // Moderate response
        p95Base: 1000,
        p99Base: 2000,
        excellentBase: 40,
        goodBase: 35,
        averageBase: 20,
        poorBase: 5,
        pattern: "gradual", // Gradual improvements
      },
      CreateTeamPageLoaded: {
        apdexBase: 0.9, // Very high performance
        errorBase: 0.01, // Very low errors
        p50Base: 200, // Very fast response
        p95Base: 500,
        p99Base: 1000,
        excellentBase: 55,
        goodBase: 30,
        averageBase: 10,
        poorBase: 5,
        pattern: "stable", // Very stable
      },
      ContestHomeLanded: {
        apdexBase: 0.85, // Good performance
        errorBase: 0.02, // Low errors
        p50Base: 350, // Good response
        p95Base: 800,
        p99Base: 1500,
        excellentBase: 45,
        goodBase: 30,
        averageBase: 20,
        poorBase: 5,
        pattern: "cyclical", // Daily patterns
      },
      AddCashLoaded: {
        apdexBase: 0.92, // Excellent performance
        errorBase: 0.008, // Very low errors
        p50Base: 180, // Very fast response
        p95Base: 450,
        p99Base: 900,
        excellentBase: 60,
        goodBase: 25,
        averageBase: 10,
        poorBase: 5,
        pattern: "stable", // Very stable
      },
      default: {
        apdexBase: 0.85, // Good performance
        errorBase: 0.02, // Low errors
        p50Base: 300, // Good response
        p95Base: 800,
        p99Base: 1500,
        excellentBase: 45,
        goodBase: 25,
        averageBase: 15,
        poorBase: 5,
        pattern: "stable", // Default stable pattern
      },
    };

    return patterns[useCaseId as keyof typeof patterns] || patterns.default;
  }

  private applyRealisticVariation(
    currentValue: number,
    min: number,
    max: number,
    maxChange: number,
    index: number,
    pattern: string = "stable",
  ): number {
    // Create more realistic patterns based on use case
    const timeOfDay = (index % 24) / 24; // Simulate daily patterns
    const isPeakHour = timeOfDay >= 0.1 && timeOfDay <= 0.2; // 10-20% of day is peak
    const isLowHour = timeOfDay >= 0.6 && timeOfDay <= 0.8; // 60-80% of day is low activity

    let cycleFactor = 0;
    let trendFactor = 0;
    let eventFactor = 0;

    switch (pattern) {
      case "stable":
        // Very stable with minimal variation
        cycleFactor = Math.sin((index / 20) * Math.PI) * 0.05;
        trendFactor = 0;
        eventFactor =
          index % 20 === 0 && Math.random() < 0.1
            ? (Math.random() - 0.5) * 0.1
            : 0;
        break;
      case "gradual":
        // Gradual improvements over time
        cycleFactor = Math.sin((index / 15) * Math.PI) * 0.08;
        trendFactor = (index / 30) * 0.02; // Slight improvement trend
        eventFactor =
          index % 15 === 0 && Math.random() < 0.2
            ? (Math.random() - 0.5) * 0.15
            : 0;
        break;
      case "cyclical":
        // Strong daily patterns
        cycleFactor = Math.sin((index / 12) * Math.PI) * 0.15;
        trendFactor = 0;
        eventFactor =
          index % 10 === 0 && Math.random() < 0.3
            ? (Math.random() - 0.5) * 0.2
            : 0;
        break;
      default:
        // Default stable pattern
        cycleFactor = Math.sin((index / 10) * Math.PI) * 0.1;
        trendFactor = (index / 30) * 0.05;
        eventFactor =
          index % 12 === 0 && Math.random() < 0.3
            ? (Math.random() - 0.5) * 0.3
            : 0;
    }

    // Calculate change based on patterns
    let change = (Math.random() - 0.5) * maxChange;
    change += cycleFactor * maxChange;
    change += trendFactor * maxChange;
    change += eventFactor * maxChange;

    // Apply peak/low hour adjustments
    if (isPeakHour) {
      change *= 1.5; // More variation during peak hours
    } else if (isLowHour) {
      change *= 0.5; // Less variation during low hours
    }

    const newValue = currentValue + change;
    return Math.round(Math.max(min, Math.min(max, newValue)) * 100) / 100;
  }

  private applyStableVariation(
    currentValue: number,
    min: number,
    max: number,
    maxChange: number,
    index: number,
    pattern: string = "stable",
  ): number {
    // Create extremely stable patterns for user categorization
    // Ultra-minimal variation to keep lines almost perfectly parallel to x-axis

    // Only extremely gentle sine wave for minimal variation
    const cycleFactor = Math.sin((index / 100) * Math.PI) * 0.005; // Ultra-gentle, very long cycle

    // No trend factor for user categorization
    const trendFactor = 0;

    // Extremely rare, extremely small events
    const isEvent = index % 50 === 0 && Math.random() < 0.02; // 2% chance every 50 points
    const eventFactor = isEvent ? (Math.random() - 0.5) * 0.02 : 0; // Extremely small event impact

    // Calculate ultra-minimal change
    let change = (Math.random() - 0.5) * maxChange * 0.1; // Reduce random variation by 90%
    change += cycleFactor * maxChange;
    change += trendFactor * maxChange;
    change += eventFactor * maxChange;

    // Apply additional ultra-stability factor
    change *= 0.2; // Further reduce all changes by 80%

    // Round to nearest integer for cleaner values
    const newValue = Math.round(currentValue + change);
    return Math.max(min, Math.min(max, newValue));
  }

  private handleApdexScoreEndpoint(timeSeriesData: any[], useCaseId: string) {
    const readings = timeSeriesData.map((point) => ({
      apdexScore: point.apdexScore.toFixed(3),
      timestamp: point.timestamp,
      useCaseId: useCaseId,
    }));

    return {
      data: {
        readings,
        jobComplete: true,
        jobReference: {
          jobId: `apdex_job_${Date.now()}`,
        },
      },
      status: 200,
    };
  }

  private handleErrorRateEndpoint(timeSeriesData: any[], useCaseId: string) {
    const readings = timeSeriesData.map((point) => ({
      errorRate: point.errorRate.toFixed(4),
      timestamp: point.timestamp,
      useCaseId: useCaseId,
    }));

    return {
      data: {
        readings,
        jobComplete: true,
        jobReference: {
          jobId: `error_rate_job_${Date.now()}`,
        },
      },
      status: 200,
    };
  }

  private handleInteractionTimeEndpoint(
    timeSeriesData: any[],
    useCaseId: string,
  ) {
    const readings = timeSeriesData.map((point) => ({
      p50: point.p50,
      p95: point.p95,
      p99: point.p99,
      timestamp: point.timestamp,
    }));

    return {
      data: {
        readings,
        jobComplete: true,
        jobReference: {
          jobId: `interaction_time_job_${Date.now()}`,
        },
      },
      status: 200,
    };
  }

  private handleUserCategorizationEndpoint(
    timeSeriesData: any[],
    useCaseId: string,
  ) {
    const readings = timeSeriesData.map((point) => ({
      excellent: point.excellent,
      good: point.good,
      average: point.average,
      poor: point.poor,
      timestamp: point.timestamp,
    }));

    return {
      data: {
        readings,
        jobComplete: true,
        jobReference: {
          jobId: `user_categorization_job_${Date.now()}`,
        },
      },
      status: 200,
    };
  }

  private handleInteractionInsightsEndpoint(
    pathname: string,
    method: string,
    request: MockRequest,
  ): MockResponse {
    const requestBody = request.body ? JSON.parse(request.body) : {};
    const { performanceMetric, groupBy, filters } = requestBody;

    if (this.config.shouldLog()) {
      console.log(
        "[Mock Server] Interaction Insights Request:",
        JSON.stringify(requestBody, null, 2),
      );
    }

    // Generate mock data based on the performance metric
    const readings = this.generateInteractionInsightsData(
      performanceMetric,
      groupBy,
      filters,
      requestBody,
    );

    return {
      data: {
        data: {
          readings,
        },
      },
      status: 200,
    };
  }

  private generateInteractionInsightsData(
    performanceMetric: string,
    groupBy: string[],
    filters: Record<string, string[]>,
    requestBody?: any,
  ) {
    const groupByDimension = groupBy[0];

    const mockDataByDimension: Record<string, string[]> = {
      APP_VERSION: ["v1.8.0", "v1.1.1", "v1.7.5", "v1.6.2"],
      DEVICE_MODEL: [
        "iPhone 14",
        "Samsung Galaxy S23",
        "Pixel 7",
        "OnePlus 11",
      ],
      OS_VERSION: ["iOS 17", "Android 14", "iOS 16", "Android 13"],
      NETWORK_PROVIDER: [
        "Jio",
        "Airtel",
        "Vi",
        "BSNL",
        "Reliance",
        "Tata",
        "Idea",
        "Vodafone",
      ],
      USER_CATEGORY: ["Premium", "Standard", "Free", "Trial"],
      GEO_STATE: [
        "Maharashtra",
        "Karnataka",
        "Tamil Nadu",
        "Delhi",
        "West Bengal",
      ],
    };

    const dimensionValues = mockDataByDimension[groupByDimension] || [
      "Unknown",
    ];

    const requestHash = requestBody
      ? this.simpleHash(JSON.stringify(requestBody))
      : 0;

    const getMetricValue = (
      metric: string,
      index: number,
      requestHash: number,
    ): number => {
      const seed = index * 1000;
      switch (metric) {
        case "APDEX":
          return parseFloat((0.75 + Math.random() * 0.2).toFixed(3));
        case "CRASH":
          return Math.floor(seed + Math.random() * 500);
        case "ANR":
          return Math.floor(seed * 0.5 + Math.random() * 200);
        case "FROZEN_FRAME":
          return Math.floor(seed * 0.8 + Math.random() * 300);
        case "LATENCY_P99":
          return Math.floor(2000 + seed + Math.random() * 1000);
        case "LATENCY_P95":
          return Math.floor(1500 + seed * 0.8 + Math.random() * 800);
        case "LATENCY_P50":
          return Math.floor(800 + seed * 0.5 + Math.random() * 400);
        case "ERROR_RATE":
          const baseValue = 0.01 + Math.random() * 0.05;
          const hashMultiplier = (requestHash % 100) / 1000;
          return parseFloat((baseValue + hashMultiplier).toFixed(4));
        default:
          return Math.floor(10000 + seed + Math.random() * 5000);
      }
    };

    const getErrorCountValue = (
      index: number,
      requestHash: number,
      errorType: "4xx" | "5xx",
    ): number => {
      const baseValues4xx = [456, 342, 278, 189, 167, 134, 98, 76];
      const baseValues5xx = [234, 189, 156, 98, 87, 65, 54, 43];
      const baseValues = errorType === "4xx" ? baseValues4xx : baseValues5xx;
      const value = baseValues[index] || baseValues[baseValues.length - 1];
      const variation = Math.floor((requestHash % 50) - 25);
      return Math.max(0, value + variation);
    };

    if (
      performanceMetric === "ERROR_RATE" &&
      groupByDimension === "NETWORK_PROVIDER"
    ) {
      const errorType: "4xx" | "5xx" =
        requestBody?.metadata?.errorType === "4xx" ||
        requestBody?.metadata?.errorType === "5xx"
          ? requestBody.metadata.errorType
          : requestHash % 2 === 0
            ? "4xx"
            : "5xx";
      const readings = dimensionValues.slice(0, 8).map((value, index) => ({
        performanceMetric: getErrorCountValue(index, requestHash, errorType),
        groupBy: [value],
      }));

      if (this.config.shouldLog()) {
        console.log(
          `[Mock Server] Generated ${errorType} Error Data:`,
          JSON.stringify(readings, null, 2),
        );
      }

      return readings;
    }

    const readings = dimensionValues.map((value, index) => ({
      performanceMetric: getMetricValue(performanceMetric, index, requestHash),
      groupBy: [value],
    }));

    if (this.config.shouldLog()) {
      console.log(
        "[Mock Server] Generated Interaction Insights Data:",
        JSON.stringify(readings, null, 2),
      );
    }

    return readings;
  }

  private simpleHash(str: string): number {
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
      const char = str.charCodeAt(i);
      hash = (hash << 5) - hash + char;
      hash = hash & hash;
    }
    return Math.abs(hash);
  }

  private handleJobBasedGraphEndpoints(
    pathname: string,
    method: string,
    request: MockRequest,
  ) {
    const requestBody = request.body ? JSON.parse(request.body) : {};
    const useCaseId = requestBody.useCaseId || "ContestJoinSuccess";

    // Extract job ID from path
    const jobIdMatch = pathname.match(/\/job\/([^/]+)/);
    const jobId = jobIdMatch ? jobIdMatch[1] : "unknown_job";

    // Generate time-series data for job-based responses
    const timeSeriesData = this.generateTimeSeriesData(30, useCaseId);

    if (pathname.includes("getApdexScore")) {
      return this.handleJobBasedApdexScore(timeSeriesData, useCaseId, jobId);
    }

    if (pathname.includes("getErrorRate")) {
      return this.handleJobBasedErrorRate(timeSeriesData, useCaseId, jobId);
    }

    if (pathname.includes("getInteractionTime")) {
      return this.handleJobBasedInteractionTime(
        timeSeriesData,
        useCaseId,
        jobId,
      );
    }

    if (pathname.includes("getInteractionCategory")) {
      return this.handleJobBasedUserCategorization(
        timeSeriesData,
        useCaseId,
        jobId,
      );
    }

    return {
      data: { message: "Job-based graph endpoint not implemented" },
      status: 200,
    };
  }

  private handleJobBasedApdexScore(
    timeSeriesData: any[],
    useCaseId: string,
    jobId: string,
  ) {
    const apdexResults = timeSeriesData.map((point) => ({
      empty: false,
      f: [{ v: point.timestamp }, { v: point.apdexScore }],
    }));

    return {
      data: {
        apdexResults,
        jobComplete: true,
        jobReference: {
          jobId: jobId,
        },
      },
      status: 200,
    };
  }

  private handleJobBasedErrorRate(
    timeSeriesData: any[],
    useCaseId: string,
    jobId: string,
  ) {
    const errorInteractionResults = timeSeriesData.map((point) => ({
      empty: false,
      f: [{ v: point.timestamp }, { v: point.errorRate }],
    }));

    return {
      data: {
        errorInteractionResults,
        jobComplete: true,
        jobReference: {
          jobId: jobId,
        },
      },
      status: 200,
    };
  }

  private handleJobBasedInteractionTime(
    timeSeriesData: any[],
    useCaseId: string,
    jobId: string,
  ) {
    const interactionTimeResults = timeSeriesData.map((point) => ({
      empty: false,
      f: [
        { v: point.timestamp },
        { v: point.p50 },
        { v: point.p95 },
        { v: point.p99 },
      ],
    }));

    return {
      data: {
        interactionTimeResults,
        jobComplete: true,
        jobReference: {
          jobId: jobId,
        },
      },
      status: 200,
    };
  }

  private handleJobBasedUserCategorization(
    timeSeriesData: any[],
    useCaseId: string,
    jobId: string,
  ) {
    const userCategorizationResults = timeSeriesData.map((point) => ({
      empty: false,
      f: [
        { v: point.timestamp },
        { v: point.excellent },
        { v: point.good },
        { v: point.average },
        { v: point.poor },
      ],
    }));

    return {
      data: {
        userCategorizationResults,
        jobComplete: true,
        jobReference: {
          jobId: jobId,
        },
      },
      status: 200,
    };
  }

  private handleQueryEndpoints(
    pathname: string,
    method: string,
    request: MockRequest,
  ): MockResponse {
    if (pathname.includes("/getListOfTables")) {
      return {
        data: [
          "user_events",
          "interaction_metrics",
          "error_logs",
          "performance_data",
        ],
        status: 200,
      };
    }

    if (pathname.includes("/getColumnNamesOfTable")) {
      const url = new URL(request.url);
      const tableName = url.searchParams.get("tableName");

      const columns = {
        user_events: ["user_id", "event_name", "timestamp", "properties"],
        interaction_metrics: [
          "interaction_id",
          "duration",
          "success",
          "timestamp",
        ],
        error_logs: ["error_id", "error_message", "timestamp", "severity"],
        performance_data: ["metric_name", "value", "timestamp", "tags"],
      };

      return {
        data: columns[tableName as keyof typeof columns] || [],
        status: 200,
      };
    }

    if (pathname.includes("/getQueryResult")) {
      return {
        data: {
          queryId: "mock_query_" + Date.now(),
          status: "COMPLETED",
          results: [
            { f: [{ v: "sample_data_1" }] },
            { f: [{ v: "sample_data_2" }] },
            { f: [{ v: "sample_data_3" }] },
          ],
        },
        status: 200,
      };
    }

    return {
      data: { message: "Query endpoint not implemented" },
      status: 200,
    };
  }

  private handleEventEndpoints(
    pathname: string,
    method: string,
    request: MockRequest,
  ): MockResponse {
    if (pathname.includes("/events") && method === "GET") {
      const events = this.dataStore.getEvents();
      return {
        data: events,
        status: 200,
      };
    }

    if (pathname.includes("/whitelist/events")) {
      const whitelistedEvents = this.dataStore
        .getEvents()
        .map((e) => e.eventName);
      return {
        data: whitelistedEvents,
        status: 200,
      };
    }

    return {
      data: { message: "Event endpoint not implemented" },
      status: 200,
    };
  }

  private handlePermissionEndpoints(
    pathname: string,
    method: string,
    request: MockRequest,
  ): MockResponse {
    if (pathname.includes("/permission/check")) {
      const requestBody = JSON.parse(request.body || "{}");
      const { id, type, userEmail, operation } = requestBody;

      if (this.config.shouldLog()) {
        console.log(`[Mock Server] CHECK_PERMISSIONS - Request:`, requestBody);
      }

      // Mock permission logic - allow all operations for demo purposes
      // In real app, this would check actual user permissions
      const isAllowed = this.checkUserPermission(
        id,
        type,
        userEmail,
        operation,
      );

      const response = {
        data: {
          isAllowed: isAllowed,
        },
        status: 200,
      };

      if (this.config.shouldLog()) {
        console.log(`[Mock Server] CHECK_PERMISSIONS - Response:`, response);
      }

      return response;
    }

    return {
      data: { message: "Permission endpoint not implemented" },
      status: 200,
    };
  }

  private checkUserPermission(
    id: string,
    type: string,
    userEmail: string,
    operation: string,
  ): boolean {
    // Mock permission logic
    // For demo purposes, allow all operations for authenticated users

    // Check if user is authenticated (has email)
    if (!userEmail || userEmail === "") {
      return false;
    }

    // Allow all operations for demo
    // In a real app, this would check:
    // - User roles and permissions
    // - Resource ownership
    // - Operation-specific permissions
    // - Team-based access control

    return true;
  }

  private handleUserEventsEndpoints(
    pathname: string,
    method: string,
    request: MockRequest,
  ): MockResponse {
    if (pathname.includes("/getUserEvent") && method === "POST") {
      const requestBody = JSON.parse(request.body || "{}");
      const { phoneNo, fetchTime } = requestBody;

      if (this.config.shouldLog()) {
        console.log(
          `[Mock Server] GET_USER_EVENTS - Phone: ${phoneNo}, FetchTime: ${fetchTime}`,
        );
      }

      // Generate mock user events
      const events = this.generateMockUserEvents(phoneNo, fetchTime);

      return {
        data: {
          events: events,
          count: events.length,
        },
        status: 200,
      };
    }

    return {
      data: { message: "User events endpoint not implemented" },
      status: 200,
    };
  }

  private generateMockUserEvents(phoneNo: string, fetchTime: string): any[] {
    const events = [];
    const baseTime = new Date(fetchTime).getTime();
    const eventTypes = [
      "contest_join_start",
      "contest_join_success",
      "contest_join_failed",
      "create_team_start",
      "create_team_success",
      "create_team_failed",
      "add_cash_start",
      "add_cash_success",
      "add_cash_failed",
      "withdraw_start",
      "withdraw_success",
      "withdraw_failed",
      "login_start",
      "login_success",
      "login_failed",
      "logout",
      "page_view",
      "button_click",
      "form_submit",
      "payment_initiated",
    ];

    // Generate 10-20 random events
    const eventCount = 10 + Math.floor(Math.random() * 11);

    for (let i = 0; i < eventCount; i++) {
      const eventType =
        eventTypes[Math.floor(Math.random() * eventTypes.length)];
      const eventTime =
        baseTime + i * 60000 + Math.floor(Math.random() * 30000); // 1-1.5 minute intervals

      events.push({
        eventId: `${eventType}-${eventTime}-${i}`,
        eventName: eventType,
        eventTimestamp: Math.floor(eventTime / 1000).toString(), // Convert to seconds
        globalProps: {
          eventTimestamp: Math.floor(eventTime / 1000).toString(),
          userId: `user_${phoneNo}`,
          sessionId: `session_${Math.random().toString(36).substr(2, 9)}`,
          platform: Math.random() > 0.5 ? "android" : "ios",
          appVersion: "1.0.0",
          deviceId: `device_${Math.random().toString(36).substr(2, 9)}`,
          timestamp: eventTime.toString(),
        },
        props: {
          screenName: this.getScreenNameForEvent(eventType),
          action: this.getActionForEvent(eventType),
          value: Math.floor(Math.random() * 1000),
          category: this.getCategoryForEvent(eventType),
          label: this.getLabelForEvent(eventType),
          ...this.getEventSpecificProps(eventType),
        },
      });
    }

    return events.sort(
      (a, b) =>
        Number(a.globalProps.eventTimestamp) -
        Number(b.globalProps.eventTimestamp),
    );
  }

  private getScreenNameForEvent(eventType: string): string {
    const screenMap: { [key: string]: string } = {
      contest_join_start: "ContestListScreen",
      contest_join_success: "ContestListScreen",
      contest_join_failed: "ContestListScreen",
      create_team_start: "CreateTeamScreen",
      create_team_success: "CreateTeamScreen",
      create_team_failed: "CreateTeamScreen",
      add_cash_start: "AddCashScreen",
      add_cash_success: "AddCashScreen",
      add_cash_failed: "AddCashScreen",
      withdraw_start: "WithdrawScreen",
      withdraw_success: "WithdrawScreen",
      withdraw_failed: "WithdrawScreen",
      login_start: "LoginScreen",
      login_success: "LoginScreen",
      login_failed: "LoginScreen",
      logout: "HomeScreen",
      page_view: "HomeScreen",
      button_click: "HomeScreen",
      form_submit: "FormScreen",
      payment_initiated: "PaymentScreen",
    };
    return screenMap[eventType] || "UnknownScreen";
  }

  private getActionForEvent(eventType: string): string {
    if (eventType.includes("start")) return "start";
    if (eventType.includes("success")) return "success";
    if (eventType.includes("failed")) return "failed";
    if (eventType.includes("click")) return "click";
    if (eventType.includes("submit")) return "submit";
    if (eventType.includes("view")) return "view";
    return "unknown";
  }

  private getCategoryForEvent(eventType: string): string {
    if (eventType.includes("contest")) return "Contest";
    if (eventType.includes("team")) return "Team";
    if (eventType.includes("cash") || eventType.includes("withdraw"))
      return "Payment";
    if (eventType.includes("login") || eventType.includes("logout"))
      return "Authentication";
    if (eventType.includes("page") || eventType.includes("button"))
      return "Navigation";
    return "General";
  }

  private getLabelForEvent(eventType: string): string {
    return eventType
      .replace(/_/g, " ")
      .replace(/\b\w/g, (l) => l.toUpperCase());
  }

  private getEventSpecificProps(eventType: string): { [key: string]: any } {
    const props: { [key: string]: any } = {};

    if (eventType.includes("contest")) {
      props.contestId = `contest_${Math.floor(Math.random() * 1000)}`;
      props.entryFee = Math.floor(Math.random() * 100) + 10;
      props.prizePool = Math.floor(Math.random() * 10000) + 1000;
    }

    if (eventType.includes("team")) {
      props.teamId = `team_${Math.floor(Math.random() * 1000)}`;
      props.playerCount = 11;
      props.formation = "4-4-2";
    }

    if (eventType.includes("cash") || eventType.includes("withdraw")) {
      props.amount = Math.floor(Math.random() * 1000) + 100;
      props.paymentMethod = Math.random() > 0.5 ? "UPI" : "Card";
      props.transactionId = `txn_${Math.random().toString(36).substr(2, 9)}`;
    }

    if (eventType.includes("login")) {
      props.loginMethod = Math.random() > 0.5 ? "OTP" : "Password";
      props.isNewUser = Math.random() > 0.7;
    }

    return props;
  }

  private handleUniversalQueryEndpoints(
    pathname: string,
    method: string,
    request: MockRequest,
  ): MockResponse {
    // Query validation endpoint
    if (pathname.includes("/validateQuery") && method === "POST") {
      const requestBody = JSON.parse(request.body || "{}");
      const { query } = requestBody;

      if (this.config.shouldLog()) {
        console.log(
          `[Mock Server] VALIDATE_QUERY - Query: ${query?.substring(0, 100)}...`,
        );
      }

      // Simple validation - check for basic SQL keywords
      const isValid = this.validateSQLQuery(query);

      return {
        data: {
          success: isValid,
          errorMessage: isValid ? undefined : "Invalid SQL syntax detected",
        },
        status: 200,
      };
    }

    // Get query ID endpoint (run query)
    if (pathname.includes("/getQueryResult") && method === "POST") {
      const requestBody = JSON.parse(request.body || "{}");
      const { query, emailId } = requestBody;

      if (this.config.shouldLog()) {
        console.log(
          `[Mock Server] GET_QUERY_ID - Query: ${query?.substring(0, 100)}..., Email: ${emailId}`,
        );
      }

      // Generate a unique request ID
      const requestId = `query_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;

      return {
        data: {
          requestId: requestId,
        },
        status: 200,
      };
    }

    // Fetch query data endpoint
    if (pathname.includes("/fetchQueryData") && method === "POST") {
      const requestBody = JSON.parse(request.body || "{}");
      const { requestId, pageToken } = requestBody;

      if (this.config.shouldLog()) {
        console.log(
          `[Mock Server] FETCH_QUERY_DATA - RequestId: ${requestId}, PageToken: ${pageToken}`,
        );
      }

      // Generate mock query results
      const mockData = this.generateMockQueryResults(requestId, pageToken);

      return {
        data: mockData,
        status: 200,
      };
    }

    // Cancel query endpoint
    if (pathname.includes("/cancelQueryRequest") && method === "POST") {
      const requestBody = JSON.parse(request.body || "{}");
      const { requestId } = requestBody;

      if (this.config.shouldLog()) {
        console.log(`[Mock Server] CANCEL_QUERY - RequestId: ${requestId}`);
      }

      return {
        data: {
          success: true,
        },
        status: 200,
      };
    }

    // Get query history endpoint
    if (pathname.includes("/getQuery/user") && method === "GET") {
      const url = new URL(request.url);
      const emailId = url.searchParams.get("emailId");

      if (this.config.shouldLog()) {
        console.log(`[Mock Server] GET_QUERY_HISTORY - Email: ${emailId}`);
      }

      const mockQueries = this.generateMockQueryHistory();

      return {
        data: {
          totalQueries: mockQueries.length,
          queries: mockQueries,
        },
        status: 200,
      };
    }

    // Get suggested queries endpoint
    if (pathname.includes("/getQuery/suggested") && method === "GET") {
      if (this.config.shouldLog()) {
        console.log(`[Mock Server] GET_SUGGESTED_QUERIES`);
      }

      const suggestedQueries = this.generateMockSuggestedQueries();

      return {
        data: {
          suggestedQuery: suggestedQueries,
        },
        status: 200,
      };
    }

    // Get list of tables endpoint
    if (pathname.includes("/getListOfTables") && method === "GET") {
      if (this.config.shouldLog()) {
        console.log(`[Mock Server] GET_LIST_OF_TABLES`);
      }

      const tables = this.generateMockTables();

      return {
        data: tables, // Return array directly, not wrapped in object
        status: 200,
      };
    }

    // Get column names of table endpoint
    if (pathname.includes("/getColumnNamesOfTable") && method === "GET") {
      const url = new URL(request.url);
      const tableName = url.searchParams.get("table");

      if (this.config.shouldLog()) {
        console.log(`[Mock Server] GET_COLUMN_NAMES - Table: ${tableName}`);
      }

      const columns = this.generateMockTableColumns(tableName || "");

      return {
        data: columns, // Return array directly, not wrapped in object
        status: 200,
      };
    }

    return {
      data: { message: "Universal query endpoint not implemented" },
      status: 200,
    };
  }

  private validateSQLQuery(query: string): boolean {
    if (!query || typeof query !== "string") {
      return false;
    }

    const trimmedQuery = query.trim().toLowerCase();

    // Basic SQL validation
    const hasSelect = trimmedQuery.includes("select");
    const hasFrom = trimmedQuery.includes("from");
    const hasValidKeywords = [
      "select",
      "from",
      "where",
      "group by",
      "order by",
      "limit",
      "having",
    ].some((keyword) => trimmedQuery.includes(keyword));

    // Check for dangerous operations (for demo purposes, we'll allow most)
    const hasDangerousOps = ["drop", "delete", "truncate", "alter"].some((op) =>
      trimmedQuery.includes(op),
    );

    return hasSelect && hasFrom && hasValidKeywords && !hasDangerousOps;
  }

  private generateMockQueryResults(requestId: string, pageToken: string): any {
    // Check if this is a last active today query
    if (requestId.startsWith("last_active_")) {
      return this.generateMockLastActiveTodayQueryResults(requestId, pageToken);
    }

    // Simulate different stages of query execution
    const isFirstPage = !pageToken;
    const isComplete = Math.random() > 0.3; // 70% chance of completion

    if (!isComplete) {
      // Query still running
      return {
        schema: { fields: [] },
        rows: [],
        totalRows: 0,
        pageToken: isFirstPage ? "page_1" : pageToken,
        jobComplete: false,
      };
    }

    // Query completed - return mock data
    const mockData = this.generateMockQueryData();

    return {
      schema: mockData.schema,
      rows: mockData.rows,
      totalRows: mockData.totalRows,
      pageToken: isFirstPage ? "page_2" : undefined,
      jobComplete: true,
    };
  }

  private generateMockLastActiveTodayQueryResults(
    requestId: string,
    pageToken: string,
  ): any {
    // Generate a random time within the last 24 hours
    const now = new Date();
    const randomHoursAgo = Math.floor(Math.random() * 24); // 0-23 hours ago
    const lastActiveTime = new Date(
      now.getTime() - randomHoursAgo * 60 * 60 * 1000,
    );

    // Convert to Unix timestamp (seconds)
    const unixTimestamp = Math.floor(lastActiveTime.getTime() / 1000);

    return {
      schema: {
        fields: [{ name: "last_active_timestamp", type: "TIMESTAMP" }],
      },
      rows: [
        {
          f: [{ v: unixTimestamp.toString() }],
        },
      ],
      totalRows: 1,
      pageToken: undefined,
      jobComplete: true,
    };
  }

  private generateMockQueryData(): any {
    const eventNames = [
      "contest_join_start",
      "contest_join_success",
      "contest_join_failed",
      "create_team_start",
      "create_team_success",
      "create_team_failed",
      "add_cash_start",
      "add_cash_success",
      "add_cash_failed",
      "login_start",
      "login_success",
      "login_failed",
      "page_view",
      "button_click",
      "form_submit",
    ];

    const platforms = ["android", "ios", "web"];
    const appVersions = ["1.0.0", "1.1.0", "1.2.0", "2.0.0"];

    const rows = [];
    const totalRows = 50 + Math.floor(Math.random() * 100);

    for (let i = 0; i < Math.min(20, totalRows); i++) {
      const eventName =
        eventNames[Math.floor(Math.random() * eventNames.length)];
      const platform = platforms[Math.floor(Math.random() * platforms.length)];
      const appVersion =
        appVersions[Math.floor(Math.random() * appVersions.length)];
      const timestamp = Date.now() - Math.floor(Math.random() * 86400000); // Last 24 hours

      rows.push({
        f: [
          { v: eventName },
          { v: platform },
          { v: appVersion },
          { v: timestamp.toString() },
          { v: `user_${Math.floor(Math.random() * 1000)}` },
          { v: `session_${Math.random().toString(36).substr(2, 9)}` },
        ],
      });
    }

    return {
      schema: {
        fields: [
          { name: "eventName" },
          { name: "platform" },
          { name: "appVersion" },
          { name: "eventTimestamp" },
          { name: "userId" },
          { name: "sessionId" },
        ],
      },
      rows: rows,
      totalRows: totalRows,
    };
  }

  private generateMockQueryHistory(): string[] {
    return [
      "SELECT eventName, COUNT(*) as count FROM d11_stream_analytics_multi_region.processed_events_partitioned_hourly WHERE eventTimestamp BETWEEN TIMESTAMP_SUB(CURRENT_TIMESTAMP(), interval 1 hour) AND CURRENT_TIMESTAMP() GROUP BY eventName ORDER BY count DESC LIMIT 10",
      "SELECT platform, appVersion, COUNT(*) as user_count FROM d11_stream_analytics_multi_region.processed_events_partitioned_hourly WHERE eventName = 'login_success' AND eventTimestamp BETWEEN TIMESTAMP_SUB(CURRENT_TIMESTAMP(), interval 24 hour) AND CURRENT_TIMESTAMP() GROUP BY platform, appVersion",
      "SELECT eventName, AVG(CAST(props.value AS FLOAT64)) as avg_value FROM d11_stream_analytics_multi_region.processed_events_partitioned_hourly WHERE props.value IS NOT NULL AND eventTimestamp BETWEEN TIMESTAMP_SUB(CURRENT_TIMESTAMP(), interval 7 day) AND CURRENT_TIMESTAMP() GROUP BY eventName HAVING avg_value > 0",
      "SELECT userId, COUNT(*) as event_count FROM d11_stream_analytics_multi_region.processed_events_partitioned_hourly WHERE eventTimestamp BETWEEN TIMESTAMP_SUB(CURRENT_TIMESTAMP(), interval 1 day) AND CURRENT_TIMESTAMP() GROUP BY userId ORDER BY event_count DESC LIMIT 100",
      "SELECT eventName, platform, COUNT(*) as count FROM d11_stream_analytics_multi_region.processed_events_partitioned_hourly WHERE eventTimestamp BETWEEN TIMESTAMP_SUB(CURRENT_TIMESTAMP(), interval 1 hour) AND CURRENT_TIMESTAMP() GROUP BY eventName, platform ORDER BY count DESC",
    ];
  }

  private generateMockSuggestedQueries(): any[] {
    return [
      {
        queryName: "Top Events by Count",
        description:
          "Get the most frequently occurring events in the last hour",
        query:
          "SELECT eventName, COUNT(*) as count FROM d11_stream_analytics_multi_region.processed_events_partitioned_hourly WHERE eventTimestamp BETWEEN TIMESTAMP_SUB(CURRENT_TIMESTAMP(), interval 1 hour) AND CURRENT_TIMESTAMP() GROUP BY eventName ORDER BY count DESC LIMIT 10",
      },
      {
        queryName: "User Activity by Platform",
        description:
          "Analyze user activity distribution across different platforms",
        query:
          "SELECT platform, COUNT(DISTINCT userId) as unique_users, COUNT(*) as total_events FROM d11_stream_analytics_multi_region.processed_events_partitioned_hourly WHERE eventTimestamp BETWEEN TIMESTAMP_SUB(CURRENT_TIMESTAMP(), interval 24 hour) AND CURRENT_TIMESTAMP() GROUP BY platform ORDER BY unique_users DESC",
      },
      {
        queryName: "Failed Events Analysis",
        description: "Find events that failed and their frequency",
        query:
          "SELECT eventName, COUNT(*) as failure_count FROM d11_stream_analytics_multi_region.processed_events_partitioned_hourly WHERE eventName LIKE '%_failed' AND eventTimestamp BETWEEN TIMESTAMP_SUB(CURRENT_TIMESTAMP(), interval 1 day) AND CURRENT_TIMESTAMP() GROUP BY eventName ORDER BY failure_count DESC",
      },
      {
        queryName: "App Version Distribution",
        description: "See which app versions are most active",
        query:
          "SELECT appVersion, platform, COUNT(*) as event_count FROM d11_stream_analytics_multi_region.processed_events_partitioned_hourly WHERE eventTimestamp BETWEEN TIMESTAMP_SUB(CURRENT_TIMESTAMP(), interval 7 day) AND CURRENT_TIMESTAMP() GROUP BY appVersion, platform ORDER BY event_count DESC LIMIT 20",
      },
      {
        queryName: "Session Analysis",
        description: "Analyze user sessions and their duration",
        query:
          "SELECT sessionId, userId, MIN(eventTimestamp) as session_start, MAX(eventTimestamp) as session_end, COUNT(*) as event_count FROM d11_stream_analytics_multi_region.processed_events_partitioned_hourly WHERE eventTimestamp BETWEEN TIMESTAMP_SUB(CURRENT_TIMESTAMP(), interval 1 day) AND CURRENT_TIMESTAMP() GROUP BY sessionId, userId ORDER BY event_count DESC LIMIT 50",
      },
    ];
  }

  private generateMockTables(): any[] {
    return [
      { name: "d11_stream_analytics_multi_region.interactions" },
      { name: "d11_stream_analytics_multi_region.events" },
    ];
  }

  private generateMockTableColumns(tableName: string): any[] {
    if (tableName?.includes("interactions")) {
      return [
        { name: "interactionId", type: "STRING" },
        { name: "interactionName", type: "STRING" },
        { name: "userId", type: "STRING" },
        { name: "sessionId", type: "STRING" },
        { name: "startTime", type: "TIMESTAMP" },
        { name: "endTime", type: "TIMESTAMP" },
        { name: "duration", type: "INTEGER" },
        { name: "status", type: "STRING" },
        { name: "platform", type: "STRING" },
        { name: "appVersion", type: "STRING" },
        { name: "props", type: "JSON" },
      ];
    }

    if (tableName?.includes("events")) {
      return [
        { name: "eventId", type: "STRING" },
        { name: "eventName", type: "STRING" },
        { name: "eventTimestamp", type: "TIMESTAMP" },
        { name: "userId", type: "STRING" },
        { name: "sessionId", type: "STRING" },
        { name: "platform", type: "STRING" },
        { name: "appVersion", type: "STRING" },
        { name: "props", type: "JSON" },
        { name: "globalProps", type: "JSON" },
      ];
    }

    // Default fallback
    return [
      { name: "id", type: "STRING" },
      { name: "name", type: "STRING" },
      { name: "timestamp", type: "TIMESTAMP" },
    ];
  }

  private generateMockUserDetails(phoneNo: string): any {
    return {
      teamName: `Team${phoneNo.slice(-4)}`,
      userId: Math.floor(Math.random() * 1000000) + 100000,
      emailId: `user${phoneNo}@dream11.com`,
      commEmailId: `comm${phoneNo}@dream11.com`,
    };
  }

  private generateMockUserLastActiveToday(phoneNo: string): any {
    return {
      requestId: `last_active_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
    };
  }

  private handleAnalyticsReportEndpoints(
    pathname: string,
    method: string,
    request: MockRequest,
  ): MockResponse {
    // Get analytics report endpoint
    if (pathname.includes("/analytics-report") && method === "GET") {
      const url = new URL(request.url);
      const reportId = url.searchParams.get("reportId");

      if (this.config.shouldLog()) {
        console.log(
          `[Mock Server] GET_ANALYSIS_REPORT - ReportId: ${reportId}`,
        );
      }

      if (!reportId) {
        return {
          data: null,
          error: {
            code: "MISSING_REPORT_ID",
            message: "Report ID is required",
            cause: "Please provide a valid report ID",
          },
          status: 400,
        };
      }

      // Generate mock analytics report
      const reportData = this.generateMockAnalyticsReport(reportId);

      return {
        data: reportData,
        status: 200,
      };
    }

    // Create analytics report endpoint
    if (pathname.includes("/incident/generateReport") && method === "POST") {
      const requestBody = JSON.parse(request.body || "{}");
      const { startTime, endTime } = requestBody;

      if (this.config.shouldLog()) {
        console.log(
          `[Mock Server] CREATE_ANALYSIS_REPORT - StartTime: ${startTime}, EndTime: ${endTime}`,
        );
      }

      if (!startTime || !endTime) {
        return {
          data: null,
          error: {
            code: "MISSING_TIME_PARAMETERS",
            message: "Start time and end time are required",
            cause: "Please provide both start and end time parameters",
          },
          status: 400,
        };
      }

      // Generate a mock report ID and URL
      const reportId = `report_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
      const analyticsReportUrl = `/analytics-report?reportId=${reportId}`;

      return {
        data: {
          analyticsReportUrl: analyticsReportUrl,
        },
        status: 200,
      };
    }

    return {
      data: { message: "Analytics report endpoint not implemented" },
      status: 200,
    };
  }

  private generateMockAnalyticsReport(reportId: string): any {
    // Set reference time to exactly 5:15 PM today
    const today = new Date();
    const referenceTime = new Date(
      today.getFullYear(),
      today.getMonth(),
      today.getDate(),
      17,
      15,
      0,
      0,
    ).getTime(); // 5:15 PM today

    // Calculate start time (22.5 minutes before reference) and end time (22.5 minutes after reference)
    const startTime = new Date(referenceTime - 22.5 * 60 * 1000).toISOString(); // 22.5 minutes before 5:15 PM
    const endTime = new Date(referenceTime + 22.5 * 60 * 1000).toISOString(); // 22.5 minutes after 5:15 PM
    const now = referenceTime + 22.5 * 60 * 1000; // Use end time as current time for data generation

    const interactions = [
      "contest_join_success",
      "create_team_success",
      "add_cash_success",
      "login_success",
      "withdraw_success",
    ];

    const generateUserCategorizationReadings = (interactionName: string) => {
      const readings = [];
      const baseTime = referenceTime - 22.5 * 60 * 1000; // 22.5 minutes before reference
      const endTime = now;
      const interval = 1 * 60 * 1000; // 1-minute intervals

      let currentTime = baseTime;
      while (currentTime < endTime) {
        const timestamp = Math.floor(currentTime / 1000);
        const totalUsers = 100 + Math.floor(Math.random() * 200);

        let excellent, good, average, poor;

        // Check if this is before or after the reference time
        const isAfterReference = currentTime >= referenceTime;

        if (isAfterReference) {
          // Show severe impact - much worse performance after incident
          poor = Math.floor(totalUsers * (0.25 + Math.random() * 0.15)); // 25-40% poor users
          excellent = Math.floor(totalUsers * (0.05 + Math.random() * 0.1)); // 5-15%
          good = Math.floor(totalUsers * (0.1 + Math.random() * 0.1)); // 10-20%
          average = Math.max(0, totalUsers - excellent - good - poor);
        } else {
          // Baseline before reference - excellent performance with ~8% poor users
          poor = Math.floor(totalUsers * (0.07 + Math.random() * 0.02)); // 7-9% (around 8%)
          excellent = Math.floor(totalUsers * (0.5 + Math.random() * 0.2)); // 50-70%
          good = Math.floor(totalUsers * (0.2 + Math.random() * 0.1)); // 20-30%
          average = Math.max(0, totalUsers - excellent - good - poor);
        }

        readings.push({
          timestamp: timestamp,
          poor: poor,
          average: average,
          good: good,
          excellent: excellent,
          distinctUsers: totalUsers,
        });

        currentTime += interval;
      }

      return { readings };
    };

    const generateErrorReadings = (interactionName: string) => {
      const readings = [];
      const baseTime = referenceTime - 22.5 * 60 * 1000; // 22.5 minutes before reference
      const endTime = now;
      const interval = 1 * 60 * 1000; // 1-minute intervals

      let currentTime = baseTime;
      while (currentTime < endTime) {
        const timestamp = Math.floor(currentTime / 1000);
        const totalUsers = 100 + Math.floor(Math.random() * 200);

        let errorRate;

        // Check if this is before or after the reference time
        const isAfterReference = currentTime >= referenceTime;

        if (isAfterReference) {
          // Dramatically increase error rate after incident
          const timeSinceIncident = (currentTime - referenceTime) / (1000 * 60); // minutes since incident
          const baseErrorRate = 0.15 + timeSinceIncident * 0.05; // 15% + 5% per minute
          errorRate =
            Math.round(
              Math.min(baseErrorRate + Math.random() * 0.2, 0.6) * 100,
            ) / 100; // 15-60% error rate
        } else {
          // Baseline data - low error rates
          errorRate = Math.round(Math.random() * 0.02 * 100) / 100; // 0-2% error rate
        }

        const errorCount = Math.floor(totalUsers * errorRate);

        readings.push({
          errorRate: errorRate.toString(),
          timestamp: timestamp,
          distinctUsers: totalUsers,
          errorCount: errorCount,
        });

        currentTime += interval;
      }

      return { readings };
    };

    const generateInteractions = () => {
      return interactions.map((interactionName) => ({
        interactionName: interactionName,
        description: `Mock description for ${interactionName}`,
        userCategorizationResults:
          generateUserCategorizationReadings(interactionName),
        errorResult: generateErrorReadings(interactionName),
      }));
    };

    return {
      report1: {
        interactions: generateInteractions(),
        startTime: startTime,
        endTime: endTime,
        name: `Report 1 - ${reportId}`,
      },
      report2: {
        interactions: generateInteractions(),
        startTime: startTime,
        endTime: endTime,
        name: `Report 2 - ${reportId}`,
      },
    };
  }

  private handleAnomalyEndpoints(
    pathname: string,
    method: string,
    request: MockRequest,
  ): MockResponse {
    if (this.config.shouldLog()) {
      console.log(
        `[Mock Server] handleAnomalyEndpoints: ${method} ${pathname}`,
      );
    }

    // Apdex anomalies endpoint
    if (pathname.includes("/anomaly/apdex") && method === "GET") {
      const url = new URL(request.url);
      const useCaseId = url.searchParams.get("useCaseId");
      const startTime = url.searchParams.get("startTime");
      const endTime = url.searchParams.get("endTime");

      if (this.config.shouldLog()) {
        console.log(
          `[Mock Server] GET_APDEX_ANOMALIES - UseCaseId: ${useCaseId}, StartTime: ${startTime}, EndTime: ${endTime}`,
        );
      }

      if (!useCaseId || !startTime || !endTime) {
        return {
          data: null,
          error: undefined,
          status: 400,
        };
      }

      const apdexAnomalies = this.generateMockApdexAnomalies(
        useCaseId,
        startTime,
        endTime,
      );
      return {
        data: apdexAnomalies,
        status: 200,
      };
    }

    // Error rate anomalies endpoint
    if (pathname.includes("/anomaly/error-rate") && method === "GET") {
      const url = new URL(request.url);
      const useCaseId = url.searchParams.get("useCaseId");
      const startTime = url.searchParams.get("startTime");
      const endTime = url.searchParams.get("endTime");

      if (this.config.shouldLog()) {
        console.log(
          `[Mock Server] GET_ERROR_RATE_ANOMALIES - UseCaseId: ${useCaseId}, StartTime: ${startTime}, EndTime: ${endTime}`,
        );
      }

      if (!useCaseId || !startTime || !endTime) {
        return {
          data: null,
          error: undefined,
          status: 400,
        };
      }

      const errorRateAnomalies = this.generateMockErrorRateAnomalies(
        useCaseId,
        startTime,
        endTime,
      );
      return {
        data: errorRateAnomalies,
        status: 200,
      };
    }

    // Anomaly details endpoint
    if (pathname.includes("/anomaly/details") && method === "GET") {
      const url = new URL(request.url);
      const useCaseId = url.searchParams.get("useCaseId");
      const timestamp = url.searchParams.get("timestamp");
      const appVersion = url.searchParams.get("appVersion");
      const networkProvider = url.searchParams.get("networkProvider");
      const osVersion = url.searchParams.get("osVersion");
      const platform = url.searchParams.get("platform");
      const state = url.searchParams.get("state");
      const limit = url.searchParams.get("limit") || "10";
      const offSet = url.searchParams.get("offSet") || "0";
      const metric = url.searchParams.get("metric");

      if (this.config.shouldLog()) {
        console.log(
          `[Mock Server] GET_ANOMALY_DETAILS - UseCaseId: ${useCaseId}, Timestamp: ${timestamp}, Metric: ${metric}`,
        );
      }

      if (!useCaseId || !timestamp) {
        return {
          data: null,
          error: undefined,
          status: 400,
        };
      }

      const anomalyDetails = this.generateMockAnomalyDetails(
        useCaseId,
        timestamp,
        {
          appVersion,
          networkProvider,
          osVersion,
          platform,
          state,
          limit: parseInt(limit),
          offSet: parseInt(offSet),
          metric: metric as "APDEX_ANOMALY" | "ERROR_RATE_ANOMALY" | null,
        },
      );
      return {
        data: anomalyDetails,
        status: 200,
      };
    }

    return {
      data: { message: "Anomaly endpoint not implemented" },
      status: 200,
    };
  }

  private handleActivityTrackingEndpoints(
    pathname: string,
    method: string,
    request: MockRequest,
  ): MockResponse {
    if (this.config.shouldLog()) {
      console.log(
        `[Mock Server] handleActivityTrackingEndpoints: ${method} ${pathname}`,
      );
    }

    // User details endpoint
    if (pathname.match(/\/user\/\d+$/) && method === "GET") {
      const phoneMatch = pathname.match(/\/user\/(\d+)$/);
      const phoneNo = phoneMatch ? phoneMatch[1] : null;

      if (this.config.shouldLog()) {
        console.log(`[Mock Server] GET_USER_DETAIL - Phone: ${phoneNo}`);
      }

      if (!phoneNo || phoneNo.length !== 10) {
        return {
          data: null,
          error: {
            code: "INVALID_PHONE_NUMBER",
            message: "Please enter a valid phone number",
            cause: "Phone number must be exactly 10 digits",
          },
          status: 400,
        };
      }

      const userDetails = this.generateMockUserDetails(phoneNo);
      return {
        data: userDetails,
        status: 200,
      };
    }

    // User last active today endpoint
    if (pathname.match(/\/user\/\d+\/active-today$/) && method === "GET") {
      const phoneMatch = pathname.match(/\/user\/(\d+)\/active-today$/);
      const phoneNo = phoneMatch ? phoneMatch[1] : null;

      if (this.config.shouldLog()) {
        console.log(
          `[Mock Server] GET_USER_LAST_ACTIVE_TODAY - Phone: ${phoneNo}`,
        );
      }

      if (!phoneNo || phoneNo.length !== 10) {
        return {
          data: null,
          error: {
            code: "INVALID_PHONE_NUMBER",
            message: "Please enter a valid phone number",
            cause: "Phone number must be exactly 10 digits",
          },
          status: 400,
        };
      }

      const lastActiveData = this.generateMockUserLastActiveToday(phoneNo);
      return {
        data: lastActiveData,
        status: 200,
      };
    }

    // User experiments endpoint
    if (
      pathname.includes("/user/") &&
      pathname.includes("/experiments") &&
      method === "GET"
    ) {
      const phoneMatch = pathname.match(/\/user\/(\d+)\/experiments$/);
      const phoneNo = phoneMatch ? phoneMatch[1] : null;

      if (this.config.shouldLog()) {
        console.log(`[Mock Server] GET_USER_EXPERIMENTS - Phone: ${phoneNo}`);
      }

      if (!phoneNo || phoneNo.length !== 10) {
        return {
          data: null,
          error: {
            code: "INVALID_PHONE_NUMBER",
            message: "Please enter a valid phone number",
            cause: "Phone number must be exactly 10 digits",
          },
          status: 400,
        };
      }

      const experiments =
        this.generateMockUserExperimentsForActivityTracking(phoneNo);
      return {
        data: experiments,
        status: 200,
      };
    }

    // AI Session creation endpoint
    if (pathname.includes("/pulse-ai/session") && method === "POST") {
      if (this.config.shouldLog()) {
        console.log(`[Mock Server] CREATE_USER_AI_SESSION`);
      }

      const sessionId = `ai_session_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;

      return {
        data: {
          session_id: sessionId,
          created_at: new Date().toISOString(),
          expires_at: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(), // 24 hours
        },
        status: 200,
      };
    }

    // AI Insights endpoint
    if (pathname.includes("/pulse-ai/user-query")) {
      if (this.config.shouldLog()) {
        console.log(
          `[Mock Server] AI_INSIGHTS_ENDPOINT - Method: ${method}, Path: ${pathname}`,
        );
      }

      // Handle GET requests (might be preflight or health check)
      if (method === "GET") {
        return {
          data: {
            message: "AI Insights endpoint is available",
            status: "ready",
            supported_methods: ["POST"],
          },
          status: 200,
        };
      }

      // Handle POST requests (actual AI insights)
      if (method === "POST") {
        const requestBody = JSON.parse(request.body || "{}");
        const {
          query,
          "user-id": userId,
          "session-id": sessionId,
        } = requestBody;

        if (this.config.shouldLog()) {
          console.log(
            `[Mock Server] GET_USER_QUERY_PULSE_AI_INSIGHTS - UserId: ${userId}, SessionId: ${sessionId}, Query: ${query?.substring(0, 100)}...`,
          );
        }

        if (!sessionId || !userId || !query) {
          return {
            data: null,
            error: {
              code: "MISSING_REQUIRED_FIELDS",
              message:
                "Missing required fields: session-id, user-id, and query are required",
              cause: "Validation error",
            },
            status: 400,
          };
        }

        const aiResponse = this.generateMockAIInsights(query, userId);
        return {
          data: {
            event: "complete",
            data: {
              text: aiResponse,
              status: "success",
              function_call: null,
              function_response: null,
            },
          },
          status: 200,
        };
      }

      // Handle other methods
      return {
        data: null,
        error: {
          code: "METHOD_NOT_ALLOWED",
          message: `Method ${method} not allowed for this endpoint`,
          cause: "Only GET and POST methods are supported",
        },
        status: 405,
      };
    }

    // Get request ID from time endpoint
    if (pathname.includes("/v2/events/queryRequestId") && method === "GET") {
      const url = new URL(request.url);
      const fromDate = url.searchParams.get("from_date");
      const toDate = url.searchParams.get("to_date");
      const email = url.searchParams.get("email");
      const pattern = url.searchParams.get("pattern");
      const userId = url.searchParams.get("userId");

      if (this.config.shouldLog()) {
        console.log(
          `[Mock Server] GET_REQUEST_ID_BY_TIME - FromDate: ${fromDate}, ToDate: ${toDate}, Email: ${email}, Pattern: ${pattern}, UserId: ${userId}`,
        );
      }

      if (!fromDate || !toDate || !email || !userId) {
        return {
          data: null,
          error: {
            code: "MISSING_REQUIRED_PARAMETERS",
            message:
              "Missing required parameters: from_date, to_date, email, and userId are required",
            cause: "Validation error",
          },
          status: 400,
        };
      }

      const requestId = `events_query_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;

      return {
        data: {
          requestId: requestId,
          status: "PENDING",
          created_at: new Date().toISOString(),
        },
        status: 200,
      };
    }

    // Get events by request ID endpoint
    if (pathname.includes("/v2/events/eventsByRequestId") && method === "GET") {
      const url = new URL(request.url);
      const requestId = url.searchParams.get("requestId");
      const pageToken = url.searchParams.get("pageToken") || "";

      if (this.config.shouldLog()) {
        console.log(
          `[Mock Server] GET_QUERY_RESULT_FROM_ID - RequestId: ${requestId}, PageToken: ${pageToken}`,
        );
      }

      if (!requestId) {
        return {
          data: null,
          error: {
            code: "MISSING_REQUEST_ID",
            message: "Request ID is required",
            cause: "Validation error",
          },
          status: 400,
        };
      }

      const eventsData = this.generateMockUserEventsForActivityTracking(
        requestId,
        pageToken,
      );
      return {
        data: eventsData,
        status: 200,
      };
    }

    // Get event properties endpoint
    if (pathname.includes("/v2/events/eventname") && method === "GET") {
      const url = new URL(request.url);
      const eventName = url.searchParams.get("eventName");
      const eventTimestamp = url.searchParams.get("eventTimestamp");
      const userId = url.searchParams.get("userId");

      if (this.config.shouldLog()) {
        console.log(
          `[Mock Server] GET_EVENT_PROPS - EventName: ${eventName}, EventTimestamp: ${eventTimestamp}, UserId: ${userId}`,
        );
      }

      if (!eventName || !eventTimestamp || !userId) {
        return {
          data: null,
          error: {
            code: "MISSING_REQUIRED_PARAMETERS",
            message:
              "Missing required parameters: eventName, eventTimestamp, and userId are required",
            cause: "Validation error",
          },
          status: 400,
        };
      }

      const eventProps = this.generateMockEventProperties(
        eventName,
        eventTimestamp,
        userId,
      );
      return {
        data: eventProps,
        status: 200,
      };
    }

    // Get screen name to event mapping endpoint
    if (pathname.includes("/v1/events") && method === "GET") {
      const url = new URL(request.url);
      const searchString = url.searchParams.get("search_string");
      const limit = url.searchParams.get("limit") || "10";

      if (this.config.shouldLog()) {
        console.log(
          `[Mock Server] GET_SCREEN_NAME_EVENTS_MAPPING - SearchString: ${searchString}, Limit: ${limit}`,
        );
      }

      const mappingData = this.generateMockScreenNameToEventMapping(
        searchString || "",
        parseInt(limit),
      );
      return {
        data: mappingData,
        status: 200,
      };
    }

    // Cancel query endpoint
    if (pathname.includes("/v2/cancelQueryRequest") && method === "POST") {
      const requestBody = JSON.parse(request.body || "{}");
      const { requestId } = requestBody;

      if (this.config.shouldLog()) {
        console.log(`[Mock Server] CANCEL_QUERY - RequestId: ${requestId}`);
      }

      if (!requestId) {
        return {
          data: null,
          error: {
            code: "MISSING_REQUEST_ID",
            message: "Request ID is required",
            cause: "Validation error",
          },
          status: 400,
        };
      }

      return {
        data: {
          success: true,
          message: "Query cancelled successfully",
          requestId: requestId,
        },
        status: 200,
      };
    }

    return {
      data: { message: "Activity tracking endpoint not implemented" },
      status: 200,
    };
  }

  private generateMockUserExperimentsForActivityTracking(phoneNo: string): any {
    const experimentCount = Math.floor(Math.random() * 5) + 1; // 1-5 experiments
    const experiments = [];

    for (let i = 0; i < experimentCount; i++) {
      experiments.push({
        experiment_id: `exp_${i + 1}_${Math.random().toString(36).substr(2, 6)}`,
        experiment_name: `Test Experiment ${i + 1}`,
        variant: Math.random() > 0.5 ? "control" : "treatment",
        status: "active",
        start_date: new Date(
          Date.now() - Math.random() * 30 * 24 * 60 * 60 * 1000,
        ).toISOString(),
        end_date: new Date(
          Date.now() + Math.random() * 30 * 24 * 60 * 60 * 1000,
        ).toISOString(),
        description: `Mock experiment ${i + 1} for user ${phoneNo}`,
        cohort: {
          cohortList: [`Cohort ${i + 1}A`, `Cohort ${i + 1}B`],
          cohortId: `cohort_${i + 1}_${Math.random().toString(36).substr(2, 4)}`,
        },
      });
    }

    return {
      experiments: experiments,
      total_count: experimentCount,
      user_id: phoneNo,
    };
  }

  private generateMockAIInsights(query: string, userId: string): string {
    const queryLower = query.toLowerCase();

    // Team saving queries
    if (
      queryLower.includes("save") &&
      (queryLower.includes("team") || queryLower.includes("squad"))
    ) {
      const teamsSaved = Math.floor(Math.random() * 5) + 1; // 1-5 teams
      const lastSaveTime = Math.floor(Math.random() * 12) + 1; // 1-12 hours ago
      return `The user with ID ${userId} saved **${teamsSaved} teams** today.

**Methodology:** Analysis based on comprehensive data, focusing on "TeamSavedClient" events to identify successful team saves within the last 24 hours.

**Recent Team Saves:**
- ✅ Team saved: ${lastSaveTime} hours ago (Cricket - IPL)
- ✅ Team saved: ${lastSaveTime + 2} hours ago (Cricket - T20)${teamsSaved > 2 ? "\n- ✅ Team saved: " + (lastSaveTime + 4) + " hours ago (Football - Premier League)" : ""}`;

      // Contest participation queries
    } else if (
      queryLower.includes("contest") ||
      queryLower.includes("join") ||
      queryLower.includes("participate")
    ) {
      const contestsJoined = Math.floor(Math.random() * 8) + 3; // 3-10 contests
      const totalWinnings = Math.floor(Math.random() * 5000) + 500; // ₹500-₹5500
      return `The user with ID ${userId} joined **${contestsJoined} contests** today with total winnings of **₹${totalWinnings}**.

**Methodology:** Analysis based on "ContestJoinSuccess" events and prize distribution data within the last 24 hours.

**Contest Breakdown:**
- 🏏 Cricket contests: ${Math.floor(contestsJoined * 0.7)} contests
- ⚽ Football contests: ${Math.floor(contestsJoined * 0.3)} contests
- 💰 Total entry fees: ₹${contestsJoined * 25}
- 🏆 Win rate: ${Math.floor(Math.random() * 30) + 20}%

**Recent Contest Activity:**
- ✅ Joined "Mega Contest" - ₹50 entry (2 hours ago)
- ✅ Joined "Quick Win" - ₹25 entry (4 hours ago)
- ${contestsJoined > 2 ? '✅ Joined "Daily Special" - ₹100 entry (6 hours ago)' : ""}`;

      // Payment queries
    } else if (
      queryLower.includes("payment") ||
      queryLower.includes("transaction") ||
      queryLower.includes("deposit")
    ) {
      const transactions = Math.floor(Math.random() * 4) + 1; // 1-4 transactions
      const totalAmount = Math.floor(Math.random() * 2000) + 500; // ₹500-₹2500
      return `The user with ID ${userId} made **${transactions} payment transactions** today totaling **₹${totalAmount}**.

**Methodology:** Analysis based on "PaymentSuccess" events and transaction records within the last 24 hours.

**Transaction Details:**
- 💳 Payment method: UPI (${Math.floor(transactions * 0.6)}), Cards (${Math.floor(transactions * 0.4)})
- ⏰ Last transaction: ${Math.floor(Math.random() * 6) + 1} hours ago
- ✅ Success rate: 100% (no failed transactions)
- 💰 Average transaction: ₹${Math.floor(totalAmount / transactions)}

**Recent Payments:**
- ✅ ₹${Math.floor(totalAmount * 0.4)} - Contest entry (${Math.floor(Math.random() * 3) + 1} hours ago)
- ✅ ₹${Math.floor(totalAmount * 0.6)} - Wallet top-up (${Math.floor(Math.random() * 5) + 2} hours ago)`;

      // Login/activity queries
    } else if (
      queryLower.includes("login") ||
      queryLower.includes("active") ||
      queryLower.includes("online")
    ) {
      const loginCount = Math.floor(Math.random() * 5) + 2; // 2-6 logins
      const lastActive = Math.floor(Math.random() * 8) + 1; // 1-8 hours ago
      return `The user with ID ${userId} logged in **${loginCount} times** today and was last active **${lastActive} hours ago**.

**Methodology:** Analysis based on "LoginSuccess" events and session tracking data within the last 24 hours.

**Activity Pattern:**
- 📱 Primary device: Android (${Math.floor(Math.random() * 20) + 70}% of sessions)
- 🌐 Web sessions: ${Math.floor(loginCount * 0.3)} times
- ⏰ Peak activity: ${Math.floor(Math.random() * 4) + 6}-${Math.floor(Math.random() * 4) + 10} PM
- 🔄 Session duration: ${Math.floor(Math.random() * 15) + 5}-${Math.floor(Math.random() * 20) + 20} minutes

**Recent Activity:**
- ✅ Login successful: ${lastActive} hours ago (Android)
- ✅ Login successful: ${lastActive + 2} hours ago (Web)
- ✅ Login successful: ${lastActive + 4} hours ago (Android)`;

      // Error/problem queries
    } else if (
      queryLower.includes("error") ||
      queryLower.includes("issue") ||
      queryLower.includes("problem")
    ) {
      const errorCount = Math.floor(Math.random() * 3); // 0-2 errors
      return `The user with ID ${userId} experienced **${errorCount} errors** today.

**Methodology:** Analysis based on error logs and "ApiError" events within the last 24 hours.

**Error Analysis:**
- 🔴 Total errors: ${errorCount}
- 🟢 Error rate: ${errorCount === 0 ? "0%" : Math.floor(Math.random() * 2) + 1 + "%"}
- ⚠️ Most common: ${errorCount > 0 ? "Network timeout" : "No errors detected"}
- ✅ Resolution: ${errorCount > 0 ? "All errors auto-resolved" : "Clean session"}

${
  errorCount > 0
    ? `**Recent Errors:**
- 🔴 Network timeout: ${Math.floor(Math.random() * 6) + 1} hours ago (Contest join)
- 🔴 API timeout: ${Math.floor(Math.random() * 8) + 2} hours ago (Payment processing)`
    : "**Status:** No issues detected in the last 24 hours"
}`;

      // Default behavior analysis
    } else {
      const sessions = Math.floor(Math.random() * 20) + 10; // 10-29 sessions
      const contests = Math.floor(Math.random() * 15) + 5; // 5-19 contests
      const teams = Math.floor(Math.random() * 8) + 2; // 2-9 teams
      return `## User Behavior Analysis for User ${userId}

**Activity Overview:**
- **Total sessions today:** ${sessions} sessions
- **Contests joined:** ${contests} contests
- **Teams created:** ${teams} teams
- **Last active:** ${Math.floor(Math.random() * 6) + 1} hours ago

**Key Metrics:**
- Contest participation: ${contests} contests joined
- Team creation: ${teams} teams created
- Payment transactions: ${Math.floor(Math.random() * 5) + 1} successful payments
- App usage: ${Math.floor(Math.random() * 3) + 1}.${Math.floor(Math.random() * 9)} hours today

**Behavioral Patterns:**
- User is highly engaged with fantasy sports
- Prefers cricket contests over other sports
- Regular payment user with good transaction history
- Active during peak hours (evenings and weekends)

**Insights:**
- User shows strong engagement and retention
- No significant issues or complaints detected
- Good candidate for premium features or promotions`;
    }
  }

  private generateMockUserEventsForActivityTracking(
    requestId: string,
    pageToken: string,
  ): any {
    const eventsPerPage = 20;
    const totalEvents = 150;
    const currentPage = pageToken ? parseInt(pageToken) : 0;
    const startIndex = currentPage * eventsPerPage;
    const endIndex = Math.min(startIndex + eventsPerPage, totalEvents);

    const events = [];
    const now = Date.now();

    for (let i = startIndex; i < endIndex; i++) {
      const eventTypes = [
        "contest_join_success",
        "team_creation_success",
        "payment_success",
        "login_success",
        "logout_success",
        "app_open",
        "contest_join_failed",
        "payment_failed",
        "api_error",
        "network_timeout",
      ];

      const eventName =
        eventTypes[Math.floor(Math.random() * eventTypes.length)];
      const timestamp = Math.floor(
        (now - (totalEvents - i) * 60 * 1000) / 1000,
      ); // Events spread over time

      events.push({
        f: [{ v: eventName }, { v: timestamp.toString() }],
      });
    }

    const hasMoreData = endIndex < totalEvents;
    const nextPageToken = hasMoreData ? (currentPage + 1).toString() : null;

    return {
      rows: events,
      schema: {
        fields: [
          { name: "event_name", type: "STRING" },
          { name: "event_timestamp", type: "TIMESTAMP" },
        ],
      },
      totalRows: totalEvents,
      pageToken: nextPageToken,
      jobComplete: true,
    };
  }

  private generateMockEventProperties(
    eventName: string,
    eventTimestamp: string,
    userId: string,
  ): any {
    const baseProps = {
      user_id: userId,
      event_timestamp: eventTimestamp,
      platform: "android",
      app_version: "2.1.0",
      device_id: `device_${Math.random().toString(36).substr(2, 10)}`,
      session_id: `session_${Math.random().toString(36).substr(2, 10)}`,
    };

    // Add event-specific properties
    const eventSpecificProps = this.getEventSpecificProperties(eventName);

    return {
      props: {
        ...baseProps,
        ...eventSpecificProps,
      },
      event_name: eventName,
      user_id: userId,
      timestamp: eventTimestamp,
    };
  }

  private getEventSpecificProperties(eventName: string): any {
    const eventLower = eventName.toLowerCase();

    if (eventLower.includes("contest")) {
      return {
        contest_id: `contest_${Math.floor(Math.random() * 1000)}`,
        contest_name: "IPL Match 1",
        entry_fee: Math.floor(Math.random() * 1000) + 100,
        prize_pool: Math.floor(Math.random() * 100000) + 10000,
        participants: Math.floor(Math.random() * 10000) + 1000,
      };
    } else if (eventLower.includes("payment")) {
      return {
        transaction_id: `txn_${Math.random().toString(36).substr(2, 12)}`,
        amount: Math.floor(Math.random() * 5000) + 100,
        payment_method: "upi",
        payment_gateway: "razorpay",
        currency: "INR",
      };
    } else if (eventLower.includes("team")) {
      return {
        team_id: `team_${Math.random().toString(36).substr(2, 8)}`,
        team_name: "My Dream Team",
        captain: "Virat Kohli",
        vice_captain: "MS Dhoni",
        players_count: 11,
      };
    } else if (eventLower.includes("login")) {
      return {
        login_method: "otp",
        ip_address: `192.168.1.${Math.floor(Math.random() * 255)}`,
        user_agent: "Mozilla/5.0 (Android 12; Mobile)",
        login_source: "mobile_app",
      };
    } else {
      return {
        custom_property_1: "value_1",
        custom_property_2: "value_2",
        additional_data: "mock_data",
      };
    }
  }

  private generateMockScreenNameToEventMapping(
    searchString: string,
    limit: number,
  ): any {
    const eventMappings = [
      {
        metadata: {
          eventName: "contest_join_success",
          description: "User successfully joined a contest",
          screenNames: [
            "ContestHomePage",
            "ContestDetailsPage",
            "ContestJoinPage",
          ],
          archived: false,
          isActive: true,
        },
        properties: [
          {
            propertyName: "contest_id",
            description: "Unique identifier for the contest",
            archived: false,
          },
          {
            propertyName: "entry_fee",
            description: "Amount paid to join the contest",
            archived: false,
          },
          {
            propertyName: "prize_pool",
            description: "Total prize money for the contest",
            archived: false,
          },
        ],
      },
      {
        metadata: {
          eventName: "team_creation_success",
          description: "User successfully created a fantasy team",
          screenNames: [
            "CreateTeamPage",
            "TeamSelectionPage",
            "PlayerSelectionPage",
          ],
          archived: false,
          isActive: true,
        },
        properties: [
          {
            propertyName: "team_id",
            description: "Unique identifier for the team",
            archived: false,
          },
          {
            propertyName: "team_name",
            description: "Name given to the team by user",
            archived: false,
          },
          {
            propertyName: "players_count",
            description: "Number of players in the team",
            archived: false,
          },
        ],
      },
      {
        metadata: {
          eventName: "payment_success",
          description: "User successfully completed a payment",
          screenNames: ["PaymentPage", "AddCashPage", "WalletPage"],
          archived: false,
          isActive: true,
        },
        properties: [
          {
            propertyName: "transaction_id",
            description: "Unique identifier for the transaction",
            archived: false,
          },
          {
            propertyName: "amount",
            description: "Amount of the transaction",
            archived: false,
          },
          {
            propertyName: "payment_method",
            description: "Method used for payment",
            archived: false,
          },
        ],
      },
      {
        metadata: {
          eventName: "login_success",
          description: "User successfully logged into the application",
          screenNames: ["LoginPage", "WelcomePage", "HomePage"],
          archived: false,
          isActive: true,
        },
        properties: [
          {
            propertyName: "login_method",
            description: "Method used for login (OTP, password, etc.)",
            archived: false,
          },
          {
            propertyName: "ip_address",
            description: "IP address from which user logged in",
            archived: false,
          },
          {
            propertyName: "device_id",
            description: "Unique identifier for the device",
            archived: false,
          },
        ],
      },
    ];

    // Filter based on search string if provided
    let filteredMappings = eventMappings;
    if (searchString) {
      filteredMappings = eventMappings.filter(
        (mapping) =>
          mapping.metadata.eventName
            .toLowerCase()
            .includes(searchString.toLowerCase()) ||
          mapping.metadata.description
            .toLowerCase()
            .includes(searchString.toLowerCase()),
      );
    }

    // Limit results
    const limitedMappings = filteredMappings.slice(0, limit);

    return {
      eventList: limitedMappings,
      totalCount: filteredMappings.length,
      searchString: searchString || "",
      limit: limit,
    };
  }

  private generateMockApdexAnomalies(
    useCaseId: string,
    startTime: string,
    endTime: string,
  ): any {
    const startTimestamp = new Date(startTime).getTime();
    const endTimestamp = new Date(endTime).getTime();
    const timeRange = endTimestamp - startTimestamp;

    // Generate 3-8 anomalies within the time range
    const anomalyCount = Math.floor(Math.random() * 6) + 3;
    const readings = [];

    for (let i = 0; i < anomalyCount; i++) {
      // Random timestamp within the range
      const randomTime = startTimestamp + Math.random() * timeRange;
      const timestamp = Math.floor(randomTime / 1000); // Convert to seconds

      // Generate anomaly contribution string
      const anomalyContributions = this.generateAnomalyContributions("APDEX");

      readings.push({
        timestamp: timestamp,
        anomalyContributions: anomalyContributions,
        distinctUsers: Math.floor(Math.random() * 500) + 50, // 50-550 users
        reading: Math.round((Math.random() * 0.3 + 0.2) * 100) / 100, // 0.2-0.5 Apdex score
      });
    }

    // Sort by timestamp
    readings.sort((a, b) => a.timestamp - b.timestamp);

    return {
      readings: readings,
    };
  }

  private generateMockErrorRateAnomalies(
    useCaseId: string,
    startTime: string,
    endTime: string,
  ): any {
    const startTimestamp = new Date(startTime).getTime();
    const endTimestamp = new Date(endTime).getTime();
    const timeRange = endTimestamp - startTimestamp;

    // Generate 2-6 anomalies within the time range
    const anomalyCount = Math.floor(Math.random() * 5) + 2;
    const readings = [];

    for (let i = 0; i < anomalyCount; i++) {
      // Random timestamp within the range
      const randomTime = startTimestamp + Math.random() * timeRange;
      const timestamp = Math.floor(randomTime / 1000); // Convert to seconds

      // Generate anomaly contribution string
      const anomalyContributions =
        this.generateAnomalyContributions("ERROR_RATE");

      readings.push({
        timestamp: timestamp,
        anomalyContributions: anomalyContributions,
        distinctUsers: Math.floor(Math.random() * 300) + 30, // 30-330 users
        reading: Math.round((Math.random() * 0.4 + 0.1) * 100) / 100, // 0.1-0.5 error rate
      });
    }

    // Sort by timestamp
    readings.sort((a, b) => a.timestamp - b.timestamp);

    return {
      readings: readings,
    };
  }

  private generateAnomalyContributions(metricType: string): string {
    // Generate application-focused anomaly messages similar to the provided example
    const appVersions = ["5.45.0", "5.44.1", "5.43.2", "5.42.0", "5.41.1"];
    const platforms = [
      "androidplaystore",
      "iosappstore",
      "androidapk",
      "iosbeta",
    ];
    const states = [
      "Bihar",
      "Maharashtra",
      "Karnataka",
      "Tamil Nadu",
      "Uttar Pradesh",
      "West Bengal",
    ];
    const networks = [
      "JIO 4G",
      "Airtel 4G",
      "Vi 4G",
      "BSNL 4G",
      "JIO 5G",
      "Airtel 5G",
    ];
    const osVersions = [
      "VANILLA_ICE_CREAM",
      "TIRAMISU",
      "SNOW_CONE",
      "RED_VELVET_CAKE",
      "OREO",
    ];
    const secondaryOsVersions = [
      "TIRAMISU",
      "SNOW_CONE",
      "RED_VELVET_CAKE",
      "OREO",
      "PIE",
    ];

    // Randomly select values
    const appVersion =
      appVersions[Math.floor(Math.random() * appVersions.length)];
    const platform = platforms[Math.floor(Math.random() * platforms.length)];
    const state = states[Math.floor(Math.random() * states.length)];
    const network = networks[Math.floor(Math.random() * networks.length)];
    const osVersion = osVersions[Math.floor(Math.random() * osVersions.length)];
    const secondaryOs =
      secondaryOsVersions[
        Math.floor(Math.random() * secondaryOsVersions.length)
      ];

    // Generate random percentages
    const statePercentage = Math.floor(Math.random() * 20) + 70; // 70-90%
    const networkPercentage = Math.floor(Math.random() * 20) + 50; // 50-70%
    const osPercentage = Math.floor(Math.random() * 20) + 40; // 40-60%

    if (metricType === "APDEX") {
      return `The anomalies are entirely attributed to app version '${appVersion}' and the '${platform}' platform. Key contributors further include the state of ${state} (over ${statePercentage}%), '${network}' network (${networkPercentage}%), and devices running '${osVersion}' OS (${osPercentage}%), with '${secondaryOs}' as a secondary OS factor.`;
    } else if (metricType === "ERROR_RATE") {
      return `The anomalies are primarily caused by app version '${appVersion}' on the '${platform}' platform. Additional factors include ${state} state users (${statePercentage}%), '${network}' network connectivity (${networkPercentage}%), and '${osVersion}' OS devices (${osPercentage}%), with '${secondaryOs}' contributing as a secondary factor.`;
    }

    return `Anomaly detected in app version '${appVersion}' on '${platform}' platform.`;
  }

  private generateMockAnomalyDetails(
    useCaseId: string,
    timestamp: string,
    filters: {
      appVersion?: string | null;
      networkProvider?: string | null;
      osVersion?: string | null;
      platform?: string | null;
      state?: string | null;
      limit: number;
      offSet: number;
      metric?: "APDEX_ANOMALY" | "ERROR_RATE_ANOMALY" | null;
    },
  ): any {
    const timestampMs = new Date(timestamp).getTime();
    const readings = [];

    // Generate 5-15 detailed anomaly readings
    const readingCount = Math.floor(Math.random() * 11) + 5;

    for (let i = 0; i < readingCount; i++) {
      const appVersions = ["5.45.0", "5.44.1", "5.43.2", "5.42.0", "5.41.1"];
      const platforms = [
        "androidplaystore",
        "iosappstore",
        "androidapk",
        "iosbeta",
      ];
      const states = [
        "Bihar",
        "Maharashtra",
        "Karnataka",
        "Tamil Nadu",
        "Uttar Pradesh",
        "West Bengal",
      ];
      const networks = [
        "JIO 4G",
        "Airtel 4G",
        "Vi 4G",
        "BSNL 4G",
        "JIO 5G",
        "Airtel 5G",
      ];
      const osVersions = [
        "VANILLA_ICE_CREAM",
        "TIRAMISU",
        "SNOW_CONE",
        "RED_VELVET_CAKE",
        "OREO",
      ];

      // Use filter values if provided, otherwise generate random
      const appVersion =
        filters.appVersion ||
        appVersions[Math.floor(Math.random() * appVersions.length)];
      const platform =
        filters.platform ||
        platforms[Math.floor(Math.random() * platforms.length)];
      const state =
        filters.state || states[Math.floor(Math.random() * states.length)];
      const networkProvider =
        filters.networkProvider ||
        networks[Math.floor(Math.random() * networks.length)];
      const osVersion =
        filters.osVersion ||
        osVersions[Math.floor(Math.random() * osVersions.length)];

      // Generate random values within realistic ranges
      const distinctUsers = Math.floor(Math.random() * 200) + 10; // 10-210 users
      const reading = Math.round((Math.random() * 0.4 + 0.1) * 100) / 100; // 0.1-0.5
      const lowerBound =
        Math.round((reading - Math.random() * 0.1) * 100) / 100; // Below reading
      const upperBound =
        Math.round((reading + Math.random() * 0.1) * 100) / 100; // Above reading

      // Determine anomaly type
      const anomalyType =
        filters.metric ||
        (Math.random() > 0.5 ? "APDEX_ANOMALY" : "ERROR_RATE_ANOMALY");

      readings.push({
        timestamp: new Date(timestampMs + i * 60000).toISOString(), // 1 minute intervals
        appVersion: appVersion,
        osVersion: osVersion,
        platform: platform,
        state: state,
        networkProvider: networkProvider,
        distinctUsers: distinctUsers,
        reading: reading,
        lowerBound: Math.max(0, lowerBound),
        upperBound: Math.min(1, upperBound),
        anomalyType: anomalyType,
      });
    }

    // Apply pagination
    const startIndex = filters.offSet;
    const endIndex = startIndex + filters.limit;
    const paginatedReadings = readings.slice(startIndex, endIndex);

    return {
      totalCount: readings.length,
      limit: filters.limit,
      offSet: filters.offSet,
      readings: paginatedReadings,
    };
  }

  private handleSessionReplaysEndpoints(
    pathname: string,
    method: string,
    request: MockRequest,
  ): MockResponse {
    if (method === "GET") {
      const url = new URL(request.url);
      const searchParams = url.searchParams;

      const interactionName = searchParams.get("interactionName") || "";
      const startTime =
        searchParams.get("startTime") ||
        new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();
      const endTime = searchParams.get("endTime") || new Date().toISOString();
      const page = parseInt(searchParams.get("page") || "0", 10);
      const pageSize = parseInt(searchParams.get("pageSize") || "20", 10);
      const eventTypesParam = searchParams.get("eventTypes");
      const eventTypes = eventTypesParam
        ? (eventTypesParam.split(",") as (
            | "crash"
            | "anr"
            | "networkError"
            | "frozenFrame"
          )[])
        : ["crash", "anr", "networkError", "frozenFrame"];
      const device = (searchParams.get("device") || "all") as
        | "all"
        | "ios"
        | "android";

      const { generateMockSessionReplays } = require("./sessionReplays");

      const data = generateMockSessionReplays({
        interactionName,
        startTime,
        endTime,
        page,
        pageSize,
        eventTypes,
        device,
      });

      return {
        data,
        status: 200,
        error: undefined,
      };
    }

    return {
      data: null,
      status: 404,
      error: {
        code: "NOT_FOUND",
        message: `Session replays endpoint not found: ${method} ${pathname}`,
        cause: "Invalid endpoint or method",
      },
    };
  }

  /**
   * Handle Data Query endpoint
   * POST /v1/interactions/performance-metric/distribution
   */
  private handleDataQueryEndpoint(
    pathname: string,
    method: string,
    request: MockRequest,
  ): MockResponse {
    console.log("[MockServer] Data Query endpoint called:", method, pathname);

    if (method === "POST") {
      try {
        if (!request.body) {
          console.log("[MockServer] No request body provided");
          return {
            data: null,
            status: 400,
            error: {
              code: "BAD_REQUEST",
              message: "Request body is required",
              cause: "Missing request body",
            },
          };
        }

        // Parse request body if it's a string
        const requestBody =
          typeof request.body === "string"
            ? JSON.parse(request.body)
            : request.body;

        console.log("[MockServer] Parsed request body:", {
          dataType: requestBody.dataType,
          selectCount: requestBody.select?.length,
          hasGroupBy: !!requestBody.groupBy,
          hasFilters: !!requestBody.filters,
        });

        // Generate mock response based on request body
        const data = generateDataQueryMockResponseV2(requestBody);

        console.log(
          "[MockServer] Generated response with",
          data.rows.length,
          "rows",
        );

        return {
          data,
          status: 200,
          error: undefined,
        };
      } catch (error: any) {
        console.error(
          "[MockServer] Error generating data query response:",
          error,
        );
        return {
          data: null,
          status: 500,
          error: {
            code: "INTERNAL_ERROR",
            message: "Failed to generate data query response",
            cause: error.message || "Unknown error",
          },
        };
      }
    }

    return {
      data: null,
      status: 404,
      error: {
        code: "NOT_FOUND",
        message: `Data query endpoint not found: ${method} ${pathname}`,
        cause: "Invalid endpoint or method",
      },
    };
  }
}
