import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import path from "path";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: [
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
