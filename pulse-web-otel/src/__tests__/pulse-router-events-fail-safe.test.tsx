/** PulseRouterEvents must not crash the host when Router context or format() fails. */
vi.mock("@opentelemetry/api-logs", () => ({
  logs: {
    getLogger: vi.fn().mockReturnValue({ emit: vi.fn() }),
    setGlobalLoggerProvider: vi.fn(),
  },
}));

import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import React from "react";
import { render, act } from "@testing-library/react";

vi.mock("../exporters", () => {
  const mockTracerProvider = {
    addSpanProcessor: vi.fn(),
    getTracer: vi.fn().mockReturnValue({
      startSpan: vi.fn().mockReturnValue({
        setAttribute: vi.fn(),
        end: vi.fn(),
      }),
    }),
    forceFlush: vi.fn().mockResolvedValue(undefined),
    shutdown: vi.fn().mockResolvedValue(undefined),
    register: vi.fn(),
  };
  const mockLoggerProvider = {
    addLogRecordProcessor: vi.fn(),
    getLogger: vi.fn().mockReturnValue({ emit: vi.fn() }),
    forceFlush: vi.fn().mockResolvedValue(undefined),
    shutdown: vi.fn().mockResolvedValue(undefined),
  };
  return {
    createProviders: vi.fn().mockReturnValue({
      tracerProvider: mockTracerProvider,
      loggerProvider: mockLoggerProvider,
      meterProvider: {
        addMetricReader: vi.fn(),
        getMeter: vi.fn().mockReturnValue({}),
        forceFlush: vi.fn().mockResolvedValue(undefined),
        shutdown: vi.fn().mockResolvedValue(undefined),
      },
      cleanup: vi.fn(),
    }),
  };
});

import {
  PulseIntegrationErrorBoundary,
  PulseRouterEvents,
} from "../integrations/react/router";
import { PulseWebLogger } from "../pulse-web-logger";
import { Pulse } from "../sdk";

beforeEach(() => {
  window.localStorage.clear();
  window.sessionStorage.clear();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("PulseRouterEvents fail-safe (React Router)", () => {
  it("does not throw when mounted outside any Router; logs alwaysError", async () => {
    const alwaysError = vi.spyOn(PulseWebLogger, "alwaysError");
    await act(async () => {
      expect(() =>
        render(
          <div data-testid="host-root">
            <PulseRouterEvents skipInitial={false} />
          </div>,
        ),
      ).not.toThrow();
    });
    expect(alwaysError).toHaveBeenCalled();
    const msg = String(alwaysError.mock.calls[0]?.[0] ?? "");
    expect(msg).toContain("[pulse:router]");
  });

  it("exports PulseIntegrationErrorBoundary from /react/router", () => {
    expect(PulseIntegrationErrorBoundary).toBeDefined();
  });
});

describe("format() errors (PulseRouterEvents + MemoryRouter)", () => {
  it("logs alwaysError and does not call setScreenName when format throws", async () => {
    const alwaysError = vi.spyOn(PulseWebLogger, "alwaysError");
    const setScreenName = vi.spyOn(Pulse, "setScreenName");

    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const { MemoryRouter, Routes, Route } = require("react-router-dom") as {
      MemoryRouter: React.ComponentType<{
        initialEntries?: string[];
        children?: React.ReactNode;
      }>;
      Routes: React.ComponentType<{ children?: React.ReactNode }>;
      Route: React.ComponentType<{
        path?: string;
        element?: React.ReactNode;
      }>;
    };

    render(
      <MemoryRouter initialEntries={["/a"]}>
        <PulseRouterEvents
          skipInitial={false}
          format={() => {
            throw new Error("format boom");
          }}
        />
        <Routes>
          <Route path="/a" element={<div>a</div>} />
        </Routes>
      </MemoryRouter>,
    );

    await act(async () => {
      await Promise.resolve();
    });

    expect(alwaysError).toHaveBeenCalled();
    expect(String(alwaysError.mock.calls[0]?.[0] ?? "")).toContain("format()");
    expect(setScreenName).not.toHaveBeenCalled();
  });
});
