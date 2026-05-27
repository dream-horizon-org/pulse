/**
 * Normalizes `location.pathname` for `screen.name` heuristics.
 *
 * Handles:
 * - Percent-encoded segments (`%20`, double-encoded `%2520`, `+` as space)
 * - Analytics-style routes that embed a full screen path in the final segment after
 *   `/screens/` (e.g. `/projects/:id/screens/%2Fprojects%2F...%2FMy%2520Screen`)
 */

/** Final path segment after `/screens/` that encodes an absolute screen path. */
const EMBEDDED_SCREEN_PATH = /\/screens\/([^/]+)$/;

/**
 * Decode percent-encoding until stable (handles double-encoded values).
 */
export function decodeUriComponentFully(value: string, maxRounds = 5): string {
  let current = value;
  for (let round = 0; round < maxRounds; round++) {
    try {
      const next = decodeURIComponent(current.replace(/\+/g, " "));
      if (next === current) {
        return current;
      }
      current = next;
    } catch {
      return current;
    }
  }
  return current;
}

/**
 * When the URL ends with `/screens/<encoded>`, and decoding yields an absolute path,
 * use that path as the logical screen name (common in observability dashboards).
 */
export function unwrapEmbeddedScreenPath(pathname: string): string {
  const match = pathname.match(EMBEDDED_SCREEN_PATH);
  const encoded = match?.[1];
  if (!encoded) {
    return pathname;
  }
  const decoded = decodeUriComponentFully(encoded);
  return decoded.startsWith("/") ? decoded : pathname;
}

/** Decode + unwrap embedded screen paths for `screen.name` resolution. */
export function normalizeScreenPathname(pathname: string): string {
  const unwrapped = unwrapEmbeddedScreenPath(pathname);
  return decodeUriComponentFully(unwrapped) || "/";
}
