/**
 * Mock Server
 *
 * Main mock server class that intercepts API calls and returns mock responses
 */

import { MockResponse, MockRequest } from "./types";
import { MockResponseGenerator } from "./MockResponseGenerator";
import { MockConfigManager } from "./MockConfig";
import { MakeRequestConfig } from "../helpers/makeRequest";

export class MockServer {
  private static instance: MockServer;
  private responseGenerator: MockResponseGenerator;
  private config: MockConfigManager;

  private constructor() {
    this.responseGenerator = new MockResponseGenerator();
    this.config = MockConfigManager.getInstance();
  }

  static getInstance(): MockServer {
    if (!MockServer.instance) {
      MockServer.instance = new MockServer();
    }
    return MockServer.instance;
  }

  /**
   * Check if mock server is enabled
   */
  isEnabled(): boolean {
    return this.config.isEnabled();
  }

  /**
   * Handle API request and return mock response
   */
  async handleRequest(requestConfig: MakeRequestConfig): Promise<Response> {
    if (!this.isEnabled()) {
      throw new Error("Mock server is not enabled");
    }

    const mockRequest: MockRequest = {
      url: requestConfig.url.toString(),
      method: requestConfig.init?.method || "GET",
      body: requestConfig.init?.body?.toString(),
      headers: requestConfig.init?.headers as Record<string, string>,
    };

    const mockResponse =
      await this.responseGenerator.generateResponse(mockRequest);

    // Convert mock response to fetch Response object
    return this.createFetchResponse(mockResponse, requestConfig.unwrapped);
  }

  /**
   * Convert mock response to fetch Response object
   */
  private createFetchResponse(mockResponse: MockResponse, unwrapped?: boolean): Response {
    // For unwrapped endpoints, return the data directly without { data, error } wrapper
    const responseBody = unwrapped
      ? JSON.stringify(mockResponse.data)
      : JSON.stringify({
          data: mockResponse.data,
          error: mockResponse.error,
        });

    const responseInit: ResponseInit = {
      status: mockResponse.status,
      statusText: this.getStatusText(mockResponse.status),
      headers: {
        "Content-Type": "application/json",
      },
    };

    return new Response(responseBody, responseInit);
  }

  /**
   * Get status text for HTTP status code
   */
  private getStatusText(status: number): string {
    const statusTexts: Record<number, string> = {
      200: "OK",
      201: "Created",
      400: "Bad Request",
      401: "Unauthorized",
      403: "Forbidden",
      404: "Not Found",
      500: "Internal Server Error",
    };

    return statusTexts[status] || "Unknown";
  }

  /**
   * Enable mock server
   */
  enable(): void {
    this.config.updateConfig({ enabled: true });
  }

  /**
   * Disable mock server
   */
  disable(): void {
    this.config.updateConfig({ enabled: false });
  }

  /**
   * Update mock server configuration
   */
  updateConfig(config: Partial<import("./types").MockConfig>): void {
    this.config.updateConfig(config);
  }

  /**
   * Get current configuration
   */
  getConfig() {
    return this.config.getConfig();
  }

  /**
   * Reset mock data store
   */
  reset(): void {
    // Reset data store if needed
    // Add reset method to MockDataStore if needed
  }
}
