import { loadConfig } from "./config";
import { HeatmapScreenshotConsumer } from "./consumer";

export * from "./breakpoint-rules";
export { loadConfig, resolveRedisUrlFromEnv } from "./config";
export { HeatmapScreenshotConsumer } from "./consumer";
export { extractHeatmapScreenshots } from "./heatmap-extract";
export {
  buildHeatmapS3ObjectKey,
  sanitizePathSegment,
  appVersionForPath,
  heatmapJsonBody,
} from "./s3-key";
export {
  buildHeatmapDedupeKey,
  buildHeatmapQuotaKey,
  createHeatmapRedis,
  HeatmapRedis,
} from "./heatmap-redis";

async function main(): Promise<void> {
  console.log("=== Pulse Heatmap Screenshot Ingestion ===");

  const config = loadConfig();
  const consumer = new HeatmapScreenshotConsumer(config);

  const shutdown = async (signal: string) => {
    console.log(`\nReceived ${signal}, shutting down gracefully...`);
    await consumer.stop();
    process.exit(0);
  };

  process.on("SIGTERM", () => shutdown("SIGTERM"));
  process.on("SIGINT", () => shutdown("SIGINT"));

  try {
    await consumer.start();
  } catch (error) {
    console.error("Fatal error starting consumer:", error);
    process.exit(1);
  }
}

if (require.main === module) {
  void main();
}
