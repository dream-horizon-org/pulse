import { defineConfig } from "vite";
import path from "node:path";

export default defineConfig({
  appType: "spa",
  server: { port: 3003 },
  resolve: {
    alias: {
      "@dreamhorizonorg/pulse-web": path.resolve(__dirname, "../../src/index.ts"),
    },
  },
  optimizeDeps: {
    include: ["@dreamhorizonorg/pulse-web"],
  },
});
