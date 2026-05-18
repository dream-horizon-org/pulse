/** Unit tests for Next.js PulseRouterEvents component. */
import React from "react";
import { describe, it, expect, vi } from "vitest";
import { render } from "@testing-library/react";

// ─── Mocks ────────────────────────────────────────────────────────────────────

vi.mock("next/navigation.js", () => ({
  usePathname: vi.fn(() => "/"),
  useSearchParams: vi.fn(() => new URLSearchParams()),
}));

vi.mock("../sdk", () => ({
  Pulse: { setScreenName: vi.fn() },
}));

const mockUseNextAppRouterTracking = vi.fn();
vi.mock("../integrations/next/useNextAppRouterTracking", () => ({
  useNextAppRouterTracking: (opts: unknown) =>
    mockUseNextAppRouterTracking(opts),
}));

import { PulseRouterEvents } from "../integrations/next/PulseRouterEvents";
import { PulseWebLogger } from "../pulse-web-logger";

// ─── Tests ────────────────────────────────────────────────────────────────────

describe("PulseRouterEvents (Next.js)", () => {
  it("renders null — nothing in the DOM", () => {
    const { container } = render(<PulseRouterEvents />);
    expect(container.firstChild).toBeNull();
  });

  it("forwards options to useNextAppRouterTracking", () => {
    const format = vi.fn(() => "custom");
    render(
      <PulseRouterEvents
        skipInitial={false}
        includeSearch={true}
        format={format}
      />,
    );
    expect(mockUseNextAppRouterTracking).toHaveBeenCalledWith(
      expect.objectContaining({
        skipInitial: false,
        includeSearch: true,
        format,
      }),
    );
  });

  it("wraps in Suspense — does not throw during render", () => {
    expect(() => render(<PulseRouterEvents />)).not.toThrow();
  });

  it("error boundary catches render failures from useNextAppRouterTracking", () => {
    const spy = vi.spyOn(PulseWebLogger, "alwaysError");
    mockUseNextAppRouterTracking.mockImplementation(() => {
      throw new Error("simulated tracking render failure");
    });
    expect(() => render(<PulseRouterEvents />)).not.toThrow();
    expect(spy).toHaveBeenCalled();
    const msg = String(spy.mock.calls[0]?.[0] ?? "");
    expect(msg).toContain("[pulse:router]");
  });
});
