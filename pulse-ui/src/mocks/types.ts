/**
 * Mock Server Types
 *
 * Type definitions for the mock server system
 */

export interface MockResponse {
  data: any;
  status: number;
  delay?: number;
  error?: {
    code: string;
    message: string;
    cause: string;
  };
}

export interface MockConfig {
  enabled: boolean;
  delay: number;
  errorRate: number;
  enableLogging: boolean;
}

export interface MockRequest {
  url: string;
  method: string;
  body?: string;
  headers?: Record<string, string>;
}

export interface MockDataStore {
  users: any[];
  jobs: any[];
  alerts: any[];
  analytics: any[];
  queries: any[];
  events: any[];
}

export type MockResponseHandler = (
  request: MockRequest,
) => Promise<MockResponse>;

export interface MockEndpoint {
  pattern: string;
  method: string;
  handler: MockResponseHandler;
}

export interface MockScenario {
  name: string;
  description: string;
  responses: Map<string, MockResponse>;
}
