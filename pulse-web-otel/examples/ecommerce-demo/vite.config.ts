import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "path";

export default defineConfig({
  plugins: [react()],
  server: { port: 3002 },
  resolve: {
    // Point directly at SDK source for HMR — changes to SDK src hot-reload in demo
    alias: [
      {
        find: "@dreamhorizon/pulse-web/react",
        replacement: path.resolve(
          __dirname,
          "../../src/integrations/react/index.ts",
        ),
      },
      {
        find: "@dreamhorizon/pulse-web",
        replacement: path.resolve(__dirname, "../../src/index.ts"),
      },
    ],
  },
  optimizeDeps: {
    // Pre-bundle the SDK so Vite doesn't re-analyse it on every request
    include: ["react", "react-dom", "react-router-dom"],
  },
});
