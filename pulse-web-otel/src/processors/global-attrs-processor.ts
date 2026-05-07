// M1: Global attributes processor — injects session.id, screen.name, network attrs
// on every span and log record.

import type { Span, Context, AttributeValue } from "@opentelemetry/api";
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

function isDynamicSegment(seg: string): boolean {
  // Pure integers: 123, 456789
  if (/^\d+$/.test(seg)) return true;
  // Standard UUID v4 (with dashes): 550e8400-e29b-41d4-a716-446655440000
  if (
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(seg)
  )
    return true;
  // UUID without dashes (32 hex chars): 550e8400e29b41d4a716446655440000
  if (/^[0-9a-f]{32}$/i.test(seg)) return true;
  // MongoDB ObjectId (24 hex chars): 507f1f77bcf86cd799439011
  if (/^[0-9a-f]{24}$/i.test(seg)) return true;
  // ULID (26 Crockford base32 chars): 01ARZ3NDEKTSV4RRFFQ69G5FAV
  if (/^[0-9a-hjkmnp-tv-zA-HJKMNP-TV-Z]{26}$/.test(seg)) return true;
  return false;
}

function resolveScreenName(
  manualScreenName: string | null,
  config: PulseWebConfig,
): string {
  if (manualScreenName) return manualScreenName;

  if (typeof window === "undefined") return "";

  const pathname = window.location.pathname;

  // routePatterns take priority over heuristic
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

  // Heuristic: replace dynamic segments with :id, preserve static segments.
  // /products/123        → /products/:id   (not /products — preserves route shape)
  // /users/uuid/settings → /users/:id/settings
  // /blog/my-post        → /blog/my-post   (unchanged — no dynamic segment detected)
  const segments = pathname.split("/").filter(Boolean);
  if (segments.length === 0) return pathname || "/";

  return (
    "/" + segments.map((seg) => (isDynamicSegment(seg) ? ":id" : seg)).join("/")
  );
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
   * Use Pulse.setUserId() to persist across refreshes.
   */
  private _userId: string | null = null;
  /** null values mark keys that should be suppressed even if present in localStorage. */
  private _userProperties: Record<string, string | null> = {};

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
    this._userProperties = { ...props } as Record<string, string | null>;
  }

  setUserId(id: string | null): void {
    this._userId = id;
  }

  getUserId(): string | null {
    return this._userId;
  }

  setUserProperty(key: string, value: string | null): void {
    this._userProperties[key] = value; // null = suppression marker
  }

  setUserProperties(props: Record<string, string | null>): void {
    for (const [k, v] of Object.entries(props)) {
      this.setUserProperty(k, v);
    }
  }

  getUserPropertiesSnapshot(): Record<string, string> {
    const out: Record<string, string> = {};
    for (const [k, v] of Object.entries(this._userProperties)) {
      if (v !== null) out[k] = v;
    }
    return out;
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
  getCommonAttrsForMetrics(): Record<string, AttributeValue> {
    return this.getCommonAttrs();
  }

  private getCommonAttrs(): Record<string, AttributeValue> {
    const sessionId = this.sessionProvider.getSessionId();
    const screenName = this.getCurrentScreenName();
    const network = getNetworkConnection();

    const installationId = getOrCreateInstallationId();
    const attrs: Record<string, AttributeValue> = {
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
        attrs[key] = value;
      }
    }

    // User identity — in-memory takes priority; falls back to localStorage so
    // userId/properties set via Pulse.setUserId() survive page refresh.
    const resolvedUserId = this._userId ?? getPersistedUserId();
    if (resolvedUserId) {
      attrs["user.id"] = resolvedUserId;
    }

    // User properties: start from localStorage, apply in-memory overrides (null = suppress).
    const persistedProps = getPersistedUserProperties();
    const merged: Record<string, string> = { ...persistedProps };
    for (const [k, v] of Object.entries(this._userProperties)) {
      if (v === null) {
        delete merged[k];
      } else {
        merged[k] = v;
      }
    }
    for (const [k, v] of Object.entries(merged)) {
      attrs[`pulse.user.${k}`] = v;
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
