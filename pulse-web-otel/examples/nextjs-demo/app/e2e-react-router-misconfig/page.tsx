"use client";

import type { ReactElement } from "react";
import dynamic from "next/dynamic";

const MisconfigProbe = dynamic(() => import("./misconfig-client"), {
  ssr: false,
});

/**
 * E2E-only route: client-only bundle so `react-router-dom` hooks never run during SSR.
 */
export default function E2eReactRouterMisconfigPage(): ReactElement {
  return <MisconfigProbe />;
}
