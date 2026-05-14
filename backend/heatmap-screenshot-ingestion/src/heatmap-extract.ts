import type { ParsedMessageData } from "./kafka/types";

/** `ReplayEventType` — align with Android SDK. */
export const REPLAY_TYPE_FULL_SNAPSHOT = 2;
export const REPLAY_TYPE_META = 4;

export interface MetaPayload {
  href: string;
  width: number;
  height: number;
  timestamp: number;
}

export interface HeatmapScreenshotPayload {
  meta: MetaPayload;
  base64: string;
}

function isRecord(x: unknown): x is Record<string, unknown> {
  return x !== null && typeof x === "object" && !Array.isArray(x);
}

/** First wireframe with type "screenshot" and non-empty base64 (depth-first). */
export function findFirstScreenshotBase64(
  wireframes: unknown,
): string | null {
  if (!Array.isArray(wireframes)) {
    return null;
  }
  for (const node of wireframes) {
    if (!isRecord(node)) continue;
    if (
      node.type === "screenshot" &&
      typeof node.base64 === "string" &&
      node.base64.length > 0
    ) {
      return node.base64;
    }
    const nested =
      node.childWireframes ?? node.child_wireframes ?? node.children;
    const found = findFirstScreenshotBase64(nested);
    if (found) return found;
  }
  return null;
}

function parseMeta(event: Record<string, unknown>): MetaPayload | null {
  if (event.type !== REPLAY_TYPE_META || !isRecord(event.data)) {
    return null;
  }
  const d = event.data;
  const href = d.href;
  const width = d.width;
  const height = d.height;
  const ts = event.timestamp;
  if (
    typeof href !== "string" ||
    href.length === 0 ||
    typeof width !== "number" ||
    typeof height !== "number" ||
    typeof ts !== "number" ||
    ts <= 0
  ) {
    return null;
  }
  if (width <= 0 || height <= 0) {
    return null;
  }
  return { href, width, height, timestamp: ts };
}

/**
 * Walks `snapshot_items` in order. Only a strict META (type 4) then full snapshot (type 2) with
 * a screenshot wireframe produces a pair. Any other event between META and that FULL drops
 * pending META (incremental type 3, custom/plugin events, etc.). A new META replaces pending.
 * A type-2 without a usable screenshot clears pending without pairing.
 */
export function extractHeatmapScreenshots(
  parsed: ParsedMessageData,
): HeatmapScreenshotPayload[] {
  const out: HeatmapScreenshotPayload[] = [];
  let pendingMeta: MetaPayload | null = null;

  for (const ev of parsed.events) {
    if (!isRecord(ev)) continue;

    const metaNow = parseMeta(ev);
    if (metaNow) {
      pendingMeta = metaNow;
      continue;
    }

    const t = ev.type;
    if (t === REPLAY_TYPE_FULL_SNAPSHOT) {
      const data = ev.data;
      if (!isRecord(data) || data.wireframes === undefined) {
        pendingMeta = null;
        continue;
      }
      const screenshotBase64 = findFirstScreenshotBase64(data.wireframes);
      if (screenshotBase64 && pendingMeta) {
        out.push({ meta: pendingMeta, base64: screenshotBase64 });
      }
      pendingMeta = null;
      continue;
    }

    pendingMeta = null;
  }

  return out;
}
