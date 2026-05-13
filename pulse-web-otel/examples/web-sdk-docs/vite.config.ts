import { defineConfig } from "vite";
import path from "node:path";

export default defineConfig({
  appType: "spa",
  server: { port: 3003 },
  resolve: {
    // Longer keys first — `@dreamhorizonorg/pulse-web/react` must not swallow `/react/router`
    alias: [
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
  optimizeDeps: {
    include: ["@dreamhorizonorg/pulse-web"],
  },
});
