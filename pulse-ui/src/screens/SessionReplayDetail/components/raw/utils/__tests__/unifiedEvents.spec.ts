import { createUnifiedEvents } from "../unifiedEvents";
import type { SessionDetailData } from "../../../../../../services/sessionReplay/mockSessionDetail";
import { mockSessionDataWithTechnical } from "../../../../__mock__/SessionReplayDetail.mock";
import { EVENT_DESCRIPTIONS, EVENT_TYPES } from "../../../../constants/strings";

describe("unifiedEvents", () => {
  it("always includes session start as first event", () => {
    const sessionData: SessionDetailData = {
      ...mockSessionDataWithTechnical,
      events: [],
      criticalInteractions: [],
      networkRequests: [],
    };
    const result = createUnifiedEvents(sessionData);
    expect(result.length).toBeGreaterThanOrEqual(1);
    expect(result[0].timestamp).toBe(0);
    expect(result[0].type).toBe(EVENT_TYPES.SESSION_START);
    expect(result[0].description).toBe(EVENT_DESCRIPTIONS.SESSION_STARTED);
  });

  it("adds app lifecycle event when first event has timestamp > 0", () => {
    const sessionData: SessionDetailData = {
      ...mockSessionDataWithTechnical,
      events: [
        { timestamp: 1000, type: "click", description: "Tap", details: {} },
      ],
      criticalInteractions: [],
      networkRequests: [],
    };
    const result = createUnifiedEvents(sessionData);
    const appLifecycle = result.find(
      (e) => e.type === EVENT_TYPES.APP_LIFECYCLE,
    );
    expect(appLifecycle).toBeDefined();
    expect(appLifecycle?.description).toBe(
      EVENT_DESCRIPTIONS.APP_LIFECYCLE_INIT,
    );
  });

  it("converts click events to interaction_tap with formatted description", () => {
    const sessionData: SessionDetailData = {
      ...mockSessionDataWithTechnical,
      events: [
        {
          timestamp: 500,
          type: "click",
          description: "Tap on Submit",
          details: {},
        },
      ],
      criticalInteractions: [],
      networkRequests: [],
    };
    const result = createUnifiedEvents(sessionData);
    const tap = result.find((e) => e.type === EVENT_TYPES.INTERACTION_TAP);
    expect(tap).toBeDefined();
    expect(tap?.timestamp).toBe(500);
    expect(tap?.description).toContain(
      EVENT_DESCRIPTIONS.INTERACTION_TAP_PREFIX,
    );
  });

  it("converts navigation events to screen_load", () => {
    const sessionData: SessionDetailData = {
      ...mockSessionDataWithTechnical,
      events: [
        {
          timestamp: 2000,
          type: "navigation",
          description: "Navigate to /HOME",
          details: {},
        },
      ],
      criticalInteractions: [],
      networkRequests: [],
    };
    const result = createUnifiedEvents(sessionData);
    const screenLoad = result.find((e) => e.type === EVENT_TYPES.SCREEN_LOAD);
    expect(screenLoad).toBeDefined();
    expect(screenLoad?.description).toContain(
      EVENT_DESCRIPTIONS.SCREEN_LOAD_PREFIX,
    );
  });

  it("adds critical interactions with timestamp", () => {
    const sessionData: SessionDetailData = {
      ...mockSessionDataWithTechnical,
      events: [],
      criticalInteractions: [
        {
          interactionId: 1,
          interactionName: "checkout",
          displayName: "Checkout",
          status: "success",
          timestamp: 3000,
        },
      ],
      networkRequests: [],
    };
    const result = createUnifiedEvents(sessionData);
    const critical = result.find(
      (e) => e.type === EVENT_TYPES.CRITICAL_INTERACTION,
    );
    expect(critical).toBeDefined();
    expect(critical?.timestamp).toBe(3000);
    expect(critical?.description).toContain("Checkout");
  });

  it("adds network requests as api_call events", () => {
    const sessionData: SessionDetailData = {
      ...mockSessionDataWithTechnical,
      events: [],
      criticalInteractions: [],
      networkRequests: [
        {
          timestamp: 4000,
          method: "POST",
          url: "https://api.example.com/order",
          status: 200,
          duration: 150,
        },
      ],
    };
    const result = createUnifiedEvents(sessionData);
    const apiCall = result.find(
      (e) =>
        e.type === EVENT_TYPES.API_CALL &&
        e.description.includes("POST") &&
        e.description.includes("order"),
    );
    expect(apiCall).toBeDefined();
    expect(apiCall?.timestamp).toBe(4000);
  });

  it("sorts events by timestamp", () => {
    const sessionData: SessionDetailData = {
      ...mockSessionDataWithTechnical,
      events: [
        { timestamp: 500, type: "click", description: "Tap", details: {} },
        { timestamp: 200, type: "navigation", description: "Nav", details: {} },
      ],
      criticalInteractions: [],
      networkRequests: [],
    };
    const result = createUnifiedEvents(sessionData);
    for (let i = 1; i < result.length; i++) {
      expect(result[i].timestamp).toBeGreaterThanOrEqual(
        result[i - 1].timestamp,
      );
    }
  });
});
