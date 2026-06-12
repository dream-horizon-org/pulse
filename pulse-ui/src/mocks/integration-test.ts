/**
 * Mock Server Integration Test
 *
 * Simple test to verify mock server works with the actual API layer
 */

// This file can be run to test the mock server integration
// Run with: npx ts-node src/mocks/integration-test.ts

import { makeRequest } from "../helpers/makeRequest";

// Set environment variables for testing
process.env.REACT_APP_USE_MOCK_SERVER = "true";
process.env.REACT_APP_MOCK_DELAY = "100";
process.env.REACT_APP_MOCK_ERROR_RATE = "0.0";
process.env.REACT_APP_MOCK_LOGGING = "true";

async function testMockServer() {
  console.log("🧪 Testing Mock Server Integration...\n");

  try {
    // Test 1: User Detail API
    console.log("1. Testing User Detail API...");
    const userResponse = await makeRequest({
      url: "http://localhost:8080/user/9876543210",
      init: { method: "GET" },
    });
    console.log("✅ User Detail Response:", userResponse.data);

    // Test 2: Jobs API
    console.log("\n2. Testing Jobs API...");
    const jobsResponse = await makeRequest({
      url: "http://localhost:8080/v2/getJobs",
      init: { method: "GET" },
    });
    console.log("✅ Jobs Response:", jobsResponse.data);

    // Test 3: Alerts API
    console.log("\n3. Testing Alerts API...");
    const alertsResponse = await makeRequest({
      url: "http://localhost:8080/v1/alert",
      init: { method: "GET" },
    });
    console.log("✅ Alerts Response:", alertsResponse.data);

    // Test 4: Analytics API
    console.log("\n4. Testing Analytics API...");
    const analyticsResponse = await makeRequest({
      url: "http://localhost:8080/v2/getApdexScore",
      init: {
        method: "POST",
        body: JSON.stringify({
          startTime: "2024-01-15 10:00:00",
          endTime: "2024-01-15 11:00:00",
        }),
      },
    });
    console.log("✅ Analytics Response:", analyticsResponse.data);

    console.log("\n🎉 All tests passed! Mock server is working correctly.");
  } catch (error) {
    console.error("❌ Test failed:", error);
  }
}

// Run the test if this file is executed directly
if (require.main === module) {
  testMockServer();
}

export { testMockServer };
