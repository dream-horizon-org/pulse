import {
  PutObjectCommand,
  type PutObjectCommandInput,
  S3Client,
} from "@aws-sdk/client-s3";

import type { Config } from "./config";

export function createS3Client(config: Config): S3Client {
  return new S3Client({
    endpoint: config.s3Endpoint,
    region: config.s3Region,
    forcePathStyle: true,
    credentials:
      config.s3AccessKeyId && config.s3SecretAccessKey
        ? {
            accessKeyId: config.s3AccessKeyId,
            secretAccessKey: config.s3SecretAccessKey,
          }
        : undefined,
  });
}

/**
 * Two attempts total (initial + one retry). On second failure, return false
 * (caller still advances Kafka offset — best-effort uploads).
 */
export async function putJsonWithRetry(
  client: S3Client,
  input: PutObjectCommandInput,
): Promise<boolean> {
  for (let attempt = 1; attempt <= 2; attempt++) {
    try {
      await client.send(new PutObjectCommand(input));
      return true;
    } catch (err) {
      console.error(
        `[S3] PutObject failed (attempt ${attempt}/2) key=${input.Key}:`,
        err,
      );
      if (attempt === 2) {
        return false;
      }
    }
  }
  return false;
}
