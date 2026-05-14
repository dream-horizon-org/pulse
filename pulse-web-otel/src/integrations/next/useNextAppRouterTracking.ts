"use client";

import { useEffect, useRef } from "react";
import { usePathname, useSearchParams } from "next/navigation.js";
import { Pulse } from "../../sdk";
import type { UseNextAppRouterTrackingOptions } from "../../types/next";

export type { UseNextAppRouterTrackingOptions } from "../../types/next";

/**
 * Next.js App Router integration — calls {@link Pulse.setScreenName} on
 * every client-side navigation so subsequent signals carry the new screen name.
 *
 * Must be rendered in a Client Component (`"use client"`) inside a
 * `<Suspense>` boundary (required by `useSearchParams`). Use the ready-made
 * {@link PulseRouterEvents} component which wraps this hook.
 *
 * Implementation notes:
 * - `usePathname()` can return `null` during static pre-rendering — we skip
 *   `setScreenName` in that case.
 * - StrictMode-safe: `prevDependency` is a ref so the synthetic
 *   unmount/remount in dev does not double-fire.
 */
export function useNextAppRouterTracking(
  options: UseNextAppRouterTrackingOptions = {},
): void {
  const { format, includeSearch = false, skipInitial = true } = options;
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const prevDependency = useRef<string | null>(null);

  const dependency =
    pathname === null
      ? null
      : includeSearch
        ? pathname + "?" + searchParams.toString()
        : pathname;

  useEffect(() => {
    if (dependency === null) return;

    if (prevDependency.current === null) {
      prevDependency.current = dependency;
      if (skipInitial) return;
    } else if (prevDependency.current === dependency) {
      return;
    } else {
      prevDependency.current = dependency;
    }

    const name = format
      ? format({
          pathname: pathname ?? "",
          search: searchParams.toString(),
          hash: "",
        })
      : dependency;

    Pulse.setScreenName(name);
    Pulse.notifySoftNavigation();
    // Intentionally only [dependency]: format/skipInitial are stable for the hook's
    // lifetime; listing them would re-run every render when callers pass new objects.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dependency]);
}
