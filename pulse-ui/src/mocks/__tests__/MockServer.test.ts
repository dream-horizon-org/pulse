/**
 * Mock Server Tests
 *
 * Basic tests to verify mock server functionality
 */

import { MockServer } from "../MockServer";
import { MockConfigManager } from "../MockConfig";

// Mock environment variables
const originalEnv = process.env;

beforeEach(() => {
  jest.resetModules();
  process.env = { ...originalEnv };
});

afterAll(() => {
  process.env = originalEnv;
});

describe("MockServer", () => {
  test("should be disabled by default", () => {
    process.env.REACT_APP_USE_MOCK_SERVER = "false";
    const mockServer = MockServer.getInstance();
    expect(mockServer.isEnabled()).toBe(false);
  });

  test("should be enabled when environment variable is set", () => {
    process.env.REACT_APP_USE_MOCK_SERVER = "true";
    const mockServer = MockServer.getInstance();
    expect(mockServer.isEnabled()).toBe(true);
  });

  test("should handle user detail request", async () => {
    process.env.REACT_APP_USE_MOCK_SERVER = "true";
    const mockServer = MockServer.getInstance();

    const requestConfig = {
      url: "http://localhost:8080/user/9876543210",
      init: {
        method: "GET",
      },
    };

    const response = await mockServer.handleRequest(requestConfig);
    const data = await response.json();

    expect(response.status).toBe(200);
    expect(data.data).toHaveProperty("teamName");
    expect(data.data).toHaveProperty("userId");
    expect(data.data).toHaveProperty("emailId");
  });

  test("should handle job list request", async () => {
    process.env.REACT_APP_USE_MOCK_SERVER = "true";
    const mockServer = MockServer.getInstance();

    const requestConfig = {
      url: "http://localhost:8080/v2/getJobs",
      init: {
        method: "GET",
      },
    };

    const response = await mockServer.handleRequest(requestConfig);
    const data = await response.json();

    expect(response.status).toBe(200);
    expect(data.data).toHaveProperty("jobs");
    expect(data.data).toHaveProperty("totalJobs");
  });
});

describe("MockConfigManager", () => {
  test("should load configuration from environment variables", () => {
    process.env.REACT_APP_USE_MOCK_SERVER = "true";
    process.env.REACT_APP_MOCK_DELAY = "1000";
    process.env.REACT_APP_MOCK_ERROR_RATE = "0.2";
    process.env.REACT_APP_MOCK_LOGGING = "true";

    const configManager = MockConfigManager.getInstance();
    const config = configManager.getConfig();

    expect(config.enabled).toBe(true);
    expect(config.delay).toBe(1000);
    expect(config.errorRate).toBe(0.2);
    expect(config.enableLogging).toBe(true);
  });
});
