import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import {
  decodeUriComponentFully,
  normalizeScreenPathname,
  unwrapEmbeddedScreenPath,
} from "../utils/screen-pathname";
import {
  PulseGlobalAttributesProcessor,
  resolveScreenNameFromUrl,
} from "../processors/global-attrs-processor";
import { resolvePulseScreenName } from "../integrations/react/apply-pulse-screen-navigation";
import type { PulseWebConfig } from "../config";
import { _resetInstallationStateForTesting, SessionProvider } from "../session";
import { PulseWebSemconv } from "../semconv";

const embeddedWrapperPath =
  "/projects/default-project/screens/%2Fprojects%2Fdefault-project%2Finteraction-details%2FUI%2520Session%2520Replay%2520Open";
const expectedEmbedded =
  "/projects/default-project/interaction-details/UI Session Replay Open";

function makeProcessor(
  config: Partial<PulseWebConfig> = {},
): PulseGlobalAttributesProcessor {
  const session = {
    getSessionId: vi.fn().mockReturnValue("s1"),
    getWindowId: vi.fn().mockReturnValue("w1"),
    updateActivity: vi.fn(),
  } as unknown as SessionProvider;

  return new PulseGlobalAttributesProcessor(
    session,
    { apiKey: "test-key", ...config } as PulseWebConfig,
    "",
  );
}

function setPath(path: string) {
  Object.defineProperty(window, "location", {
    value: {
      ...window.location,
      pathname: path,
      href: `http://localhost${path}`,
    },
    configurable: true,
    writable: true,
  });
}

describe("decodeUriComponentFully", () => {
  it("decodes double-encoded spaces", () => {
    expect(decodeUriComponentFully("UI%2520Session")).toBe("UI Session");
  });

  it("decodes single-encoded spaces", () => {
    expect(decodeUriComponentFully("UI%20Session")).toBe("UI Session");
  });

  it("treats + as space", () => {
    expect(decodeUriComponentFully("UI+Session")).toBe("UI Session");
  });

  it("returns input when already decoded", () => {
    expect(decodeUriComponentFully("/products")).toBe("/products");
  });

  it("returns partial string on malformed percent sequences", () => {
    expect(decodeUriComponentFully("bad%")).toBe("bad%");
  });

  it("returns empty string for empty input", () => {
    expect(decodeUriComponentFully("")).toBe("");
  });
});

describe("unwrapEmbeddedScreenPath", () => {
  it("unwraps /screens/ embedded analytics path", () => {
    expect(unwrapEmbeddedScreenPath(embeddedWrapperPath)).toBe(
      expectedEmbedded,
    );
  });

  it("returns pathname when /screens/ tail is not an absolute path", () => {
    expect(unwrapEmbeddedScreenPath("/demo/screens/home")).toBe(
      "/demo/screens/home",
    );
  });

  it("returns pathname when no /screens/ segment", () => {
    expect(unwrapEmbeddedScreenPath("/products")).toBe("/products");
  });
});

describe("normalizeScreenPathname", () => {
  it("unwraps then decodes embedded paths", () => {
    expect(normalizeScreenPathname(embeddedWrapperPath)).toBe(expectedEmbedded);
  });

  it("decodes percent-encoded segments on direct routes", () => {
    expect(
      normalizeScreenPathname(
        "/projects/default-project/interaction-details/UI%20Onboarding%20Success%20to%20Dashboard",
      ),
    ).toBe(
      "/projects/default-project/interaction-details/UI Onboarding Success to Dashboard",
    );
  });

  it("leaves ordinary paths unchanged", () => {
    expect(normalizeScreenPathname("/products")).toBe("/products");
  });

  it("normalizes root to /", () => {
    expect(normalizeScreenPathname("/")).toBe("/");
  });
});

describe("resolveScreenNameFromUrl", () => {
  beforeEach(() => {
    _resetInstallationStateForTesting();
    window.localStorage.clear();
  });

  afterEach(() => vi.restoreAllMocks());

  it("unwraps embedded /screens/ paths for History navigation", () => {
    setPath(embeddedWrapperPath);
    expect(resolveScreenNameFromUrl({ apiKey: "k" } as PulseWebConfig)).toBe(
      expectedEmbedded,
    );
  });

  it("still applies routePatterns on decoded pathname", () => {
    setPath("/products/123");
    expect(
      resolveScreenNameFromUrl({
        apiKey: "k",
        routePatterns: [{ pattern: "^/products/", name: "Product Detail" }],
      } as PulseWebConfig),
    ).toBe("Product Detail");
  });

  it("still normalizes numeric segments after decode", () => {
    setPath("/products/123");
    expect(resolveScreenNameFromUrl({ apiKey: "k" } as PulseWebConfig)).toBe(
      "/products/:id",
    );
  });
});

describe("resolveScreenName — encoded pathnames (processor)", () => {
  beforeEach(() => {
    _resetInstallationStateForTesting();
    window.localStorage.clear();
  });

  afterEach(() => vi.restoreAllMocks());

  it("unwraps /screens/ wrapper via getCurrentScreenName", () => {
    setPath(embeddedWrapperPath);
    const proc = makeProcessor();
    expect(proc.getCurrentScreenName()).toBe(expectedEmbedded);
  });

  it("decodes percent-encoded segments on interaction-details routes", () => {
    setPath(
      "/projects/default-project/interaction-details/UI%20Onboarding%20Success%20to%20Dashboard",
    );
    const proc = makeProcessor();
    expect(proc.getCurrentScreenName()).toBe(
      "/projects/default-project/interaction-details/UI Onboarding Success to Dashboard",
    );
  });

  it("stamps decoded screen.name on metrics attrs", () => {
    setPath(embeddedWrapperPath);
    const proc = makeProcessor();
    const attrs = proc.getCommonAttrsForMetrics();
    expect(attrs["screen.name"]).toBe(expectedEmbedded);
  });

  it("stamps last.screen.name with previous decoded screen after navigation", () => {
    setPath("/products");
    const proc = makeProcessor();
    proc.getCommonAttrsForMetrics();

    setPath(embeddedWrapperPath);
    const attrs = proc.getCommonAttrsForMetrics();

    expect(attrs[PulseWebSemconv.AttributeKey.LAST_SCREEN_NAME]).toBe(
      "/products",
    );
    expect(attrs["screen.name"]).toBe(expectedEmbedded);
  });
});

describe("resolvePulseScreenName integration", () => {
  it("matches processor for embedded wrapper path", () => {
    const fromRouter = resolvePulseScreenName(undefined, embeddedWrapperPath, {
      pathname: embeddedWrapperPath,
      search: "",
      hash: "",
    });
    setPath(embeddedWrapperPath);
    const fromProcessor = makeProcessor().getCurrentScreenName();
    expect(fromRouter).toBe(fromProcessor);
    expect(fromRouter).toBe(expectedEmbedded);
  });
});
