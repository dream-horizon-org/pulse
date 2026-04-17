import { randomUUID } from "crypto";

import Kafka, { CODES as ErrorCodes, Message, TopicPartition } from "node-rdkafka";

import { extractHeatmapScreenshot } from "./heatmap-extract";
import {
  buildHeatmapDedupeKey,
  buildHeatmapQuotaKey,
  createHeatmapRedis,
  type HeatmapRedis,
} from "./heatmap-redis";
import { KafkaMessageParser, type RawKafkaMessage } from "./kafka/message-parser";
import { KafkaOffsetManager } from "./kafka/offset-manager";
import {
  appVersionForPath,
  buildHeatmapS3ObjectKey,
  heatmapJsonBody,
} from "./s3-key";
import { createS3Client, putJsonWithRetry } from "./s3-upload";
import type { Config } from "./config";
import { resolveHeatmapBreakpoint } from "./breakpoint-rules";

/**
 * Consumes `session_recording_events` with a dedicated consumer group,
 * uploads heatmap screenshot JSON to S3 when META + full snapshot screenshot
 * exist in the same `snapshot_items` batch.
 */
export class HeatmapScreenshotConsumer {
  private consumer: Kafka.KafkaConsumer | null = null;
  private readonly parser = new KafkaMessageParser();
  private stopping = false;
  private redis: HeatmapRedis | null = null;

  constructor(private readonly config: Config) {}

  public async start(): Promise<void> {
    console.log("[HeatmapConsumer] Starting heatmap screenshot ingestion...");

    this.validateRedisEnv();
    this.redis = createHeatmapRedis(this.config);
    if (this.redis) {
      await this.redis.ping();
      console.log(
        `[HeatmapConsumer] Redis connected (quota=${this.config.heatmapQuotaEnabled} dedupe=${this.config.heatmapDedupeEnabled})`,
      );
    } else {
      console.log(
        "[HeatmapConsumer] Redis disabled (no quota/dedupe or no REDIS_URL)",
      );
    }

    const batchSize = this.effectiveFetchBatchSize();
    if (batchSize === 1 && this.config.fetchBatchSize > 1) {
      console.log(
        `[HeatmapConsumer] Using fetch batch size 1 while quota/dedupe Redis features are enabled (FETCH_BATCH_SIZE=${this.config.fetchBatchSize} ignored for offset safety)`,
      );
    }

    const s3 = createS3Client(this.config);

    this.consumer = new Kafka.KafkaConsumer(
      {
        "group.id": this.config.kafkaGroupId,
        "metadata.broker.list": this.config.kafkaBrokers,
        "enable.auto.commit": true,
        "enable.auto.offset.store": false,
        "session.timeout.ms": 90000,
        "max.poll.interval.ms": 300000,
        "fetch.min.bytes": 1,
        "fetch.wait.max.ms": 500,
      } as any,
      {
        "auto.offset.reset": "earliest",
      },
    );

    const offsetManager = new KafkaOffsetManager((offsets) => {
      if (this.consumer) {
        this.consumer.offsetsStore(offsets);
      }
    }, this.config.kafkaTopic);

    this.consumer.on("rebalance", (err: any, assignments: TopicPartition[]) => {
      if (err.code === ErrorCodes.ERRORS.ERR__ASSIGN_PARTITIONS) {
        const partitions = assignments.map((a) => a.partition);
        console.log(`[HeatmapConsumer] Partitions assigned: ${partitions.join(", ")}`);
      } else if (err.code === ErrorCodes.ERRORS.ERR__REVOKE_PARTITIONS) {
        const partitions = assignments.map((a) => a.partition);
        console.log(`[HeatmapConsumer] Partitions revoked: ${partitions.join(", ")}`);
      }
    });

    this.consumer.on("event.error", (err: unknown) => {
      console.error("[HeatmapConsumer] Kafka error:", err);
    });

    await this.connectConsumer();
    this.consumer.setDefaultConsumeTimeout(500);
    this.consumer.subscribe([this.config.kafkaTopic]);

    console.log(
      `[HeatmapConsumer] Subscribed to ${this.config.kafkaTopic} group=${this.config.kafkaGroupId}`,
    );

    await this.consumeLoop(s3, offsetManager, batchSize);
  }

  public async stop(): Promise<void> {
    console.log("[HeatmapConsumer] Stopping...");
    this.stopping = true;
    if (this.consumer) {
      await new Promise<void>((resolve) => {
        this.consumer!.disconnect(() => resolve());
      });
    }
    if (this.redis) {
      await this.redis.quit();
      this.redis = null;
    }
    console.log("[HeatmapConsumer] Stopped");
  }

  private validateRedisEnv(): void {
    if (
      (this.config.heatmapQuotaEnabled || this.config.heatmapDedupeEnabled) &&
      !this.config.redisUrl
    ) {
      throw new Error(
        "REDIS_URL is required when HEATMAP_QUOTA_ENABLED or HEATMAP_DEDUPE_ENABLED is true",
      );
    }
  }

  /** One message per poll when Redis quota/dedupe is on so offset commits stay aligned with Redis failures. */
  private effectiveFetchBatchSize(): number {
    if (this.config.heatmapQuotaEnabled || this.config.heatmapDedupeEnabled) {
      return 1;
    }
    return this.config.fetchBatchSize;
  }

  private async consumeLoop(
    s3: ReturnType<typeof createS3Client>,
    offsetManager: KafkaOffsetManager,
    fetchBatchSize: number,
  ): Promise<void> {
    const quotaOn = Boolean(this.redis && this.config.heatmapQuotaEnabled);
    const dedupeOn = Boolean(this.redis && this.config.heatmapDedupeEnabled);

    while (!this.stopping) {
      try {
        const messages = await this.consumeBatch(fetchBatchSize);
        if (messages.length === 0) {
          continue;
        }

        for (const m of messages) {
          const raw: RawKafkaMessage = {
            value: m.value ?? null,
            timestamp: m.timestamp ?? Date.now(),
            partition: m.partition,
            topic: m.topic,
            size: m.size,
            offset: m.offset,
            headers: m.headers as Array<{ [key: string]: Buffer }> | undefined,
          };

          const parsed = await this.parser.parseMessage(raw);
          if (!parsed) {
            offsetManager.trackOffset(m.partition, m.offset);
            await offsetManager.commit();
            continue;
          }

          const extracted = extractHeatmapScreenshot(parsed);
          if (!extracted) {
            offsetManager.trackOffset(m.partition, m.offset);
            await offsetManager.commit();
            continue;
          }

          const platform = parsed.snapshot_source ?? "unknown";
          const breakpoint = resolveHeatmapBreakpoint(
            platform,
            extracted.meta.width,
            extracted.meta.height,
          );

          const appLabel = appVersionForPath(parsed.app_version);

          if (dedupeOn) {
            const dedupeKey = buildHeatmapDedupeKey({
              sessionId: parsed.session_id,
              screenHref: extracted.meta.href,
              metaTimestamp: extracted.meta.timestamp,
              base64: extracted.base64,
            });
            const first = await this.redis!.tryClaimDedupe(dedupeKey);
            if (!first) {
              console.log(
                `[HeatmapConsumer] Dedupe skip partition=${m.partition} offset=${m.offset}`,
              );
              offsetManager.trackOffset(m.partition, m.offset);
              await offsetManager.commit();
              continue;
            }
          }

          let quotaKey: string | null = null;
          if (quotaOn) {
            quotaKey = buildHeatmapQuotaKey({
              metaTimestampMs: extracted.meta.timestamp,
              projectId: parsed.project_id,
              screenHref: extracted.meta.href,
              platform,
              appVersionLabel: appLabel,
              breakpoint,
            });
            const { allowed, count } = await this.redis!.reserveQuota(
              quotaKey,
              this.config.heatmapQuotaPerDay,
            );
            if (!allowed) {
              console.warn(
                `[HeatmapConsumer] Quota exceeded count=${count} partition=${m.partition} offset=${m.offset}`,
              );
              offsetManager.trackOffset(m.partition, m.offset);
              await offsetManager.commit();
              continue;
            }
          }

          const fileName = `capture-${randomUUID()}.json`;
          const key = buildHeatmapS3ObjectKey({
            s3Prefix: this.config.s3Prefix,
            projectId: parsed.project_id,
            metaTimestampMs: extracted.meta.timestamp,
            platform,
            appVersionLabel: appLabel,
            screenHref: extracted.meta.href,
            breakpoint,
            objectFileName: fileName,
          });

          const body = heatmapJsonBody({
            schemaVersion: this.config.schemaVersion,
            projectId: parsed.project_id,
            sessionId: parsed.session_id,
            snapshotSource: platform,
            appVersion: parsed.app_version,
            screenHref: extracted.meta.href,
            breakpoint,
            meta: extracted.meta,
            base64: extracted.base64,
          });

          const ok = await putJsonWithRetry(s3, {
            Bucket: this.config.s3Bucket,
            Key: key,
            Body: body,
            ContentType: "application/json",
          });

          if (!ok) {
            console.error(
              `[HeatmapConsumer] S3 upload failed after retry; skipping object key=${key} offset=${m.offset}`,
            );
            if (quotaKey) {
              await this.redis!.releaseQuota(quotaKey);
            }
          } else {
            console.log(
              `[HeatmapConsumer] Uploaded heatmap screenshot key=${key} partition=${m.partition} offset=${m.offset}`,
            );
          }

          offsetManager.trackOffset(m.partition, m.offset);
          await offsetManager.commit();
        }
      } catch (error) {
        console.error("[HeatmapConsumer] Fatal error in consume loop:", error);
        process.exit(1);
      }
    }
  }

  private consumeBatch(batchSize: number): Promise<Message[]> {
    return new Promise((resolve, reject) => {
      this.consumer!.consume(batchSize, (err: unknown, msgs: Message[]) => {
        if (err) {
          reject(err);
        } else {
          resolve(msgs);
        }
      });
    });
  }

  private connectConsumer(): Promise<void> {
    return new Promise((resolve, reject) => {
      const onError = (err: unknown) => {
        this.consumer!.removeListener("event.error", onError);
        reject(err);
      };
      this.consumer!.on("ready", () => {
        this.consumer!.removeListener("event.error", onError);
        console.log("[HeatmapConsumer] Kafka consumer connected");
        resolve();
      });
      this.consumer!.on("event.error", onError);
      this.consumer!.connect();
    });
  }
}
