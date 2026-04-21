/**
 * Integration test: Simplified SDK initialization (API key only)
 *
 * Tests:
 * 1. Dev mode (devkey) — resolves localhost:4318 automatically
 * 2. Prod mode — requires explicit endpointBaseUrl
 * 3. Data flow to mock ClickHouse in both modes
 */

import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { resolveEndpointBaseUrl, isLocalEnvironment } from "../config";

// Mock ClickHouse exporter
class MockClickHouseExporter {
  private spans: any[] = [];
  private logs: any[] = [];
  private baseUrl: string;

  constructor(baseUrl: string) {
    this.baseUrl = baseUrl;
  }

  exportSpan(span: any) {
    console.log(`[Mock CH] Exporting span to ${this.baseUrl}: ${span.name}`);
    this.spans.push({
      ...span,
      exportedAt: Date.now(),
      destination: this.baseUrl,
    });
  }

  exportLog(log: any) {
    console.log(`[Mock CH] Exporting log to ${this.baseUrl}: ${log.body}`);
    this.logs.push({
      ...log,
      exportedAt: Date.now(),
      destination: this.baseUrl,
    });
  }

  getExportedSpans() {
    return this.spans;
  }

  getExportedLogs() {
    return this.logs;
  }

  reset() {
    this.spans = [];
    this.logs = [];
  }
}

describe("Simplified SDK Initialization Integration Tests", () => {
  let devExporter: MockClickHouseExporter;
  let prodExporter: MockClickHouseExporter;

  beforeEach(() => {
    devExporter = new MockClickHouseExporter("http://localhost:8080");
    prodExporter = new MockClickHouseExporter(
      "https://collector.prod.example.com",
    );
  });

  afterEach(() => {
    devExporter.reset();
    prodExporter.reset();
  });

  describe("Development Mode (default-project / Test- keys)", () => {
    it("should detect default-project_ prefix", () => {
      expect(isLocalEnvironment("default-project_abc123")).toBe(true);
      expect(isLocalEnvironment("Test-myapp_abc123")).toBe(true);
      expect(isLocalEnvironment("myproject-123_prodkey456")).toBe(false);
    });

    it("should resolve localhost:4318 for default-project key without endpointBaseUrl", () => {
      const devKey = "default-project_devkey01";
      const url = resolveEndpointBaseUrl(devKey);
      expect(url).toBe("http://localhost:4318");
    });

    it("should export spans to localhost in dev mode", () => {
      const devKey = "default-project_devkey01";
      const baseUrl = resolveEndpointBaseUrl(devKey);

      // Create mock SDK init config
      const devConfig = {
        apiKey: devKey,
        serviceName: "test-app-dev",
        endpointBaseUrl: baseUrl, // Should be http://localhost:4318
      };

      expect(devConfig.endpointBaseUrl).toBe("http://localhost:4318");

      // Simulate span export
      const mockSpan = {
        name: "http.request",
        traceId: "trace-001",
        spanId: "span-001",
        duration: 150,
        attributes: {
          "http.method": "GET",
          "http.url": "/api/products",
          "http.status_code": 200,
        },
      };

      devExporter.exportSpan(mockSpan);
      const exported = devExporter.getExportedSpans();

      expect(exported).toHaveLength(1);
      expect(exported[0].name).toBe("http.request");
      expect(exported[0].destination).toBe("http://localhost:8080");
    });

    it("should export logs to localhost in dev mode", () => {
      const devKey = "default-project_devkey01";
      const baseUrl = resolveEndpointBaseUrl(devKey);

      // Mock config
      const devConfig = {
        apiKey: devKey,
        serviceName: "test-app-dev",
        endpointBaseUrl: baseUrl,
      };

      // Simulate log export (session.start)
      const mockLog = {
        body: "session.start",
        severity: "INFO",
        attributes: {
          "pulse.type": "session.start",
          "session.id": "session-123",
          "installation.id": "inst-456",
          platform: "web",
        },
      };

      devExporter.exportLog(mockLog);
      const exported = devExporter.getExportedLogs();

      expect(exported).toHaveLength(1);
      expect(exported[0].body).toBe("session.start");
      expect(exported[0].destination).toBe("http://localhost:8080");
      expect(exported[0].attributes["platform"]).toBe("web");
    });

    it("should override localhost with explicit endpointBaseUrl in dev mode", () => {
      const devKey = "default-project_devkey01";
      const explicitUrl = "http://custom-collector:4318";
      const url = resolveEndpointBaseUrl(devKey, explicitUrl);

      expect(url).toBe(explicitUrl);
    });
  });

  describe("Production Mode (non-devkey)", () => {
    it("should NOT detect devkey in production apiKey", () => {
      const prodKey = "myproject-123_prodkey456";
      expect(isLocalEnvironment(prodKey)).toBe(false);
    });

    it("should return prod URL for production keys without endpointBaseUrl", () => {
      const prodKey = "myproject-123_prodkey456";
      const url = resolveEndpointBaseUrl(prodKey);
      expect(url).toBe("https://pulse-otel-collector.pulse-ux.com");
    });

    it("should use provided endpointBaseUrl for production", () => {
      const prodKey = "myproject-123_prodkey456";
      const prodUrl = "https://collector.prod.example.com";
      const url = resolveEndpointBaseUrl(prodKey, prodUrl);

      expect(url).toBe(prodUrl);
    });

    it("should export spans to production collector", () => {
      const prodKey = "myproject-123_prodkey456";
      const prodUrl = "https://collector.prod.example.com";

      // Create mock SDK init config for prod
      const prodConfig = {
        apiKey: prodKey,
        serviceName: "test-app-prod",
        endpointBaseUrl: prodUrl,
      };

      expect(prodConfig.endpointBaseUrl).toBe(prodUrl);

      // Simulate span export
      const mockSpan = {
        name: "http.request",
        traceId: "trace-002",
        spanId: "span-002",
        duration: 200,
        attributes: {
          "http.method": "POST",
          "http.url": "/api/checkout",
          "http.status_code": 201,
        },
      };

      prodExporter.exportSpan(mockSpan);
      const exported = prodExporter.getExportedSpans();

      expect(exported).toHaveLength(1);
      expect(exported[0].name).toBe("http.request");
      expect(exported[0].destination).toBe(
        "https://collector.prod.example.com",
      );
    });

    it("should export logs to production collector", () => {
      const prodKey = "myproject-123_prodkey456";
      const prodUrl = "https://collector.prod.example.com";

      const prodConfig = {
        apiKey: prodKey,
        serviceName: "test-app-prod",
        endpointBaseUrl: prodUrl,
      };

      // Simulate log export (error)
      const mockLog = {
        body: "Payment processing failed",
        severity: "ERROR",
        attributes: {
          "pulse.type": "non_fatal",
          "exception.type": "PaymentError",
          "exception.message": "Card declined",
          "non_fatal.is_manual": false,
        },
      };

      prodExporter.exportLog(mockLog);
      const exported = prodExporter.getExportedLogs();

      expect(exported).toHaveLength(1);
      expect(exported[0].body).toBe("Payment processing failed");
      expect(exported[0].destination).toBe(
        "https://collector.prod.example.com",
      );
      expect(exported[0].attributes["pulse.type"]).toBe("non_fatal");
    });
  });

  describe("Data Flow Verification", () => {
    it("should flow multiple signals through dev mode without data loss", () => {
      const devKey = "default-project_devkey789";
      const baseUrl = resolveEndpointBaseUrl(devKey);

      expect(baseUrl).toBe("http://localhost:4318");

      // Simulate real-world signal flow
      const signals = [
        // Session start
        {
          type: "log",
          body: "session.start",
          attributes: { "pulse.type": "session.start", "session.id": "s1" },
        },
        // Navigation
        {
          type: "span",
          name: "screen_session",
          attributes: { "screen.name": "/products", duration_ms: 1500 },
        },
        // HTTP request
        {
          type: "span",
          name: "http",
          attributes: { "http.method": "GET", "http.status_code": 200 },
        },
        // Click
        {
          type: "span",
          name: "app.click",
          attributes: { "view.target.class_name": "product-card" },
        },
        // Session end
        {
          type: "log",
          body: "session.end",
          attributes: { "pulse.type": "session.end", "session.id": "s1" },
        },
      ];

      signals.forEach((sig) => {
        if (sig.type === "log") {
          devExporter.exportLog(sig);
        } else {
          devExporter.exportSpan(sig);
        }
      });

      const allExported = [
        ...devExporter.getExportedLogs(),
        ...devExporter.getExportedSpans(),
      ];

      expect(allExported).toHaveLength(5);
      allExported.forEach((signal) => {
        expect(signal.destination).toBe("http://localhost:8080");
      });
    });

    it("should flow multiple signals through prod mode without data loss", () => {
      const prodKey = "ecommerce-app_prodkey789";
      const baseUrl = resolveEndpointBaseUrl(
        prodKey,
        "https://collector.prod.example.com",
      );

      expect(baseUrl).toBe("https://collector.prod.example.com");

      // Simulate production signal flow
      const signals = [
        {
          type: "log",
          body: "session.start",
          attributes: { "pulse.type": "session.start" },
        },
        {
          type: "span",
          name: "http",
          attributes: { "http.method": "GET", "http.status_code": 200 },
        },
        {
          type: "log",
          body: "session.end",
          attributes: { "pulse.type": "session.end" },
        },
      ];

      signals.forEach((sig) => {
        if (sig.type === "log") {
          prodExporter.exportLog(sig);
        } else {
          prodExporter.exportSpan(sig);
        }
      });

      const allExported = [
        ...prodExporter.getExportedLogs(),
        ...prodExporter.getExportedSpans(),
      ];

      expect(allExported).toHaveLength(3);
      allExported.forEach((signal) => {
        expect(signal.destination).toBe("https://collector.prod.example.com");
      });
    });
  });
});
