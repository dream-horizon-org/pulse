import { defineConfig } from "vite";
import path from "node:path";

export default defineConfig({
  appType: "spa",
  server: { port: 3003 },
  resolve: {
    alias: {
      "@dreamhorizon/pulse-web": path.resolve(__dirname, "../../src/index.ts"),
    },
  },
  optimizeDeps: {
    include: ["@dreamhorizon/pulse-web"],
  },
});
