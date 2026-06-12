import type {
  PulseAttributesToAddEntry,
  PulseAttributesToDropEntry,
  PulseMetricsToAddEntry,
  PulseMetricsToAddTarget,
  PulseSamplingConfig,
  PulseSdkConfig,
  PulseSignalMatchCondition,
  PulseSignalsToSampleEntry,
} from "./types/remote-config";
import { PulseWebLogger } from "./pulse-web-logger";
import { DEFAULT_SDK_CONFIG } from "./constants/default-sdk-config";

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
  PulseMetricsToAddEntry,
  PulseMetricsToAddTarget,
  PulseMetricsType,
  PulseSignalConfig,
  PulseSignalFilter,
  PulseSignalMatchCondition,
} from "./types/remote-config";
export { PulseFeature } from "./types/remote-config";

const SDK_CONFIG_CACHE_KEY = "pulse_sdk_config";

function normalizeMatchProp(p: {
  key?: string;
  name?: string;
  value?: string | null;
}): { key: string; value: string } {
  return {
    key: p.key ?? p.name ?? "",
    value: p.value ?? "",
  };
}

/** Dashboard / server JSON often uses lowercase scopes; matcher expects uppercase values. */
export function normalizeSignalMatchCondition(
  c: PulseSignalMatchCondition,
): PulseSignalMatchCondition {
  const scopes = (c.scopes ?? [])
    .map((s) => String(s).toUpperCase())
    .filter(
      (u): u is "LOGS" | "TRACES" | "METRICS" =>
        u === "LOGS" || u === "TRACES" || u === "METRICS",
    );
  const rawProps = (c.props ?? []) as Array<{
    key?: string;
    name?: string;
    value?: string | null;
  }>;
  return {
    ...c,
    props: rawProps.map(normalizeMatchProp),
    scopes,
  };
}

function normalizePulseMetricsToAddTarget(
  t: PulseMetricsToAddTarget,
): PulseMetricsToAddTarget {
  if (t.type === "attribute") {
    return {
      ...t,
      condition: normalizeSignalMatchCondition(t.condition),
    };
  }
  return t;
}

function normalizeMetricsToAddEntry(
  e: PulseMetricsToAddEntry,
): PulseMetricsToAddEntry {
  return {
    ...e,
    condition: normalizeSignalMatchCondition(
      e.condition ?? {
        name: ".*",
        props: [],
        scopes: [],
        sdks: [],
      },
    ),
    target: normalizePulseMetricsToAddTarget(e.target),
    attributesToPick: (e.attributesToPick ?? []).map(
      normalizeSignalMatchCondition,
    ),
  };
}

function normalizeMetricsToAdd(
  entries: PulseMetricsToAddEntry[] | undefined,
): PulseMetricsToAddEntry[] {
  return (entries ?? []).map(normalizeMetricsToAddEntry);
}

function normalizeAttributesToDrop(
  entries: PulseAttributesToDropEntry[],
): PulseAttributesToDropEntry[] {
  return entries.map((e) => ({
    ...e,
    condition: normalizeSignalMatchCondition(e.condition),
  }));
}

function normalizeAttributesToAdd(
  entries: PulseAttributesToAddEntry[],
): PulseAttributesToAddEntry[] {
  return entries.map((e) => ({
    ...e,
    condition: normalizeSignalMatchCondition(e.condition),
  }));
}

/** Merge server JSON with defaults; normalizes `sampling.criticalSessionPolicies`. */
export function mergePulseSdkConfig(raw: PulseSdkConfig): PulseSdkConfig {
  const samplingIn = raw.sampling ?? DEFAULT_SDK_CONFIG.sampling;
  const {
    criticalSessionPolicies: _csp,
    criticalEventPolicies: _legacyEventKey,
    ...samplingRest
  } = samplingIn as PulseSamplingConfig & {
    criticalEventPolicies?: { alwaysSend?: PulseSignalMatchCondition[] };
  };
  void _legacyEventKey;
  const signalsIn = raw.signals ?? DEFAULT_SDK_CONFIG.signals;
  const filtersMerged = {
    ...DEFAULT_SDK_CONFIG.signals.filters,
    ...signalsIn.filters,
  };
  return {
    ...DEFAULT_SDK_CONFIG,
    ...raw,
    sampling: {
      ...DEFAULT_SDK_CONFIG.sampling,
      ...samplingRest,
      default: {
        ...DEFAULT_SDK_CONFIG.sampling.default,
        ...samplingIn.default,
      },
      rules: samplingIn.rules ?? [],
      signalsToSample: (samplingIn.signalsToSample ?? []).map(
        (e: PulseSignalsToSampleEntry) => ({
          ...e,
          condition: normalizeSignalMatchCondition(e.condition),
        }),
      ),
      ...(_csp !== undefined
        ? {
            criticalSessionPolicies: {
              alwaysSend: (_csp.alwaysSend ?? []).map(
                normalizeSignalMatchCondition,
              ),
            },
          }
        : {}),
    },
    signals: {
      ...DEFAULT_SDK_CONFIG.signals,
      ...signalsIn,
      attributesToDrop: normalizeAttributesToDrop(
        signalsIn.attributesToDrop ?? [],
      ),
      attributesToAdd: normalizeAttributesToAdd(
        signalsIn.attributesToAdd ?? [],
      ),
      filters: {
        ...filtersMerged,
        values: (filtersMerged.values ?? []).map(normalizeSignalMatchCondition),
      },
      metricsToAdd: normalizeMetricsToAdd(signalsIn.metricsToAdd),
    },
    interaction: raw.interaction ?? DEFAULT_SDK_CONFIG.interaction,
    features: raw.features ?? [],
  };
}

function sdkConfigDevLog(
  phase: string,
  detail?: Record<string, unknown>,
): void {
  if (detail === undefined) {
    PulseWebLogger.debug(`[sdkConfig] ${phase}`);
  } else {
    let encoded: string;
    try {
      encoded = JSON.stringify(detail);
    } catch {
      encoded = String(detail);
    }
    PulseWebLogger.debug(`[sdkConfig] ${phase} ${encoded}`);
  }
}

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

/** Resolves active SDK config URL for local/prod endpoints. */
export function resolveConfigUrl(
  configEndpointUrl: string | undefined,
  endpointBaseUrl: string,
  projectId: string,
): string {
  if (configEndpointUrl) return configEndpointUrl;
  // Local dev: rewrite :4318 → :8080, use /v1/configs/active/
  if (
    endpointBaseUrl.includes("localhost") ||
    endpointBaseUrl.includes("10.0.2.2") ||
    endpointBaseUrl.includes("127.0.0.1")
  ) {
    return `${endpointBaseUrl.replace(/:4318\b/, ":8080").replace(/\/$/, "")}/v1/configs/active/`;
  }
  // Prod: /config/projects/{projectId}/pulse-config.json
  return `${endpointBaseUrl.replace(/\/$/, "")}/config/projects/${projectId}/pulse-config.json`;
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
    this.configUrl = resolveConfigUrl(
      configEndpointUrl,
      endpointBaseUrl,
      projectId,
    );
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
          this.config = mergePulseSdkConfig(parsed);
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
        this.config = mergePulseSdkConfig(data);
        sdkConfigDevLog("fetchInBackground applied new version (in-memory)", {
          previousVersion,
          newVersion: data.version,
          description: data.description,
        });

        if (typeof window !== "undefined") {
          try {
            localStorage.setItem(
              SDK_CONFIG_CACHE_KEY,
              JSON.stringify(this.config),
            );
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
