/**
 * Unit tests for PulseNavigationEvents component.
 */
import React from "react";
import { describe, it, expect, vi } from "vitest";
import { render } from "@testing-library/react";

// ─── Mocks ────────────────────────────────────────────────────────────────────

vi.mock("next/navigation", () => ({
  usePathname: vi.fn(() => "/"),
  useSearchParams: vi.fn(() => new URLSearchParams()),
}));

vi.mock("../sdk", () => ({
  PulseWeb: { setScreenName: vi.fn() },
}));

const mockUseNextAppRouterTracking = vi.fn();
vi.mock("../integrations/next/useNextAppRouterTracking", () => ({
  useNextAppRouterTracking: (opts: unknown) =>
    mockUseNextAppRouterTracking(opts),
}));

import { PulseNavigationEvents } from "../integrations/next/PulseNavigationEvents";

// ─── Tests ────────────────────────────────────────────────────────────────────

describe("PulseNavigationEvents", () => {
  it("renders null — nothing in the DOM", () => {
    const { container } = render(<PulseNavigationEvents />);
    expect(container.firstChild).toBeNull();
  });

  it("forwards options to useNextAppRouterTracking", () => {
    const format = vi.fn(() => "custom");
    render(
      <PulseNavigationEvents
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
    expect(() => render(<PulseNavigationEvents />)).not.toThrow();
  });
});
