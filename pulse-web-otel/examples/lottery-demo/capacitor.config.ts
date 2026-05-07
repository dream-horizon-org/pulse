import type { CapacitorConfig } from "@capacitor/cli";

const config: CapacitorConfig = {
  appId: "com.dreamhorizon.lotterydemo",
  appName: "LotteryDemo",
  // webDir: points at Next.js build output for production native builds.
  // For dev: comment out webDir and use server.url instead.
  webDir: "out",
  server: {
    url: "http://10.0.2.2:3006",
    cleartext: true,
  },
};

export default config;
