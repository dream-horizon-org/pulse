"use client";

import { useEffect, useRef } from "react";
import { useRouter } from "next/router";
import { Pulse } from "../../sdk";
import type { UseNextPagesRouterTrackingOptions } from "../../types/next";

export type { UseNextPagesRouterTrackingOptions } from "../../types/next";

/**
 * Next.js Pages Router integration — listens to `router.events.routeChangeComplete`
 * and calls {@link Pulse.setScreenName} on every client-side navigation.
 *
 * Note: `routeChangeComplete` does NOT fire on the initial page load — the
 * first screen name is set by the SDK's session.start signal (url.path attribute).
 * Unlike App Router, `skipInitial` defaults to `false` here because there is no
 * automatic mount-time event to suppress.
 *
 * Peer dependency: requires `next >=13.0.0` (Pages Router).
 */
export function useNextPagesRouterTracking(
  options: UseNextPagesRouterTrackingOptions = {},
): void {
  const router = useRouter();

  // Stable ref so the closure captures the latest options without re-registering.
  const optionsRef = useRef(options);
  optionsRef.current = options;

  // Tracks whether the first routeChangeComplete has fired — used by skipInitial.
  const isFirstRef = useRef(true);

  useEffect(() => {
    const handleRouteChangeComplete = (url: string): void => {
      const {
        format: fmt,
        includeSearch: incSearch = false,
        skipInitial = false,
      } = optionsRef.current;

      if (skipInitial && isFirstRef.current) {
        isFirstRef.current = false;
        return;
      }
      isFirstRef.current = false;

      const parsed = new URL(url, "http://x");
      const dependency = incSearch ? url : parsed.pathname;
      const name = fmt
        ? fmt({
            pathname: parsed.pathname,
            search: parsed.search.slice(1),
            hash: parsed.hash.slice(1),
          })
        : dependency;
      Pulse.setScreenName(name);
    };

    router.events.on("routeChangeComplete", handleRouteChangeComplete);
    return (): void => {
      router.events.off("routeChangeComplete", handleRouteChangeComplete);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
}
