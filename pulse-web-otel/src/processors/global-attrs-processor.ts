// M1: Global attributes processor — injects session.id, screen.name, network attrs
// on every span and log record.

import type { Span, Context } from "@opentelemetry/api";
import type { SpanProcessor, ReadableSpan } from "@opentelemetry/sdk-trace-web";
import type { LogRecord, LogRecordProcessor } from "@opentelemetry/sdk-logs";
import type { SessionProvider } from "../session";
import { getOrCreateInstallationId } from "../session";
import type { PulseWebConfig } from "../config";
import { PulseWebSemconv } from "../semconv";

/** When merging global attrs into logs, never overwrite an explicit value here (session.end, etc.). */
const SESSION_ID_ATTR_KEY = PulseWebSemconv.AttributeKey.SESSION_ID;

type NetworkConnection = {
  type?: string;
  effectiveType?: string;
  rtt?: number;
  downlink?: number;
};

function getNetworkConnection(): NetworkConnection {
  if (typeof navigator === "undefined") return {};
  const nav = navigator as unknown as { connection?: NetworkConnection };
  return nav.connection ?? {};
}

function resolveScreenName(
  manualScreenName: string | null,
  config: PulseWebConfig,
): string {
  if (manualScreenName) return manualScreenName;

  if (typeof window === "undefined") return "";

  const pathname = window.location.pathname;

  // Check route patterns
  if (config.routePatterns && config.routePatterns.length > 0) {
    for (const { pattern, name } of config.routePatterns) {
      try {
        const regex = new RegExp(pattern);
        if (regex.test(pathname)) return name;
      } catch {
        // invalid regex — skip
      }
    }
  }

  // Heuristic: strip UUIDs and pure-number segments from path
  const segments = pathname.split("/").filter(Boolean);
  const cleaned = segments.filter((seg) => {
    // Remove UUID-like segments
    if (
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
        seg,
      )
    ) {
      return false;
    }
    // Remove pure number segments
    if (/^\d+$/.test(seg)) {
      return false;
    }
    return true;
  });

  if (cleaned.length > 0) {
    return "/" + cleaned.join("/");
  }

  // Fall back to raw pathname
  return pathname || "/";
}

export class PulseGlobalAttributesProcessor
  implements SpanProcessor, LogRecordProcessor
{
  private manualScreenName: string | null = null;

  constructor(
    private readonly sessionProvider: SessionProvider,
    private readonly config: PulseWebConfig,
  ) {}

  setScreenName(name: string): void {
    this.manualScreenName = name;
  }

  getCurrentScreenName(): string {
    return resolveScreenName(this.manualScreenName, this.config);
  }

  /**
   * Public accessor used by the metric exporter wrapper so metric data points
   * receive the same global attributes as spans and logs.
   */
  getCommonAttrsForMetrics(): Record<string, string | number | boolean> {
    return this.getCommonAttrs();
  }

  private getCommonAttrs(): Record<string, string | number | boolean> {
    const sessionId = this.sessionProvider.getSessionId();
    const screenName = this.getCurrentScreenName();
    const network = getNetworkConnection();

    const K = PulseWebSemconv.AttributeKey;
    const F = PulseWebSemconv.FixedValue;
    const attrs: Record<string, string | number | boolean> = {
      [K.SESSION_ID]: sessionId,
      [K.INSTALLATION_ID]: getOrCreateInstallationId(),
      [K.SCREEN_NAME]: screenName,
      [K.PLATFORM]: F.PLATFORM_WEB,
      [K.WINDOW_ID]: this.sessionProvider.getWindowId(),
    };

    if (typeof window !== "undefined") {
      attrs[K.URL_PATH] = window.location.pathname;
      attrs[K.PAGE_URL] = window.location.href;
    }

    attrs[K.NETWORK_CONNECTION_TYPE] = network.type ?? "unknown";
    attrs[K.NETWORK_EFFECTIVE_TYPE] = network.effectiveType ?? "unknown";

    if (typeof network.rtt === "number") {
      attrs[K.NETWORK_RTT] = network.rtt;
    }
    if (typeof network.downlink === "number") {
      attrs[K.NETWORK_DOWNLINK] = network.downlink;
    }

    // Inject global attributes from config
    if (this.config.globalAttributes) {
      for (const [key, value] of Object.entries(this.config.globalAttributes)) {
        attrs[key] = value;
      }
    }

    return attrs;
  }

  onStart(span: Span, _parentContext: Context): void {
    const attrs = this.getCommonAttrs();
    for (const [key, value] of Object.entries(attrs)) {
      span.setAttribute(key, value);
    }
    this.sessionProvider.updateActivity();
  }

  onEnd(_span: ReadableSpan): void {
    // No-op: attributes set on start
  }

  onEmit(logRecord: LogRecord): void {
    const attrs = this.getCommonAttrs();
    for (const [key, value] of Object.entries(attrs)) {
      // Do not overwrite session.id if the instrumentation already set it explicitly.
      // session.start / session.end log records set the correct session.id themselves;
      // overwriting them with the post-rotation value from getSessionId() would corrupt
      // the session.end record (it would carry the NEW session.id instead of the old one).
      if (key === SESSION_ID_ATTR_KEY) {
        const existing = logRecord.attributes
          ? (logRecord.attributes as Record<string, unknown>)[
              SESSION_ID_ATTR_KEY
            ]
          : undefined;
        if (existing !== undefined && existing !== "") continue;
      }
      logRecord.setAttribute(key, value);
    }
    this.sessionProvider.updateActivity();
  }

  forceFlush(): Promise<void> {
    return Promise.resolve();
  }

  shutdown(): Promise<void> {
    return Promise.resolve();
  }
}
