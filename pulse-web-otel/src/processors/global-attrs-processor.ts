// M1: Global attributes processor — injects session.id, screen.name, network attrs
// on every span and log record.

import type { Span, Context } from "@opentelemetry/api";
import type { SpanProcessor, ReadableSpan } from "@opentelemetry/sdk-trace-web";
import type { SdkLogRecord, LogRecordProcessor } from "@opentelemetry/sdk-logs";
import type { SessionProvider } from "../session";
import { getOrCreateInstallationId } from "../session";
import type { PulseWebConfig } from "../config";
import type { PulseAttributeValue } from "../types/attributes";
import { computeAspectRatio } from "../resource";
import { PulseWebSemconv } from "../semconv";

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

export function isDynamicSegment(seg: string): boolean {
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

  // Heuristic: UUID / ULID / ObjectId / numeric segments → :id
  const segments = pathname.split("/").filter(Boolean);
  const isUuidDashed = (seg: string): boolean =>
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(seg);
  const isNumeric = (seg: string): boolean => /^\d+$/.test(seg);
  /** MongoDB ObjectId (24 hex) or UUID without dashes (32 hex). */
  const isHexObjectIdOr32 = (seg: string): boolean =>
    /^[0-9a-f]{24}$/i.test(seg) || /^[0-9a-f]{32}$/i.test(seg);
  /** ULID — 26 Crockford base32 characters. */
  const isUlid = (seg: string): boolean =>
    /^[0-9A-HJKMNP-TV-Z]{26}$/i.test(seg);

  const mapped = segments.map((seg) => {
    if (
      isNumeric(seg) ||
      isUuidDashed(seg) ||
      isHexObjectIdOr32(seg) ||
      isUlid(seg)
    ) {
      return ":id";
    }
    return seg;
  });

  if (mapped.length > 0) {
    return "/" + mapped.join("/");
  }

  // Fall back to raw pathname
  return pathname || "/";
}

/** URL-derived `screen.name` for SPA History — no manual override (see screen-signals SPEC R3). */
export function resolveScreenNameFromUrl(config: PulseWebConfig): string {
  return resolveScreenName(null, config);
}

export class PulseGlobalAttributesProcessor
  implements SpanProcessor, LogRecordProcessor
{
  private manualScreenName: string | null = null;
  private manualScreenNamePath: string | null = null;
  private readonly screenAspectRatio: string;

  /** Android `setUserId` parity — stamped as `user.id`. */
  private _userId: string | null = null;
  /** Android `setUserProperty` parity — stamped as `pulse.user.<key>`. */
  private _userProperties: Record<string, string> = {};

  /** Pathname seen on last `getCommonAttrs` — drives `last.screen.name` when URL changes. */
  private _trackedPathname: string | null = null;
  /** Resolved `screen.name` for `_trackedPathname` (after that call's `getCurrentScreenName`). */
  private _resolvedAtTrackedPathname = "";

  /** One UUID per navigation (cold, SPA, BFCache); omitted from attrs when empty. */
  private _navigationId = "";

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

  setNavigationId(id: string): void {
    this._navigationId = id;
  }

  /**
   * Restore user id + properties from localStorage at cold start (no lifecycle logs).
   * Must run before signal emission; called from SDK after construction.
   */
  hydrateUserIdentity(
    userId: string | null,
    properties: Record<string, string>,
  ): void {
    this._userId = userId;
    this._userProperties = { ...properties };
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
      if (v === null) {
        delete this._userProperties[k];
      } else {
        this._userProperties[k] = v;
      }
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
  getCommonAttrsForMetrics(): Record<string, PulseAttributeValue> {
    return this.getCommonAttrs();
  }

  private getCommonAttrs(): Record<string, PulseAttributeValue> {
    const sessionId = this.sessionProvider.getSessionId();
    const screenName = this.getCurrentScreenName();
    const pathname =
      typeof window !== "undefined" ? window.location.pathname : "";

    let lastScreenNameForAttrs: string | undefined;
    if (this._trackedPathname !== null && this._trackedPathname !== pathname) {
      lastScreenNameForAttrs = this._resolvedAtTrackedPathname;
    }
    this._resolvedAtTrackedPathname = screenName;
    this._trackedPathname = pathname;

    const network = getNetworkConnection();

    const installationId = getOrCreateInstallationId();
    const attrs: Record<string, PulseAttributeValue> = {
      [PulseWebSemconv.AttributeKey.SESSION_ID]: sessionId,
      "window.id": this.sessionProvider.getWindowId(),
      "installation.id": installationId,
      "app.installation.id": installationId,
      "screen.name": screenName,
      "device.screen.aspect_ratio": this.screenAspectRatio,
      "pulse.metering.session.id": this.meteringSessionId,
      platform: "web",
    };

    if (this._navigationId !== "") {
      attrs[PulseWebSemconv.AttributeKey.NAVIGATION_ID] = this._navigationId;
    }

    if (lastScreenNameForAttrs !== undefined) {
      attrs[PulseWebSemconv.AttributeKey.LAST_SCREEN_NAME] =
        lastScreenNameForAttrs;
    }

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

    // Inject global attributes from config (span attributes — primitives + homogenous arrays)
    if (this.config.globalAttributes) {
      for (const [key, value] of Object.entries(this.config.globalAttributes)) {
        if (value === undefined) continue;
        attrs[key] = value;
      }
    }

    const attributeKeys = PulseWebSemconv.AttributeKey;
    if (this._userId !== null && this._userId !== "") {
      attrs[attributeKeys.USER_ID] = this._userId;
    }
    for (const [k, v] of Object.entries(this._userProperties)) {
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

  onEmit(logRecord: SdkLogRecord): void {
    const attrs = this.getCommonAttrs();
    const sessionIdAttr = PulseWebSemconv.AttributeKey.SESSION_ID;
    for (const [key, value] of Object.entries(attrs)) {
      // Do not overwrite session.id if the instrumentation already set it explicitly.
      // session.start / session.end log records set the correct session.id themselves;
      // overwriting them with the post-rotation value from getSessionId() would corrupt
      // the session.end record (it would carry the NEW session.id instead of the old one).
      if (key === sessionIdAttr) {
        const existing = logRecord.attributes
          ? (logRecord.attributes as Record<string, unknown>)[sessionIdAttr]
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
