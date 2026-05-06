"use client";

import { useQuery } from "@tanstack/react-query";
import { api } from "../lib/api";
import type { Order } from "../types/lottery";

interface OrdersResponse {
  items: Order[];
  total: number;
}

export function useOrders() {
  return useQuery<OrdersResponse>({
    queryKey: ["orders"],
    queryFn: () => api.get<OrdersResponse>("/api/orders"),
    staleTime: 60_000,
  });
}
