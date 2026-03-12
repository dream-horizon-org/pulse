import { convertEventToFlameChartNode } from "../eventConverter";
import type { UnifiedEvent } from "../unifiedEvents";
import { mockSessionDataWithTechnical } from "../../../../__mock__/SessionReplayDetail.mock";

describe("eventConverter", () => {
  const baseSessionData = {
    ...mockSessionDataWithTechnical,
    startTime: "2025-03-08T14:00:00.000Z",
    networkRequests: [
      {
        timestamp: 5000,
        method: "GET",
        url: "https://api.example.com/users",
        status: 200,
        duration: 120,
      },
    ],
    criticalInteractions: [
      {
        interactionId: 1,
        interactionName: "tap_checkout",
        displayName: "Checkout",
        status: "success" as const,
        timestamp: 3000,
        latency: 80,
        apdexScore: 1,
      },
    ],
    consoleLogs: [
      {
        timestamp: 7000,
        level: "error" as const,
        message: "Something failed",
        stackTrace: "Error: at foo()",
      },
    ],
  };

  it("converts a basic event to FlameChartNode with correct ids and type", () => {
    const event: UnifiedEvent = {
      timestamp: 1000,
      type: "session_start",
      description: "Session Started",
      color: "#6b7280",
    };
    const result = convertEventToFlameChartNode(event, baseSessionData);
    expect(result.id).toBe("raw_event_1000_session_start");
    expect(result.traceId).toBe("trace_1000");
    expect(result.spanId).toBe("span_1000");
    expect(result.name).toBe("Session Started");
    expect(result.start).toBe(1000);
    expect(result.duration).toBe(0);
    expect(result.type).toBe("log");
    expect(result.color).toBe("#6b7280");
    expect(result.metadata).toBeDefined();
    expect(result.metadata!.description).toBe("Session Started");
    expect(result.metadata!.eventType).toBe("session_start");
  });

  it("maps event types to FlameChartNode types correctly", () => {
    const types: Array<{ type: UnifiedEvent["type"]; expected: "span" | "log" }> = [
      { type: "screen_load", expected: "span" },
      { type: "critical_interaction", expected: "span" },
      { type: "api_call", expected: "span" },
      { type: "console_log", expected: "log" },
      { type: "network_performance", expected: "log" },
    ];
    types.forEach(({ type, expected }) => {
      const event: UnifiedEvent = {
        timestamp: 2000,
        type,
        description: "Test",
        color: "#000",
      };
      const result = convertEventToFlameChartNode(event, baseSessionData);
      expect(result.type).toBe(expected);
    });
  });

  it("enriches metadata from matching network request", () => {
    const event: UnifiedEvent = {
      timestamp: 5000,
      type: "api_call",
      description: "GET https://api.example.com/users",
      color: "#10b981",
    };
    const result = convertEventToFlameChartNode(event, baseSessionData);
    expect(result.duration).toBe(120);
    expect(result.metadata).toBeDefined();
    expect(result.metadata!.method).toBe("GET");
    expect(result.metadata!.url).toBe("https://api.example.com/users");
    expect(result.metadata!.status).toBe(200);
    expect(result.metadata!.duration).toBe(120);
  });

  it("enriches metadata from matching critical interaction", () => {
    const event: UnifiedEvent = {
      timestamp: 3000,
      type: "critical_interaction",
      description: "Checkout",
      color: "#8b5cf6",
    };
    const result = convertEventToFlameChartNode(event, baseSessionData);
    expect(result.duration).toBe(80);
    expect(result.metadata).toBeDefined();
    expect(result.metadata!.interactionId).toBe(1);
    expect(result.metadata!.displayName).toBe("Checkout");
    expect(result.metadata!.status).toBe("success");
    expect(result.metadata!.latency).toBe(80);
    expect(result.metadata!.apdexScore).toBe(1);
  });

  it("enriches metadata from matching console log", () => {
    const event: UnifiedEvent = {
      timestamp: 7000,
      type: "console_log",
      description: "Log message",
      color: "#ef4444",
    };
    const result = convertEventToFlameChartNode(event, baseSessionData);
    expect(result.metadata).toBeDefined();
    expect(result.metadata!.level).toBe("error");
    expect(result.metadata!.message).toBe("Something failed");
    expect(result.metadata!.stackTrace).toBe("Error: at foo()");
  });

  it("uses session startTime in metadata timestamp", () => {
    const event: UnifiedEvent = {
      timestamp: 100,
      type: "session_start",
      description: "Session Started",
      color: "#6b7280",
    };
    const result = convertEventToFlameChartNode(event, baseSessionData);
    const expectedTs = new Date(baseSessionData.startTime).getTime() + 100;
    expect(result.metadata).toBeDefined();
    expect(result.metadata!.timestamp).toBe(expectedTs);
  });
});
