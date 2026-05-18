import type { Page, Route } from "@playwright/test";

import { findAllSpans } from "./fixture";

export type EventConfig = {
  name: string;
  isBlacklisted?: boolean;
  props?: Array<{
    name?: string;
    value: string;
    operator: string;
  }> | null;
};

function toNumericId(id: number | string): number {
  if (typeof id === "number") return id;
  let hash = 0;
  for (let i = 0; i < id.length; i += 1) {
    hash = (hash * 31 + id.charCodeAt(i)) >>> 0;
  }
  return hash === 0 ? 1 : hash;
}

export function expectedConfigId(id: number | string): string {
  return String(toNumericId(id));
}

export function makeConfig(opts: {
  id: number | string;
  name: string;
  events: EventConfig[];
  description?: string;
  thresholdInMs?: number;
  uptimeLowerLimitInMs?: number;
  uptimeMidLimitInMs?: number;
  uptimeUpperLimitInMs?: number;
  globalBlacklistedEvents?: Array<EventConfig | string>;
}) {
  return {
    id: toNumericId(opts.id),
    name: opts.name,
    description: opts.description ?? opts.name,
    events: opts.events.map((event) => ({
      name: event.name,
      isBlacklisted: event.isBlacklisted ?? false,
      props:
        event.props == null
          ? event.props
          : event.props.map((prop) => ({
              name: prop.name ?? "",
              value: prop.value,
              operator: prop.operator,
            })),
    })),
    thresholdInMs: opts.thresholdInMs ?? 600,
    uptimeLowerLimitInMs: opts.uptimeLowerLimitInMs ?? 120,
    uptimeMidLimitInMs: opts.uptimeMidLimitInMs ?? 240,
    uptimeUpperLimitInMs: opts.uptimeUpperLimitInMs ?? 360,
    globalBlacklistedEvents:
      opts.globalBlacklistedEvents?.map((event) => {
        if (typeof event === "string") {
          return { name: event, isBlacklisted: true, props: [] };
        }
        return {
          name: event.name,
          isBlacklisted: event.isBlacklisted ?? true,
          props:
            event.props == null
              ? event.props
              : event.props.map((prop) => ({
                  name: prop.name ?? "",
                  value: prop.value,
                  operator: prop.operator,
                })),
        };
      }) ?? [],
  };
}

export async function seedInteractionConfig(
  page: Page,
  payload: unknown,
): Promise<void> {
  await page.route("**/v1/interaction-configs/", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(payload),
    });
  });
}

export async function emitEvent(
  page: Page,
  name: string,
  props?: Record<string, unknown>,
  timestampMs?: number,
): Promise<void> {
  await page.evaluate(
    ({ eventName, eventProps, atMs }) => {
      const w = window as unknown as {
        Pulse?: {
          trackEvent?: (
            eventName: string,
            attrs?: Record<string, unknown>,
            eventTimeMs?: number,
          ) => void;
        };
      };
      w.Pulse?.trackEvent?.(eventName, eventProps, atMs ?? Date.now());
    },
    { eventName: name, eventProps: props ?? {}, atMs: timestampMs },
  );
}

export async function setUserId(
  page: Page,
  userId: string | null,
): Promise<void> {
  await page.evaluate((nextUserId) => {
    const w = window as unknown as {
      Pulse?: { setUserId?: (id: string | null) => void };
    };
    w.Pulse?.setUserId?.(nextUserId);
  }, userId);
}

export async function gotoAndWaitInteractionInit(page: Page): Promise<void> {
  await Promise.all([
    page.waitForResponse((r) => r.url().includes("/v1/interaction-configs/"), {
      timeout: 10_000,
    }),
    page.goto("/"),
  ]);
  // Give InteractionFeature.init() a short tick after fetcher resolves.
  await page.waitForTimeout(150);
}

export async function waitForInteractionCount(
  page: Page,
  otlp: { captured: unknown[] },
  expected: number,
  timeoutMs = 8_000,
): Promise<void> {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    if (findAllSpans(otlp.captured as never[], "interaction").length >= expected)
      return;
    await page.waitForTimeout(100);
  }
  throw new Error(
    `Timed out waiting for ${expected} interaction spans (got ${findAllSpans(
      otlp.captured as never[],
      "interaction",
    ).length})`,
  );
}
