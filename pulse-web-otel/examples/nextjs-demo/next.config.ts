import path from "path";
import type { NextConfig } from "next";

const SDK_ROOT = path.resolve(__dirname, "../..");
const DIST = path.join(SDK_ROOT, "dist");

const nextConfig: NextConfig = {
  webpack(config) {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const alias = (config.resolve.alias as Record<string, any>) ?? {};
    // Use $ for exact match to prevent prefix-matching @dreamhorizonorg/pulse-web/next → index.cjs/next
    alias["@dreamhorizonorg/pulse-web$"] = path.join(DIST, "index.cjs");
    // `react/router` before `react` — same prefix rule as Vite (avoid …/react.cjs/router)
    alias["@dreamhorizonorg/pulse-web/react/router"] = path.join(
      DIST,
      "react-router.cjs",
    );
    alias["@dreamhorizonorg/pulse-web/react"] = path.join(DIST, "react.cjs");
    alias["@dreamhorizonorg/pulse-web/next"] = path.join(DIST, "next.cjs");
    config.resolve.alias = alias;
    return config;
  },
};

export default nextConfig;
