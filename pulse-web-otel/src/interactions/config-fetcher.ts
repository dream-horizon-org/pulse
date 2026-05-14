import type { PulseWebConfig } from "../config";
import { PULSE_PROD_ENDPOINT_URL, isLocalEnvironment } from "../config";
import { PulseWebLogger } from "../pulse-web-logger";
import { extractProjectId } from "../resource";
import type { InteractionConfig, PropertyFilter } from "./interaction-models";

const CACHE_KEY = "pulse_interaction_config";
const REFRESH_INTERVAL_MS = 30 * 60 * 1000;

/** Bound fetch — bare `fetch` as a default arg loses `this` and throws Illegal invocation. */
function defaultFetch(
  input: RequestInfo | URL,
  init?: RequestInit,
): ReturnType<typeof fetch> {
  return globalThis.fetch(input, init);
}

export interface InteractionConfigRequest {
  enabled: boolean;
  url: string;
  headers: Record<string, string>;
}

/**
 * Mirrors Android PulseEndpointUtils.getInteractionConfigUrl().
 * Branches on apiKey (isLocalEnvironment) — same signal as Android isApiLocalDev().
 * Local: {backend}/v1/interaction-configs/ (REST, needs X-API-KEY)
 * Prod:  {PULSE_PROD_ENDPOINT_URL}/config/projects/{projectId}/interaction-config.json
 */
export function resolveInteractionConfigRequest(
  endpointBaseUrl: string,
  config: Pick<PulseWebConfig, "apiKey">,
): InteractionConfigRequest {
  if (isLocalEnvironment(config.apiKey)) {
    return {
      enabled: true,
      url: `${endpointBaseUrl.replace(/:4318\b/, ":8080").replace(/\/$/, "")}/v1/interaction-configs/`,
      headers: {
        "X-API-KEY": config.apiKey,
      },
    };
  }

  const projectId = extractProjectId(config.apiKey);
  return {
    enabled: true,
    url: `${PULSE_PROD_ENDPOINT_URL}/config/projects/${projectId}/interaction-config.json`,
    headers: {},
  };
}

const PROPERTY_OPERATORS = new Set([
  "EQUALS",
  "NOTEQUALS",
  "CONTAINS",
  "NOTCONTAINS",
  "STARTSWITH",
  "ENDSWITH",
]);

function isPropertyFilter(value: unknown): value is PropertyFilter {
  if (typeof value !== "object" || value === null) return false;
  const f = value as Record<string, unknown>;
  return (
    typeof f["name"] === "string" &&
    typeof f["value"] === "string" &&
    typeof f["operator"] === "string" &&
    PROPERTY_OPERATORS.has(f["operator"])
  );
}

function isInteractionEvent(value: unknown): boolean {
  if (typeof value !== "object" || value === null) return false;
  const e = value as Record<string, unknown>;
  if (typeof e["name"] !== "string" || typeof e["isBlacklisted"] !== "boolean") {
    return false;
  }
  if (e["props"] === undefined || e["props"] === null) return true;
  if (!Array.isArray(e["props"])) return false;
  return (e["props"] as unknown[]).every(isPropertyFilter);
}

function isInteractionConfig(value: unknown): value is InteractionConfig {
  if (typeof value !== "object" || value === null) return false;
  const cfg = value as Record<string, unknown>;
  const events = cfg["events"];
  return (
    typeof cfg["id"] === "number" &&
    typeof cfg["name"] === "string" &&
    typeof cfg["description"] === "string" &&
    Array.isArray(events) &&
    (events as unknown[]).every(isInteractionEvent) &&
    (events as unknown[]).some((event) => {
      const e = event as Record<string, unknown>;
      return e["isBlacklisted"] === false;
    }) &&
    typeof cfg["thresholdInMs"] === "number" &&
    typeof cfg["uptimeLowerLimitInMs"] === "number" &&
    typeof cfg["uptimeMidLimitInMs"] === "number" &&
    typeof cfg["uptimeUpperLimitInMs"] === "number" &&
    Array.isArray(cfg["globalBlacklistedEvents"]) &&
    (cfg["globalBlacklistedEvents"] as unknown[]).every(isInteractionEvent)
  );
}

function isInteractionConfigArray(
  value: unknown,
): value is InteractionConfig[] {
  return Array.isArray(value) && value.every(isInteractionConfig);
}

function explainConfigSchemaMismatch(value: unknown): string[] {
  const errors: string[] = [];
  if (!Array.isArray(value)) return ["root: expected array"];
  value.forEach((item, index) => {
    if (typeof item !== "object" || item === null) {
      errors.push(`[${index}]: expected object`);
      return;
    }
    const cfg = item as Record<string, unknown>;
    if (typeof cfg["id"] !== "number") errors.push(`[${index}].id: expected number`);
    if (typeof cfg["name"] !== "string") errors.push(`[${index}].name: expected string`);
    if (typeof cfg["description"] !== "string") {
      errors.push(`[${index}].description: expected string`);
    }
    if (!Array.isArray(cfg["events"])) {
      errors.push(`[${index}].events: expected array`);
    } else {
      const events = cfg["events"] as unknown[];
      if (events.length === 0) errors.push(`[${index}].events: must not be empty`);
      if (!events.some((event) => {
        if (typeof event !== "object" || event === null) return false;
        return (event as Record<string, unknown>)["isBlacklisted"] === false;
      })) {
        errors.push(`[${index}].events: at least one non-blacklisted event required`);
      }
      events.forEach((event, eventIndex) => {
        if (!isInteractionEvent(event)) {
          errors.push(`[${index}].events[${eventIndex}]: invalid event shape`);
        }
      });
    }
    if (!Array.isArray(cfg["globalBlacklistedEvents"])) {
      errors.push(`[${index}].globalBlacklistedEvents: expected array`);
    } else {
      (cfg["globalBlacklistedEvents"] as unknown[]).forEach((event, eventIndex) => {
        if (!isInteractionEvent(event)) {
          errors.push(
            `[${index}].globalBlacklistedEvents[${eventIndex}]: invalid event shape`,
          );
        }
      });
    }
    if (typeof cfg["thresholdInMs"] !== "number") {
      errors.push(`[${index}].thresholdInMs: expected number`);
    }
    if (typeof cfg["uptimeLowerLimitInMs"] !== "number") {
      errors.push(`[${index}].uptimeLowerLimitInMs: expected number`);
    }
    if (typeof cfg["uptimeMidLimitInMs"] !== "number") {
      errors.push(`[${index}].uptimeMidLimitInMs: expected number`);
    }
    if (typeof cfg["uptimeUpperLimitInMs"] !== "number") {
      errors.push(`[${index}].uptimeUpperLimitInMs: expected number`);
    }
  });
  return errors;
}

export class InteractionConfigFetcher {
  private configs: InteractionConfig[] = [];
  private refreshTimer?: ReturnType<typeof setTimeout>;
  private listeners: Array<(configs: InteractionConfig[]) => void> = [];

  constructor(
    private readonly request: InteractionConfigRequest,
    private readonly fetchFn: typeof fetch = defaultFetch,
  ) {}

  async init(): Promise<void> {
    if (!this.request.enabled) {
      this.configs = [];
      return;
    }
    this.loadFromCache();
    await this.refresh();
    this.scheduleRefresh();
  }

  getConfigs(): InteractionConfig[] {
    return this.configs;
  }

  onChange(listener: (configs: InteractionConfig[]) => void): void {
    this.listeners.push(listener);
  }

  destroy(): void {
    if (this.refreshTimer) clearTimeout(this.refreshTimer);
  }

  private async refresh(): Promise<void> {
    try {
      const response = await this.fetchFn(this.request.url, {
        headers: { ...this.request.headers },
        cache: "no-store",
      });

      if (!response.ok) {
        PulseWebLogger.warn(
          `[Pulse] Interaction config fetch failed: ${response.status}`,
        );
        return;
      }

      const json: unknown = await response.json();
      if (!isInteractionConfigArray(json)) {
        const details = explainConfigSchemaMismatch(json).slice(0, 5).join("; ");
        PulseWebLogger.warn(
          `[Pulse] Interaction config fetch returned invalid schema: ${details}`,
        );
        return;
      }

      this.setConfigs(json);
      this.saveToCache(json);
    } catch (err) {
      PulseWebLogger.warn(
        `[Pulse] Interaction config fetch error: ${String(err)}`,
      );
    }
  }

  private setConfigs(configs: InteractionConfig[]): void {
    this.configs = configs;
    this.listeners.forEach((fn) => fn(configs));
  }

  private loadFromCache(): void {
    if (typeof window === "undefined") return;
    try {
      const raw = localStorage.getItem(CACHE_KEY);
      if (!raw) return;
      const parsed: unknown = JSON.parse(raw);
      if (!isInteractionConfigArray(parsed)) return;
      this.configs = parsed;
    } catch {
      // Corrupt cache / blocked storage: no-op.
    }
  }

  private saveToCache(configs: InteractionConfig[]): void {
    if (typeof window === "undefined") return;
    try {
      localStorage.setItem(CACHE_KEY, JSON.stringify(configs));
    } catch {
      // Blocked storage / quota exceeded: no-op.
    }
  }

  private scheduleRefresh(): void {
    this.refreshTimer = setTimeout(() => {
      this.refresh().then(() => this.scheduleRefresh());
    }, REFRESH_INTERVAL_MS);
  }
}
