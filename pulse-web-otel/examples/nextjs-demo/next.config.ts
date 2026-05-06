import path from "path";
import type { NextConfig } from "next";

const SDK_ROOT = path.resolve(__dirname, "../..");
const DIST = path.join(SDK_ROOT, "dist");

const nextConfig: NextConfig = {
  webpack(config) {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const alias = (config.resolve.alias as Record<string, any>) ?? {};
    // Use $ for exact match to prevent prefix-matching @dreamhorizon/pulse-web/next → index.cjs/next
    alias["@dreamhorizon/pulse-web$"] = path.join(DIST, "index.cjs");
    alias["@dreamhorizon/pulse-web/react"] = path.join(DIST, "react.cjs");
    alias["@dreamhorizon/pulse-web/next"] = path.join(DIST, "next.cjs");
    config.resolve.alias = alias;
    return config;
  },
};

export default nextConfig;
