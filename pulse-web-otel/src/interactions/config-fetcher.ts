import type { PulseWebConfig } from "../config";
import { PULSE_PROD_ENDPOINT_URL, isLocalEnvironment } from "../config";
import { extractProjectId } from "../resource";
import type { InteractionConfig, PropertyFilter } from "./interaction-models";

const CACHE_KEY = "pulse_interaction_config";
const REFRESH_INTERVAL_MS = 30 * 60 * 1000;

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
  "NOT_EQUALS",
  "CONTAINS",
  "NOT_CONTAINS",
  "STARTS_WITH",
  "ENDS_WITH",
]);

function isPropertyFilter(value: unknown): value is PropertyFilter {
  if (typeof value !== "object" || value === null) return false;
  const f = value as Record<string, unknown>;
  return (
    typeof f["key"] === "string" &&
    typeof f["value"] === "string" &&
    typeof f["operator"] === "string" &&
    PROPERTY_OPERATORS.has(f["operator"])
  );
}

function isInteractionEvent(value: unknown): boolean {
  if (typeof value !== "object" || value === null) return false;
  const e = value as Record<string, unknown>;
  if (typeof e["name"] !== "string" || typeof e["required"] !== "boolean") {
    return false;
  }
  if (
    e["isBlacklisted"] !== undefined &&
    typeof e["isBlacklisted"] !== "boolean"
  ) {
    return false;
  }
  if (e["props"] === undefined) return true;
  if (!Array.isArray(e["props"])) return false;
  return (e["props"] as unknown[]).every(isPropertyFilter);
}

function isInteractionConfig(value: unknown): value is InteractionConfig {
  if (typeof value !== "object" || value === null) return false;
  const cfg = value as Record<string, unknown>;
  const events = cfg["events"];
  return (
    typeof cfg["id"] === "string" &&
    typeof cfg["name"] === "string" &&
    Array.isArray(events) &&
    (events as unknown[]).every(isInteractionEvent) &&
    typeof cfg["thresholdInMs"] === "number" &&
    typeof cfg["uptimeLowerLimitInMs"] === "number" &&
    typeof cfg["uptimeMidLimitInMs"] === "number" &&
    typeof cfg["uptimeUpperLimitInMs"] === "number" &&
    Array.isArray(cfg["globalBlacklistedEvents"]) &&
    (cfg["globalBlacklistedEvents"] as unknown[]).every(
      (n) => typeof n === "string",
    )
  );
}

function isInteractionConfigArray(
  value: unknown,
): value is InteractionConfig[] {
  return Array.isArray(value) && value.every(isInteractionConfig);
}

export class InteractionConfigFetcher {
  private configs: InteractionConfig[] = [];
  private refreshTimer?: ReturnType<typeof setTimeout>;
  private listeners: Array<(configs: InteractionConfig[]) => void> = [];

  constructor(
    private readonly request: InteractionConfigRequest,
    private readonly fetchFn: typeof fetch = fetch,
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
        console.warn(
          `[Pulse] Interaction config fetch failed: ${response.status}`,
        );
        return;
      }

      const json: unknown = await response.json();
      if (!isInteractionConfigArray(json)) {
        console.warn(
          "[Pulse] Interaction config fetch returned invalid schema",
        );
        return;
      }

      this.setConfigs(json);
      this.saveToCache(json);
    } catch (err) {
      console.warn("[Pulse] Interaction config fetch error:", err);
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
