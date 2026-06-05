/**
 * Tests for useFunnelDropoff hook.
 * Verifies enable-gating, query-key composition, and service delegation.
 */
import { renderHook, waitFor } from "@testing-library/react";
import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useFunnelDropoff } from "../useFunnelDropoff";

jest.mock("../../../services/funnels.service", () => ({
  fetchFunnelDropoff: jest.fn(),
}));
jest.mock("../../useProjectQueryEnabled", () => ({
  useProjectQueryEnabled: (base: boolean) => base,
}));

import { fetchFunnelDropoff } from "../../../services/funnels.service";

const mockedFetch = fetchFunnelDropoff as jest.Mock;

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);
}

describe("useFunnelDropoff", () => {
  beforeEach(() => {
    mockedFetch.mockReset();
  });

  test("stays disabled when funnelId is missing", async () => {
    const { result } = renderHook(() => useFunnelDropoff(undefined, 0), {
      wrapper: wrapper(),
    });
    expect(result.current.fetchStatus).toBe("idle");
    expect(mockedFetch).not.toHaveBeenCalled();
  });

  test("stays disabled when stepIndex is missing", async () => {
    const { result } = renderHook(() => useFunnelDropoff("f-1", undefined), {
      wrapper: wrapper(),
    });
    expect(result.current.fetchStatus).toBe("idle");
    expect(mockedFetch).not.toHaveBeenCalled();
  });

  test("fetches when both funnelId and stepIndex are provided", async () => {
    mockedFetch.mockResolvedValue({
      data: {
        funnelId: 1,
        stepIndex: 2,
        stepName: "Checkout",
        mode: "UNIQUE_USERS",
        dropoffCohort: 10,
        converterCohort: 20,
        causes: [],
      },
      error: null,
    });
    const { result } = renderHook(
      () => useFunnelDropoff("f-1", 2, "2026-04-23"),
      { wrapper: wrapper() },
    );
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockedFetch).toHaveBeenCalledWith("f-1", 2, "2026-04-23");
    expect(result.current.data?.data?.stepName).toBe("Checkout");
  });
});
