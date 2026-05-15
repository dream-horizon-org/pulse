import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import path from "path";

export default defineConfig({
  // @vitejs/plugin-react resolves against examples/ecommerce-demo/node_modules/vite;
  // vitest/config expects the hoisted workspace copy — same runtime, duplicate TS types (nmHoistingLimits: workspaces).
  plugins: [react() as never],
  resolve: {
    dedupe: ["react", "react-dom"],
    alias: [
      {
        find: "react",
        replacement: path.resolve(__dirname, "../../node_modules/react"),
      },
      {
        find: "react-dom",
        replacement: path.resolve(__dirname, "../../node_modules/react-dom"),
      },
      {
        find: "react-router-dom",
        replacement: path.resolve(
          __dirname,
          "../../node_modules/react-router-dom",
        ),
      },
      {
        find: "react-router",
        replacement: path.resolve(__dirname, "../../node_modules/react-router"),
      },
      {
        find: "@dreamhorizonorg/pulse-web/react/router",
        replacement: path.resolve(
          __dirname,
          "../../src/integrations/react/router.ts",
        ),
      },
      {
        find: "@dreamhorizonorg/pulse-web/react",
        replacement: path.resolve(
          __dirname,
          "../../src/integrations/react/index.ts",
        ),
      },
      {
        find: "@dreamhorizonorg/pulse-web",
        replacement: path.resolve(__dirname, "../../src/index.ts"),
      },
    ],
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: [],
  },
});
