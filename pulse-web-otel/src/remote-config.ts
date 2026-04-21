// M1/M2: SdkConfigFetcher — loads cached config from localStorage on init,
// fetches fresh config in the background from /v1/configs/active.
// See: web-sdk-plan/v1/01-foundation/sdk-config.md

import type { PulseSdkConfig } from "./types/remote-config";

export type {
  PulseAttributeValue,
  PulseAttributesToAddEntry,
  PulseAttributesToDropEntry,
  PulseFeatureConfig,
  PulseFeatureName,
  PulseSamplingConfig,
  PulseSdkConfig,
  PulseSdkName,
  PulseSessionSamplingRule,
  PulseSignalConfig,
  PulseSignalFilter,
  PulseSignalMatchCondition,
} from "./types/remote-config";

const SDK_CONFIG_CACHE_KEY = "pulse_sdk_config";

/** Temporary dev tracing — grep `PulseWeb:sdkConfig` or `sdkConfigDevLog` to remove. */
function sdkConfigDevLog(
  phase: string,
  detail?: Record<string, unknown>,
): void {
  if (detail === undefined) {
    console.log("[PulseWeb:sdkConfig]", phase);
  } else {
    console.log("[PulseWeb:sdkConfig]", phase, detail);
  }
}

export const DEFAULT_SDK_CONFIG: PulseSdkConfig = {
  version: -1,
  sampling: { default: { sessionSampleRate: 1.0 }, rules: [] },
  signals: {
    scheduleDurationMs: 5000,
    attributesToDrop: [],
    attributesToAdd: [],
    filters: { mode: "BLACKLIST", values: [] },
  },
  interaction: { beforeInitQueueSize: 5000 },
  features: [],
};

function isValidSdkConfig(value: unknown): value is PulseSdkConfig {
  if (typeof value !== "object" || value === null) return false;
  const v = value as Record<string, unknown>;
  return (
    typeof v["version"] === "number" &&
    typeof v["sampling"] === "object" &&
    typeof v["signals"] === "object" &&
    Array.isArray(v["features"])
  );
}

/**
 * Derives the Pulse server base URL from the OTLP collector URL.
 * Mirrors Android's PulseSdkConfigRefresher.resolveConfigUrl():
 *   endpointBaseUrl(:4318) → pulseServerUrl(:8080)
 * If configEndpointUrl is supplied explicitly it takes precedence.
 */
export function resolveConfigUrl(
  configEndpointUrl: string | undefined,
  endpointBaseUrl: string,
): string {
  if (configEndpointUrl) return configEndpointUrl;
  const serverBase = endpointBaseUrl
    .replace(/:4318\b/, ":8080")
    .replace(/\/$/, "");
  return `${serverBase}/v1/configs/active/`;
}

export class SdkConfigFetcher {
  private config: PulseSdkConfig = { ...DEFAULT_SDK_CONFIG };
  private readonly configUrl: string;
  private readonly projectId: string;
  private readonly apiKey: string;

  constructor(
    endpointBaseUrl: string,
    projectId: string,
    configEndpointUrl?: string,
    apiKey?: string,
  ) {
    this.configUrl = resolveConfigUrl(configEndpointUrl, endpointBaseUrl);
    this.projectId = projectId;
    this.apiKey = apiKey ?? "";
  }

  loadCached(): PulseSdkConfig {
    let source:
      | "ssr_no_window"
      | "default_no_cache_key"
      | "localStorage_valid"
      | "localStorage_invalid_json"
      | "localStorage_invalid_shape";

    if (typeof window === "undefined") {
      source = "ssr_no_window";
      sdkConfigDevLog("loadCached", {
        source,
        version: this.config.version,
        description: this.config.description,
      });
      return this.config;
    }

    try {
      const raw = localStorage.getItem(SDK_CONFIG_CACHE_KEY);
      if (!raw) {
        source = "default_no_cache_key";
      } else {
        const parsed: unknown = JSON.parse(raw);
        if (isValidSdkConfig(parsed)) {
          this.config = parsed;
          source = "localStorage_valid";
        } else {
          source = "localStorage_invalid_shape";
        }
      }
    } catch {
      source = "localStorage_invalid_json";
    }

    sdkConfigDevLog("loadCached", {
      source,
      version: this.config.version,
      description: this.config.description,
    });
    return this.config;
  }

  async fetchInBackground(): Promise<void> {
    if (!this.configUrl || !this.projectId) {
      sdkConfigDevLog("fetchInBackground skipped", {
        reason: !this.configUrl ? "empty_config_url" : "empty_project_id",
      });
      return;
    }

    const previousVersion = this.config.version;
    sdkConfigDevLog("fetchInBackground start", {
      url: this.configUrl,
      previousVersion,
    });

    try {
      const url = this.configUrl;
      const headers: Record<string, string> = {
        "Content-Type": "application/json",
      };
      if (this.apiKey) headers["X-API-KEY"] = this.apiKey;
      const response = await fetch(url, { headers });

      if (!response.ok) {
        sdkConfigDevLog("fetchInBackground response not ok", {
          status: response.status,
          previousVersion,
        });
        return;
      }

      const data: unknown = await response.json();
      if (!isValidSdkConfig(data)) {
        sdkConfigDevLog("fetchInBackground invalid config body", {
          previousVersion,
        });
        return;
      }

      // Only update and persist if version has changed
      if (data.version !== this.config.version) {
        this.config = data;
        sdkConfigDevLog("fetchInBackground applied new version (in-memory)", {
          previousVersion,
          newVersion: data.version,
          description: data.description,
        });

        if (typeof window !== "undefined") {
          try {
            localStorage.setItem(SDK_CONFIG_CACHE_KEY, JSON.stringify(data));
            sdkConfigDevLog("fetchInBackground persisted localStorage", {
              key: SDK_CONFIG_CACHE_KEY,
              version: data.version,
            });
          } catch (e) {
            sdkConfigDevLog("fetchInBackground persist failed", {
              message: String(e),
              newVersion: data.version,
            });
          }
        }
      } else {
        sdkConfigDevLog("fetchInBackground no version change", {
          version: data.version,
        });
      }
    } catch (err) {
      sdkConfigDevLog("fetchInBackground error", {
        message: String(err),
        previousVersion,
      });
    }
  }

  getConfig(): PulseSdkConfig {
    return this.config;
  }
}
