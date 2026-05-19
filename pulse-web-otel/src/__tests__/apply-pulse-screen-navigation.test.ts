import { describe, it, expect, vi, afterEach } from "vitest";
import { resolvePulseScreenName } from "../integrations/react/apply-pulse-screen-navigation";

const embeddedWrapperPath =
  "/projects/default-project/screens/%2Fprojects%2Fdefault-project%2Finteraction-details%2FUI%2520Session%2520Replay%2520Open";
const expectedEmbedded =
  "/projects/default-project/interaction-details/UI Session Replay Open";

describe("resolvePulseScreenName", () => {
  afterEach(() => vi.restoreAllMocks());

  it("normalizes pathname when format is omitted", () => {
    expect(
      resolvePulseScreenName(undefined, embeddedWrapperPath, {
        pathname: embeddedWrapperPath,
        search: "",
        hash: "",
      }),
    ).toBe(expectedEmbedded);
  });

  it("prefers location.pathname over dependency when format is omitted", () => {
    expect(
      resolvePulseScreenName(undefined, "/stale", {
        pathname:
          "/projects/default-project/interaction-details/UI%20Onboarding%20Success%20to%20Dashboard",
        search: "",
        hash: "",
      }),
    ).toBe(
      "/projects/default-project/interaction-details/UI Onboarding Success to Dashboard",
    );
  });

  it("uses custom format when provided", () => {
    expect(
      resolvePulseScreenName(() => "Custom Screen", "/products", {
        pathname: "/products",
        search: "",
        hash: "",
      }),
    ).toBe("Custom Screen");
  });

  it("returns null when format throws", () => {
    expect(
      resolvePulseScreenName(
        () => {
          throw new Error("format boom");
        },
        "/products",
        { pathname: "/products", search: "", hash: "" },
      ),
    ).toBeNull();
  });
});
