/**
 * Events & Data Mock Responses
 */

export const mockEventResponses = {
  getEvents: {
    data: [
      {
        eventName: "login_start",
        screenName: "LoginScreen",
        properties: ["user_id", "timestamp", "device_type"],
      },
      {
        eventName: "login_complete",
        screenName: "LoginScreen",
        properties: ["user_id", "timestamp", "success"],
      },
    ],
    status: 200,
  },
  getWhitelistedEvents: {
    data: [
      "login_start",
      "login_complete",
      "checkout_start",
      "checkout_complete",
    ],
    status: 200,
  },
  whitelistEvents: {
    data: { success: true },
    status: 200,
  },
  getRequestIdByTime: {
    data: {
      requestIds: ["req_123", "req_456", "req_789"],
    },
    status: 200,
  },
  getEventsByRequestId: {
    data: [
      {
        eventName: "login_start",
        timestamp: "2024-01-15T10:00:00Z",
        properties: { user_id: "123" },
      },
    ],
    status: 200,
  },
};
