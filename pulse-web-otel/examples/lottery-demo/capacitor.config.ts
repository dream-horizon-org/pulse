import type { CapacitorConfig } from "@capacitor/cli";

const config: CapacitorConfig = {
  appId: "com.dreamhorizon.lotterydemo",
  appName: "LotteryDemo",
  // webDir: points at Next.js build output for production native builds.
  // For dev: comment out webDir and use server.url instead.
  webDir: "out",
  server: {
    // Dev mode: point at the running Next.js dev server on your local machine.
    // Find your IP: ipconfig getifaddr en0 (Mac)
    // Uncomment for native dev testing:
    // url: "http://YOUR_IP:3001",
    // cleartext: true,
  },
};

export default config;
