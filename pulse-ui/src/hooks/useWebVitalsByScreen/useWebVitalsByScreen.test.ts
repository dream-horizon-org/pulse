import { renderHook } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import React from "react";
import { useWebVitalsByScreen } from "./useWebVitalsByScreen";
import { makeRequest } from "../../helpers/makeRequest";
import { useProjectQueryEnabled } from "../useProjectQueryEnabled";

jest.mock("../../helpers/makeRequest");
jest.mock("../useProjectQueryEnabled");

const createWrapper = () => {
  const queryClient = new QueryClient();
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
};

describe("useWebVitalsByScreen", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (useProjectQueryEnabled as jest.Mock).mockReturnValue(true);
    (makeRequest as jest.Mock).mockResolvedValue({
      data: {
        screens: [
          {
            screenName: "Home",
            p75: 2000,
            totalCount: 500,
            goodPct: 80,
          },
          {
            screenName: "Profile",
            p75: 2500,
            totalCount: 300,
            goodPct: 70,
          },
        ],
      },
      error: null,
      status: 200,
    });
  });

  it("should_call_makeRequest_with_vitalName_param", async () => {
    const startTime = 1000;
    const endTime = 2000;
    const vitalName = "LCP";

    renderHook(() => useWebVitalsByScreen({ startTime, endTime, vitalName }), {
      wrapper: createWrapper(),
    });

    await new Promise((resolve) => setTimeout(resolve, 100));

    expect(makeRequest).toHaveBeenCalledWith(
      expect.objectContaining({
        url: expect.stringContaining("/v1/web-vitals/by-screen"),
        init: expect.objectContaining({
          method: "GET",
        }),
      }),
    );

    const callUrl = (makeRequest as jest.Mock).mock.calls[0][0].url;
    expect(callUrl).toContain("vitalName=LCP");
    expect(callUrl).toContain("startTime=1000");
    expect(callUrl).toContain("endTime=2000");
  });

  it("should_not_have_screenName_param_in_query", async () => {
    const startTime = 1000;
    const endTime = 2000;
    const vitalName = "LCP";

    renderHook(() => useWebVitalsByScreen({ startTime, endTime, vitalName }), {
      wrapper: createWrapper(),
    });

    await new Promise((resolve) => setTimeout(resolve, 100));

    const callUrl = (makeRequest as jest.Mock).mock.calls[0][0].url;
    expect(callUrl).not.toContain("screenName");
  });

  it("should_refetch_when_startTime_or_endTime_change", async () => {
    const { rerender } = renderHook(
      ({ startTime, endTime }: { startTime: number; endTime: number }) =>
        useWebVitalsByScreen({ startTime, endTime, vitalName: "LCP" }),
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

    const { result } = renderHook(
      () =>
        useWebVitalsByScreen({
          startTime: 1000,
          endTime: 2000,
          vitalName: "LCP",
        }),
      { wrapper: createWrapper() },
    );

    expect(result.current.data).toBeUndefined();
    expect(result.current.isLoading).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it("should_return_useQuery_object_with_data_isLoading_error_fields", () => {
    const { result } = renderHook(
      () =>
        useWebVitalsByScreen({
          startTime: 1000,
          endTime: 2000,
          vitalName: "LCP",
        }),
      { wrapper: createWrapper() },
    );

    expect(result.current).toHaveProperty("data");
    expect(result.current).toHaveProperty("isLoading");
    expect(result.current).toHaveProperty("error");
  });
});
