import { defineConfig } from "tsup";

export default defineConfig({
  entry: ["src/index.ts", "src/index-http.ts"],
  format: ["esm"],
  platform: "node",
  target: "node22",
  outDir: "dist",
  clean: true,
  sourcemap: true,
  dts: false,
  bundle: true,
});
