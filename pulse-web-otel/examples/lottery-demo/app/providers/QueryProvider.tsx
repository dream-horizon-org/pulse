"use client";

import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

const client = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

export function QueryProvider({ children }: { children: React.ReactNode }) {
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}
