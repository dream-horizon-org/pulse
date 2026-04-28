import { describe, it, expect } from "vitest";
import { Resource } from "@opentelemetry/resources";
import { buildMergedResource, buildResource } from "../resource";
import { PulseDataCollectionConsent } from "../config";

describe("buildMergedResource", () => {
  const base = {
    apiKey: "default-project_devkey01",
    dataCollectionState: PulseDataCollectionConsent.ALLOWED,
    serviceName: "svc-from-config",
    serviceVersion: "9.9.9",
  };

  it("includes user resourceAttributes alongside Pulse defaults", () => {
    const r = buildMergedResource(
      {
        ...base,
        resourceAttributes: {
          "deployment.environment": "staging",
          "service.namespace": "checkout",
        },
      },
      "14.0",
    );
    const attrs = r.attributes;
    expect(attrs["deployment.environment"]).toBe("staging");
    expect(attrs["service.namespace"]).toBe("checkout");
    expect(attrs.platform).toBe("web");
    expect(attrs["rum.sdk.name"]).toBe("pulse_web_js");
  });

  it("Pulse wins on project.id, rum.sdk.name, and platform", () => {
    const r = buildMergedResource(
      {
        ...base,
        resourceAttributes: {
          "project.id": "user-wrong",
          "rum.sdk.name": "fake-sdk",
          platform: "fake-platform",
        },
      },
      "14.0",
    );
    const attrs = r.attributes;
    expect(attrs["project.id"]).toBe("default-project");
    expect(attrs["rum.sdk.name"]).toBe("pulse_web_js");
    expect(attrs.platform).toBe("web");
  });

  it("matches userLayer.merge(pulseLayer) manually", () => {
    const cfg = {
      ...base,
      resourceAttributes: { "custom.key": 42 },
    };
    const merged = buildMergedResource(cfg, "14.0");
    const manual = new Resource(cfg.resourceAttributes ?? {}).merge(
      buildResource(cfg, "14.0"),
    );
    expect(merged.attributes["custom.key"]).toEqual(
      manual.attributes["custom.key"],
    );
    expect(merged.attributes["project.id"]).toEqual(
      manual.attributes["project.id"],
    );
  });
});
