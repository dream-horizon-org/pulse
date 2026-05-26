import { renderHook, act } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import React from "react";
import { useGetInteractionsOverview } from "./useGetInteractionsOverview";
import { makeRequest } from "../../helpers/makeRequest";

jest.mock("../../helpers/makeRequest");
jest.mock("../../utils", () => ({
  getApiBaseUrl: () => "http://localhost:8080",
}));

const createWrapper = () => {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
};

describe("useGetInteractionsOverview", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("calls makeRequest with POST to correct URL and body regenerate:false when mutate called with empty params", async () => {
    const mockResponse = {
      data: { summary: "All good", cached: true, cachedAt: "2026-05-22T10:00:00Z" },
      error: null,
      status: 200,
    };
    (makeRequest as jest.Mock).mockResolvedValue(mockResponse);

    const { result } = renderHook(() => useGetInteractionsOverview(), {
      wrapper: createWrapper(),
    });

    await act(async () => {
      result.current.mutate({});
    });

    expect(makeRequest).toHaveBeenCalledWith(
      expect.objectContaining({
        url: "http://localhost:8080/v1/ai/interactions/overview",
        init: expect.objectContaining({
          method: "POST",
          body: JSON.stringify({ regenerate: false }),
        }),
      }),
    );
  });

  it("calls makeRequest with body regenerate:true when mutate called with { regenerate: true }", async () => {
    const mockResponse = {
      data: { summary: "Fresh report", cached: false, cachedAt: null },
      error: null,
      status: 200,
    };
    (makeRequest as jest.Mock).mockResolvedValue(mockResponse);

    const { result } = renderHook(() => useGetInteractionsOverview(), {
      wrapper: createWrapper(),
    });

    await act(async () => {
      result.current.mutate({ regenerate: true });
    });

    expect(makeRequest).toHaveBeenCalledWith(
      expect.objectContaining({
        init: expect.objectContaining({
          body: JSON.stringify({ regenerate: true }),
        }),
      }),
    );
  });

  it("returns mutation data with summary, cached, cachedAt on success", async () => {
    const mockData = {
      data: {
        summary: "Performance is healthy across all interactions.",
        cached: true,
        cachedAt: "2026-05-22T08:00:00Z",
      },
      error: null,
      status: 200,
    };
    (makeRequest as jest.Mock).mockResolvedValue(mockData);

    const { result } = renderHook(() => useGetInteractionsOverview(), {
      wrapper: createWrapper(),
    });

    await act(async () => {
      result.current.mutate({});
    });

    expect(result.current.isSuccess).toBe(true);
    expect(result.current.data?.data?.summary).toBe(
      "Performance is healthy across all interactions.",
    );
    expect(result.current.data?.data?.cached).toBe(true);
    expect(result.current.data?.data?.cachedAt).toBe("2026-05-22T08:00:00Z");
  });

  it("sets isError to true when makeRequest rejects", async () => {
    (makeRequest as jest.Mock).mockRejectedValue(new Error("Network error"));

    const { result } = renderHook(() => useGetInteractionsOverview(), {
      wrapper: createWrapper(),
    });

    await act(async () => {
      result.current.mutate({});
    });

    expect(result.current.isError).toBe(true);
  });
});
