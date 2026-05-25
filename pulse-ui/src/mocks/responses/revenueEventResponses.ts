/**
 * Revenue events mock responses — GET/POST/PUT/DELETE /v1/revenue-events
 */

import type { RevenueEventConfig } from "../../screens/EventCatalog/RevenueEvent.types";

const store = new Map<string, RevenueEventConfig[]>();

function projectKey(request: { headers?: Record<string, string> }): string {
  const headers = request.headers ?? {};
  return (
    headers["X-Project-Id"] ??
    headers["x-project-id"] ??
    headers["X-Project-ID"] ??
    "default-project"
  );
}

function getProjectConfigs(key: string): RevenueEventConfig[] {
  if (!store.has(key)) {
    store.set(key, []);
  }
  return store.get(key)!;
}

const seedDefaults = (key: string) => {
  const configs = getProjectConfigs(key);
  if (configs.length === 0) {
    configs.push({
      id: "rev-mock-1",
      eventName: "order_placed",
      valueAttribute: "order_amount",
      currency: "INR",
      conversionWindowHours: 24,
      configuredAt: new Date(Date.now() - 7 * 86400000).toISOString(),
    });
  }
};

export function handleRevenueEventEndpoints(
  pathname: string,
  method: string,
  request: { body?: unknown; headers?: Record<string, string> },
): { data: unknown; status: number; error?: { code: string; message: string; cause: string } } {
  const pathOnly = pathname.split("?")[0];
  const project = projectKey(request);
  seedDefaults(project);
  const configs = getProjectConfigs(project);

  const idMatch = pathOnly.match(/\/v1\/revenue-events\/([^/]+)$/);

  if (method === "GET" && /\/v1\/revenue-events$/.test(pathOnly)) {
    return { data: { revenueEvents: [...configs] }, status: 200 };
  }

  if (method === "POST" && /\/v1\/revenue-events$/.test(pathOnly)) {
    const body = (request.body ?? {}) as Partial<RevenueEventConfig>;
    const eventName = String(body.eventName ?? "").trim();
    if (!eventName) {
      return {
        data: null,
        status: 400,
        error: { code: "400", message: "eventName is required", cause: "validation" },
      };
    }
    if (configs.some((c) => c.eventName === eventName)) {
      return {
        data: null,
        status: 409,
        error: {
          code: "409",
          message: "A revenue event is already configured for this event name",
          cause: "duplicate",
        },
      };
    }
    const created: RevenueEventConfig = {
      id: crypto.randomUUID(),
      eventName,
      valueAttribute: String(body.valueAttribute ?? ""),
      currency: String(body.currency ?? ""),
      currencyAttribute: body.currencyAttribute,
      conversionWindowHours: Number(body.conversionWindowHours ?? 24),
      configuredAt: new Date().toISOString(),
    };
    configs.push(created);
    return { data: created, status: 200 };
  }

  if (idMatch) {
    const id = decodeURIComponent(idMatch[1]);
    const idx = configs.findIndex((c) => c.id === id);

    if (method === "PUT") {
      if (idx < 0) {
        return {
          data: null,
          status: 404,
          error: { code: "404", message: "Revenue event not found", cause: "not found" },
        };
      }
      const body = (request.body ?? {}) as Partial<RevenueEventConfig>;
      const eventName = String(body.eventName ?? "").trim();
      if (
        configs.some((c) => c.eventName === eventName && c.id !== id)
      ) {
        return {
          data: null,
          status: 409,
          error: {
            code: "409",
            message: "A revenue event is already configured for this event name",
            cause: "duplicate",
          },
        };
      }
      configs[idx] = {
        ...configs[idx],
        eventName,
        valueAttribute: String(body.valueAttribute ?? ""),
        currency: String(body.currency ?? ""),
        currencyAttribute: body.currencyAttribute,
        conversionWindowHours: Number(body.conversionWindowHours ?? 24),
      };
      return { data: "Success", status: 200 };
    }

    if (method === "DELETE") {
      if (idx < 0) {
        return {
          data: null,
          status: 404,
          error: { code: "404", message: "Revenue event not found", cause: "not found" },
        };
      }
      configs.splice(idx, 1);
      return { data: "Success", status: 200 };
    }
  }

  return {
    data: null,
    status: 404,
    error: { code: "404", message: "Revenue events path not found", cause: "not found" },
  };
}

/** Test helper — reset in-memory store between tests. */
export function resetRevenueEventMocks() {
  store.clear();
}
