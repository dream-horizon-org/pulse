// M1: Global attributes processor — injects session.id, screen.name, network attrs
// on every span and log record.

import type { Span, Context } from "@opentelemetry/api";
import type { SpanProcessor, ReadableSpan } from "@opentelemetry/sdk-trace-web";
import type { LogRecord, LogRecordProcessor } from "@opentelemetry/sdk-logs";
import type { SessionProvider } from "../session";
import {
  getOrCreateInstallationId,
  getPersistedUserId,
  getPersistedUserProperties,
} from "../session";
import type { PulseWebConfig } from "../config";
import { computeAspectRatio } from "../resource";

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
  private manualScreenNamePath: string | null = null;
  private readonly screenAspectRatio: string;
  /**
   * In-memory user ID. null = read from localStorage (persisted value).
   * Set explicitly via setUserId() to override the persisted value for
   * the current page load without writing to localStorage.
   * Use PulseWeb.setUserId() to persist across refreshes.
   */
  private _userId: string | null = null;
  private _userProperties: Record<string, string> = {};

  constructor(
    private readonly sessionProvider: SessionProvider,
    private readonly config: PulseWebConfig,
    private readonly meteringSessionId: string = "",
  ) {
    if (typeof screen !== "undefined") {
      const w = screen.width ?? 0;
      const h = screen.height ?? 0;
      this.screenAspectRatio = computeAspectRatio(w, h);
    } else {
      this.screenAspectRatio = "0:0";
    }
  }

  setScreenName(name: string): void {
    this.manualScreenName = name;
    this.manualScreenNamePath =
      typeof location !== "undefined" ? location.pathname : null;
  }

  hydrateUserIdentity(
    userId: string | null,
    props: Record<string, string>,
  ): void {
    this._userId = userId;
    this._userProperties = { ...props };
  }

  setUserId(id: string | null): void {
    this._userId = id;
  }

  getUserId(): string | null {
    return this._userId;
  }

  setUserProperty(key: string, value: string | null): void {
    if (value === null) {
      delete this._userProperties[key];
    } else {
      this._userProperties[key] = value;
    }
  }

  setUserProperties(props: Record<string, string | null>): void {
    for (const [k, v] of Object.entries(props)) {
      this.setUserProperty(k, v);
    }
  }

  getUserPropertiesSnapshot(): Record<string, string> {
    return { ...this._userProperties };
  }

  getCurrentScreenName(): string {
    // Clear manual override if the URL has changed since it was set (SPA navigation).
    if (
      this.manualScreenName !== null &&
      this.manualScreenNamePath !== null &&
      typeof location !== "undefined" &&
      location.pathname !== this.manualScreenNamePath
    ) {
      this.manualScreenName = null;
      this.manualScreenNamePath = null;
    }
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

    const installationId = getOrCreateInstallationId();
    const attrs: Record<string, string | number | boolean> = {
      "session.id": sessionId,
      "window.id": this.sessionProvider.getWindowId(),
      "installation.id": installationId,
      "app.installation.id": installationId,
      "screen.name": screenName,
      "device.screen.aspect_ratio": this.screenAspectRatio,
      "pulse.metering.session.id": this.meteringSessionId,
      platform: "web",
    };

    if (typeof window !== "undefined") {
      attrs["url.path"] = window.location.pathname;
      attrs["page.url"] = window.location.href;
    }

    attrs["network.connection.type"] = network.type ?? "unknown";
    attrs["network.effective_type"] = network.effectiveType ?? "unknown";

    if (typeof network.rtt === "number") {
      attrs["network.rtt"] = network.rtt;
    }
    if (typeof network.downlink === "number") {
      attrs["network.downlink"] = network.downlink;
    }

    // Inject global attributes from config
    if (this.config.globalAttributes) {
      for (const [key, value] of Object.entries(this.config.globalAttributes)) {
        attrs[key] = value as string | number | boolean;
      }
    }

    // User identity — in-memory takes priority; falls back to localStorage so
    // userId/properties set via PulseWeb.setUserId() survive page refresh.
    const resolvedUserId = this._userId ?? getPersistedUserId();
    if (resolvedUserId) {
      attrs["user.id"] = resolvedUserId;
    }

    // User properties: merge persisted base with in-memory overrides.
    const persistedProps = getPersistedUserProperties();
    const merged: Record<string, string> = { ...persistedProps, ...this._userProperties };
    for (const [k, v] of Object.entries(merged)) {
      attrs[`user.${k}`] = v;
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
      if (key === "session.id") {
        const existing = logRecord.attributes
          ? (logRecord.attributes as Record<string, unknown>)["session.id"]
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
