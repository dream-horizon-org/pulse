import {
  extractCacheMeta,
  extractInteractionReport,
} from "../extractInteractionReport";
import { mockInteractionReportV1 } from "../../../screens/CriticalInteractionDetails/components/InteractionReport/__mocks__/interactionReport.fixture";

describe("extractInteractionReport", () => {
  it("parses nested report key", () => {
    const parsed = extractInteractionReport({
      report: mockInteractionReportV1,
      cached: true,
    });
    expect(parsed?.identity.name).toBe("PaymentGatewayHandshakeLatency");
  });

  it("parses flat InteractionReportV1 body", () => {
    const parsed = extractInteractionReport(mockInteractionReportV1);
    expect(parsed?.verdict.rating).toBe("amber");
  });

  it("returns null for invalid payload", () => {
    expect(extractInteractionReport(null)).toBeNull();
    expect(extractInteractionReport({ foo: 1 })).toBeNull();
  });
});

describe("extractCacheMeta", () => {
  it("reads cached flag and cachedAt", () => {
    const meta = extractCacheMeta({
      cached: true,
      cachedAt: "2026-05-24T10:00:00Z",
    });
    expect(meta.cached).toBe(true);
    expect(meta.cachedAt).toBe("2026-05-24T10:00:00Z");
  });
});
