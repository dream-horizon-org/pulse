/**
 * Mock Server Usage Examples
 *
 * Examples showing how to use the mock server in development
 */

import { MockServer } from "../MockServer";
import { makeRequest } from "../../helpers/makeRequest";

// Example 1: Enable mock server programmatically
export const enableMockServer = () => {
  const mockServer = MockServer.getInstance();
  mockServer.enable();
};

// Example 2: Disable mock server programmatically
export const disableMockServer = () => {
  const mockServer = MockServer.getInstance();
  mockServer.disable();
  console.log("Mock server disabled");
};

// Example 3: Configure mock server
export const configureMockServer = () => {
  const mockServer = MockServer.getInstance();
  mockServer.updateConfig({
    delay: 1000,
    errorRate: 0.2,
    enableLogging: true,
  });
  console.log("Mock server configured");
};

// Example 4: Using with existing API calls
export const testApiCall = async () => {
  try {
    const response = await makeRequest({
      url: "http://localhost:8080/user/9876543210",
      init: {
        method: "GET",
      },
    });

    console.log("API Response:", response);
    return response;
  } catch (error) {
    console.error("API Error:", error);
    throw error;
  }
};

// Example 5: Testing different scenarios
export const testScenarios = async () => {
  const mockServer = MockServer.getInstance();

  // Test with error simulation
  mockServer.updateConfig({ errorRate: 1.0 }); // 100% error rate

  try {
    await testApiCall();
  } catch (error) {
    console.log("Expected error:", error);
  }

  // Test with normal operation
  mockServer.updateConfig({ errorRate: 0.0 }); // 0% error rate
  const response = await testApiCall();
  console.log("Success response:", response);
};
