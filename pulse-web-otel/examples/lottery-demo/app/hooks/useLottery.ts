"use client";

import { useQuery } from "@tanstack/react-query";
import { api } from "../lib/api";
import type { Lottery } from "../types/lottery";

export function useLottery(id: string) {
  return useQuery<Lottery>({
    queryKey: ["lottery", id],
    queryFn: () => api.get<Lottery>(`/api/lottery/${id}`),
    staleTime: 30_000,
    retry: false, // don't retry 404/410 — they're intentional negative cases
  });
}
