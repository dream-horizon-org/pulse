"use client";

import { useRouterTracking } from "./useRouterTracking";
import type { PulseRouterEventsProps } from "../../types/react";

export type { PulseRouterEventsProps } from "../../types/react";

/**
 * React Router v6 — calls the SDK `setScreenName` on route changes.
 * Mount once inside a component under `<BrowserRouter>` / `<MemoryRouter>`,
 * typically next to your `<Routes>`.
 *
 * ```tsx
 * function AppRoutes() {
 *   return (
 *     <>
 *       <PulseRouterEvents />
 *       <Routes>...</Routes>
 *     </>
 *   );
 * }
 * ```
 *
 * **Peer dependency:** requires `react-router-dom >=6.0.0`.
 */
export function PulseRouterEvents(props: PulseRouterEventsProps): null {
  useRouterTracking(props);
  return null;
}
