import type { SessionDetailData } from "./mockSessionDetail";

/** Known snapshot_source values from mobile/web SDKs and ingestion defaults. */
const PLATFORM_PATTERN = /\b(Android|iOS|Web|mobile)\b/i;

/**
 * Strip non-printable bytes (e.g. ClickHouse length-prefixed aggregate blobs) and
 * extract a known platform token from snapshots-source.snapshotSource.
 */
export function normalizeSnapshotSource(
  raw: string | null | undefined,
): string | null {
  if (raw == null) return null;
  const trimmed = raw.trim();
  if (!trimmed) return null;

  const printable = trimmed.replace(/[^\x20-\x7E]/g, "");
  const match = printable.match(PLATFORM_PATTERN);
  if (match) return match[1].toLowerCase();

  const collapsed = printable.replace(/\s+/g, "");
  if (!collapsed) return null;
  return collapsed.toLowerCase();
}

/**
 * Map normalized snapshot source to session detail platform labels for device chrome.
 */
export function toDisplayPlatform(
  raw: string | null | undefined,
): SessionDetailData["platform"] | undefined {
  const normalized = normalizeSnapshotSource(raw);
  if (!normalized) return undefined;

  if (normalized === "android" || normalized === "mobile") return "Android";
  if (normalized === "ios") return "iOS";
  if (normalized === "web") return "Web";

  return undefined;
}
