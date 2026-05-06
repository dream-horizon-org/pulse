/**
 * Server-side crash capture for Next.js 15+ via `instrumentation.ts`.
 *
 * Usage — create `instrumentation.ts` at your project root:
 *
 * ```ts
 * import { createPulseInstrumentationHandler } from "@dreamhorizon/pulse-web/next";
 *
 * export const onRequestError = createPulseInstrumentationHandler({
 *   apiKey: process.env.PULSE_API_KEY!,
 *   collectorEndpoint: "https://your-collector/v1/logs",
 *   serviceName: "my-nextjs-app",
 * });
 * ```
 *
 * Edge-safe: uses `fetch` only — no Node.js-specific APIs.
 *
 * Note: if you are also using the OpenTelemetry Node SDK (via
 * `@vercel/otel` or similar), set `NEXT_RUNTIME === 'nodejs'` guards around
 * your NodeSDK init to avoid double-exporting from edge workers.
 */

import { PulseWebSemconv } from "../../semconv";

export interface PulseInstrumentationConfig {
  /** Pulse API key — sent as X-API-KEY header, matching the browser transport contract. */
  apiKey: string;
  /** OTLP/HTTP logs endpoint, e.g. https://collector.example.com/v1/logs */
  collectorEndpoint: string;
  /** Value for the `service.name` resource attribute. */
  serviceName: string;
}

type NextRequestError = {
  message: string;
  name?: string;
  stack?: string;
};

type NextInstrumentationRequest = {
  path?: string;
  url?: string;
  method?: string;
};

type NextInstrumentationContext = {
  routerKind?: string;
  routePath?: string;
  routeType?: string;
  renderSource?: string;
  revalidateReason?: string;
};

function buildOtlpLogsBody(
  serviceName: string,
  attrs: Record<string, string>,
  body: string,
): string {
  const attributes = Object.entries(attrs).map(([key, value]) => ({
    key,
    value: { stringValue: value },
  }));

  return JSON.stringify({
    resourceLogs: [
      {
        resource: {
          attributes: [
            {
              key: PulseWebSemconv.ResourceKey.SERVICE_NAME,
              value: { stringValue: serviceName },
            },
            {
              key: PulseWebSemconv.ResourceKey.PLATFORM,
              value: { stringValue: "web" },
            },
          ],
        },
        scopeLogs: [
          {
            scope: { name: "@dreamhorizon/pulse-web" },
            logRecords: [
              {
                timeUnixNano: String(Date.now() * 1_000_000),
                severityText: "ERROR",
                body: { stringValue: body },
                attributes,
              },
            ],
          },
        ],
      },
    ],
  });
}

/**
 * Returns a handler compatible with Next.js `instrumentation.ts` `onRequestError`.
 * Sends `device.crash` signals for server-side render / API route errors.
 */
export function createPulseInstrumentationHandler(
  config: PulseInstrumentationConfig,
): (
  error: NextRequestError,
  request: NextInstrumentationRequest,
  context: NextInstrumentationContext,
) => void {
  return (error, request, context): void => {
    const attrs: Record<string, string> = {
      [PulseWebSemconv.AttributeKey.PULSE_TYPE]:
        PulseWebSemconv.PulseType.DEVICE_CRASH,
      [PulseWebSemconv.AttributeKey.EXCEPTION_MESSAGE]: error.message ?? "",
      [PulseWebSemconv.AttributeKey.EXCEPTION_TYPE]: error.name ?? "Error",
      [PulseWebSemconv.AttributeKey.EXCEPTION_STACKTRACE]: error.stack ?? "",
      "server.request_path": request.path ?? request.url ?? "",
      "server.router_kind": context.routerKind ?? "",
      "server.route_path": context.routePath ?? "",
      "server.route_type": context.routeType ?? "",
    };

    fetch(config.collectorEndpoint, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-API-KEY": config.apiKey,
      },
      body: buildOtlpLogsBody(config.serviceName, attrs, error.message),
    }).catch(() => {
      // Best-effort — never throw from error handler
    });
  };
}
