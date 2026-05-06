"use client";

import { usePathname } from "next/navigation";
import { useEffect } from "react";
import { PulseWeb } from "@dreamhorizon/pulse-web";

const ROUTE_NAMES: Record<string, string> = {
  "/": "Home",
  "/login": "Login",
  "/orders": "Orders",
  "/sdk-lab": "SdkLab",
};

function resolveScreenName(pathname: string): string {
  if (pathname.endsWith("/choose")) return "TicketSelection";
  if (pathname.startsWith("/lottery/")) return "LotteryDetail";
  return ROUTE_NAMES[pathname] ?? pathname;
}

// Next.js App Router equivalent of ecommerce-demo's _PulseWebRouterTracking.
// Follows PostHog's PostHogPageView pattern — null-returning 'use client'
// component mounted once inside <PulseProvider> in layout.tsx.
export function PulsePageView() {
  const pathname = usePathname();

  useEffect(() => {
    PulseWeb.setScreenName(resolveScreenName(pathname));
  }, [pathname]);

  return null;
}
