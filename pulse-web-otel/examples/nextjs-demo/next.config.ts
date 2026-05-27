import path from "path";
import type { NextConfig } from "next";

const SDK_ROOT = path.resolve(__dirname, "../..");
const DIST = path.join(SDK_ROOT, "dist");

const nextConfig: NextConfig = {
  webpack(config) {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const alias = (config.resolve.alias as Record<string, any>) ?? {};
    // Use ESM (.js) bundles so that webpack resolves both entries through the
    // shared chunk (chunk-NY4WMXE4.js) — CJS bundles inline a separate Pulse
    // class per entry, which splits the singleton and breaks manual API calls.
    alias["@dreamhorizonorg/pulse-web$"] = path.join(DIST, "index.js");
    // `react/router` before `react` — same prefix rule as Vite (avoid …/react.js/router)
    alias["@dreamhorizonorg/pulse-web/react/router"] = path.join(
      DIST,
      "react-router.js",
    );
    alias["@dreamhorizonorg/pulse-web/react"] = path.join(DIST, "react.js");
    alias["@dreamhorizonorg/pulse-web/next"] = path.join(DIST, "next.js");
    config.resolve.alias = alias;
    return config;
  },
};

export default nextConfig;
