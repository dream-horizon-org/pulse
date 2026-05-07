import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // No basePath — keeps routes simple for a demo app
  // No output:'export' — we need API route handlers for real fetch/network spans
};

export default nextConfig;
