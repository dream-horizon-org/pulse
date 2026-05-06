"use client";

import { useQuery } from "@tanstack/react-query";
import { api } from "../lib/api";
import type { Lottery } from "../types/lottery";

interface LotteriesResponse {
  items: Lottery[];
  total: number;
}

export function useLotteries(scenario?: string) {
  const url = scenario
    ? `/api/lotteries?scenario=${scenario}`
    : "/api/lotteries";

  return useQuery<LotteriesResponse>({
    queryKey: ["lotteries", scenario],
    queryFn: () => api.get<LotteriesResponse>(url),
    staleTime: 30_000,
  });
}
