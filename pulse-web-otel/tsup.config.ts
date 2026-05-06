import { defineConfig } from "tsup";

export default defineConfig([
  // Browser / React / Next.js runtime bundles
  {
    entry: {
      index: "src/index.ts",
      react: "src/integrations/react/index.ts",
      next: "src/integrations/next/index.ts",
    },
    format: ["esm", "cjs"],
    dts: true,
    clean: true,
    sourcemap: true,
    define: {
      __SDK_VERSION__: JSON.stringify(
        process.env["npm_package_version"] ?? "0.0.0",
      ),
    },
    external: ["react", "react-dom", "react-router-dom", "next"],
  },
  // Node.js build-time bundle (Next.js config wrapper + source map upload)
  {
    entry: {
      "next-config": "src/integrations/next-config/index.ts",
    },
    format: ["esm", "cjs"],
    dts: true,
    platform: "node",
    target: "node18",
    // Don't bundle anything that could be large — let the user's build resolve it
    external: ["next", "webpack"],
  },
]);
