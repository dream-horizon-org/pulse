/**
 * Retrying OTLP transport (same behaviour as @opentelemetry/otlp-exporter-base
 * createRetryingTransport) — inlined to avoid deep /build/esm imports that break Vite dev.
 */
import type {
  ExportResponse,
  IExporterTransport,
} from "@opentelemetry/otlp-exporter-base";

const MAX_ATTEMPTS = 5;
const INITIAL_BACKOFF = 1000;
const MAX_BACKOFF = 5000;
const BACKOFF_MULTIPLIER = 1.5;
const JITTER = 0.2;

function getJitter(): number {
  return Math.random() * (2 * JITTER) - JITTER;
}

class RetryingTransport implements IExporterTransport {
  constructor(private readonly inner: IExporterTransport) {}

  private retry(
    data: Uint8Array,
    timeoutMillis: number,
    inMillis: number,
  ): Promise<ExportResponse> {
    return new Promise((resolve, reject) => {
      setTimeout(() => {
        this.inner.send(data, timeoutMillis).then(resolve, reject);
      }, inMillis);
    });
  }

  async send(data: Uint8Array, timeoutMillis: number): Promise<ExportResponse> {
    const deadline = Date.now() + timeoutMillis;
    let result = await this.inner.send(data, timeoutMillis);
    let attempts = MAX_ATTEMPTS;
    let nextBackoff = INITIAL_BACKOFF;

    while (result.status === "retryable" && attempts > 0) {
      attempts--;
      const backoff = Math.max(
        Math.min(nextBackoff, MAX_BACKOFF) + getJitter(),
        0,
      );
      nextBackoff *= BACKOFF_MULTIPLIER;
      const retryInMillis = result.retryInMillis ?? backoff;
      const remainingTimeoutMillis = deadline - Date.now();
      if (retryInMillis > remainingTimeoutMillis) {
        return result;
      }
      result = await this.retry(data, remainingTimeoutMillis, retryInMillis);
    }
    return result;
  }

  shutdown(): void {
    this.inner.shutdown();
  }
}

export function createPulseRetryingTransport(options: {
  transport: IExporterTransport;
}): IExporterTransport {
  return new RetryingTransport(options.transport);
}
