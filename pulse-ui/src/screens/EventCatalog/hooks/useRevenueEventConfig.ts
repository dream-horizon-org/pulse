import { useCallback, useMemo, useSyncExternalStore } from "react";
import { RevenueEventConfig } from "../RevenueEvent.types";

const STORAGE_PREFIX = "pulse:revenue-events";
const EMPTY_CONFIGS: RevenueEventConfig[] = [];

const snapshotCache = new Map<
  string,
  { raw: string; snapshot: RevenueEventConfig[] }
>();

function storageKey(projectId: string) {
  return `${STORAGE_PREFIX}:${projectId}`;
}

/** Stable snapshot for useSyncExternalStore — same reference until localStorage changes. */
function getSnapshot(projectId: string): RevenueEventConfig[] {
  if (!projectId || typeof window === "undefined") {
    return EMPTY_CONFIGS;
  }

  const raw = window.localStorage.getItem(storageKey(projectId)) ?? "";
  const cached = snapshotCache.get(projectId);
  if (cached && cached.raw === raw) {
    return cached.snapshot;
  }

  let snapshot = EMPTY_CONFIGS;
  if (raw) {
    try {
      const parsed = JSON.parse(raw) as RevenueEventConfig[];
      if (Array.isArray(parsed)) {
        snapshot = parsed;
      }
    } catch {
      snapshot = EMPTY_CONFIGS;
    }
  }

  snapshotCache.set(projectId, { raw, snapshot });
  return snapshot;
}

function writeConfigs(projectId: string, configs: RevenueEventConfig[]) {
  const raw = JSON.stringify(configs);
  window.localStorage.setItem(storageKey(projectId), raw);
  snapshotCache.set(projectId, { raw, snapshot: configs });
  window.dispatchEvent(new Event("pulse-revenue-events-changed"));
}

function subscribe(onStoreChange: () => void) {
  window.addEventListener("pulse-revenue-events-changed", onStoreChange);
  window.addEventListener("storage", onStoreChange);
  return () => {
    window.removeEventListener("pulse-revenue-events-changed", onStoreChange);
    window.removeEventListener("storage", onStoreChange);
  };
}

export function useRevenueEventConfig(projectId: string | undefined) {
  const pid = projectId ?? "";

  const configs = useSyncExternalStore(
    subscribe,
    () => getSnapshot(pid),
    () => EMPTY_CONFIGS,
  );

  const saveConfig = useCallback(
    (config: Omit<RevenueEventConfig, "id" | "configuredAt"> & { id?: string }) => {
      if (!projectId) {
        return;
      }
      const existing = getSnapshot(projectId);
      const now = new Date().toISOString();
      const next: RevenueEventConfig = {
        id: config.id ?? crypto.randomUUID(),
        eventName: config.eventName,
        valueAttribute: config.valueAttribute,
        currency: config.currency,
        currencyAttribute: config.currencyAttribute,
        conversionWindowHours: config.conversionWindowHours,
        configuredAt: config.id
          ? (existing.find((c) => c.id === config.id)?.configuredAt ?? now)
          : now,
      };

      const withoutDuplicate = existing.filter(
        (c) => c.id !== next.id && c.eventName !== next.eventName,
      );
      writeConfigs(projectId, [...withoutDuplicate, next]);
    },
    [projectId],
  );

  const removeConfig = useCallback(
    (id: string) => {
      if (!projectId) {
        return;
      }
      writeConfigs(
        projectId,
        getSnapshot(projectId).filter((c) => c.id !== id),
      );
    },
    [projectId],
  );

  const getByEventName = useCallback(
    (eventName: string) => configs.find((c) => c.eventName === eventName),
    [configs],
  );

  return useMemo(
    () => ({
      configs,
      saveConfig,
      removeConfig,
      getByEventName,
    }),
    [configs, saveConfig, removeConfig, getByEventName],
  );
}
