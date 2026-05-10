import { renderHook } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import React from "react";
import { useWebVitalsSummary } from "./useWebVitalsSummary";
import { makeRequest } from "../../helpers/makeRequest";
import { useProjectQueryEnabled } from "../useProjectQueryEnabled";

jest.mock("../../helpers/makeRequest");
jest.mock("../useProjectQueryEnabled");

const createWrapper = () => {
  const queryClient = new QueryClient();
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
};

describe("useWebVitalsSummary", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (useProjectQueryEnabled as jest.Mock).mockReturnValue(true);
    (makeRequest as jest.Mock).mockResolvedValue({
      vitals: [
        {
          name: "LCP",
          p75: 2000,
          goodPct: 80,
          needsImprovementPct: 15,
          poorPct: 5,
          totalCount: 1000,
        },
      ],
    });
  });

  it("should_call_makeRequest_with_GET_web_vitals_summary_endpoint", async () => {
    const startTime = 1000;
    const endTime = 2000;

    const { result } = renderHook(() =>
      useWebVitalsSummary({ startTime, endTime }),
      { wrapper: createWrapper() },
    );

    expect(result.current.isLoading).toBe(true);
    await new Promise((resolve) => setTimeout(resolve, 100));

    expect(makeRequest).toHaveBeenCalledWith(
      expect.objectContaining({
        url: expect.stringContaining("/v1/web-vitals/summary"),
        init: expect.objectContaining({
          method: "GET",
        }),
      }),
    );
  });

  it("should_pass_screenName_to_query_when_provided", async () => {
    const startTime = 1000;
    const endTime = 2000;
    const screenName = "Home";

    renderHook(() => useWebVitalsSummary({ startTime, endTime, screenName }), {
      wrapper: createWrapper(),
    });

    await new Promise((resolve) => setTimeout(resolve, 100));

    expect(makeRequest).toHaveBeenCalledWith(
      expect.objectContaining({
        url: expect.stringContaining("screenName=Home"),
      }),
    );
  });

  it("should_omit_screenName_from_query_when_undefined", async () => {
    const startTime = 1000;
    const endTime = 2000;

    renderHook(() => useWebVitalsSummary({ startTime, endTime }), {
      wrapper: createWrapper(),
    });

    await new Promise((resolve) => setTimeout(resolve, 100));

    const callUrl = (makeRequest as jest.Mock).mock.calls[0][0].url;
    expect(callUrl).not.toContain("screenName");
  });

  it("should_refetch_when_startTime_or_endTime_change", async () => {
    const { rerender } = renderHook(
      ({ startTime, endTime }: { startTime: number; endTime: number }) =>
        useWebVitalsSummary({ startTime, endTime }),
      {
        initialProps: { startTime: 1000, endTime: 2000 },
        wrapper: createWrapper(),
      },
    );

    await new Promise((resolve) => setTimeout(resolve, 100));
    expect(makeRequest).toHaveBeenCalledTimes(1);

    rerender({ startTime: 1500, endTime: 2500 });

    await new Promise((resolve) => setTimeout(resolve, 100));
    expect(makeRequest).toHaveBeenCalledTimes(2);
  });

  it("should_disable_query_when_project_unset", () => {
    (useProjectQueryEnabled as jest.Mock).mockReturnValue(false);

    const { result } = renderHook(() =>
      useWebVitalsSummary({ startTime: 1000, endTime: 2000 }),
      { wrapper: createWrapper() },
    );

    expect(result.current.data).toBeUndefined();
    expect(result.current.isLoading).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it("should_return_useQuery_object_with_data_isLoading_error_fields", () => {
    const { result } = renderHook(() =>
      useWebVitalsSummary({ startTime: 1000, endTime: 2000 }),
      { wrapper: createWrapper() },
    );

    expect(result.current).toHaveProperty("data");
    expect(result.current).toHaveProperty("isLoading");
    expect(result.current).toHaveProperty("error");
  });
});
