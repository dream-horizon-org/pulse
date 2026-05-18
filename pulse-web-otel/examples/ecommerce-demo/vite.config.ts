import { defineConfig, type Plugin } from "vite";
import react from "@vitejs/plugin-react";
import path from "path";

/** Never responds — used by NetworkLab XHR timeout/abort when `import.meta.env.MODE === "test"`. */
function pulseE2eXhrStallPlugin(): Plugin {
  return {
    name: "pulse-e2e-xhr-stall",
    apply: "serve",
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        if (
          req.method === "GET" &&
          typeof req.url === "string" &&
          req.url.startsWith("/pulse-e2e-xhr-stall")
        ) {
          void res;
          return;
        }
        next();
      });
    },
  };
}

export default defineConfig(({ mode }) => ({
  plugins: [react(), ...(mode === "test" ? [pulseE2eXhrStallPlugin()] : [])],
  server: {
    port: 3002,
    // Playwright uses `--mode test`. Disable HMR so the Vite client does not touch
    // `localStorage` (error overlay / HMR state). E2E tests replace `window.localStorage`
    // with a throwing accessor to simulate SecurityError — HMR + that mock breaks boot.
    ...(mode === "test" ? { hmr: false } : {}),
  },
  resolve: {
    // Point directly at SDK source for HMR — changes to SDK src hot-reload in demo
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
    // Pre-bundle host deps only. Do NOT list `@dreamhorizonorg/pulse-web` here: the
    // `resolve.alias` points at monorepo `src/` and mixing alias + optimizeDeps pre-bundle
    // can load two copies of `sdk.ts` → two Pulse singletons. Then PulseProvider inits copy A
    // while App's `_PulseExpose` assigns `window.Pulse` from copy B → isInitialized() stays
    // false in E2E after reload (and any window.Pulse checks).
    include: ["react", "react-dom", "react-router-dom"],
  },
}));
