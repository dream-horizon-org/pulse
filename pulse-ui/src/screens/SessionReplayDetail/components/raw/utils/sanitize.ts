/**
 * Sanitize raw session event data for safe display (mask PII, query params, path IDs).
 */

const MAX_DISPLAY_LENGTH = 200;
const UUID_REGEX =
  /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi;
const NUMERIC_ID_REGEX = /\/\d{5,}\b/g;

/**
 * Sanitize a URL for display: strip query string, mask path params that look like IDs.
 */
export function sanitizeUrl(url: string): string {
  if (!url || typeof url !== "string") return "";
  const isAbsolute = /^https?:\/\//i.test(url);
  try {
    const parsed = new URL(url, isAbsolute ? undefined : "https://_");
    let path = parsed.pathname;
    path = path.replace(UUID_REGEX, ":id");
    path = path.replace(NUMERIC_ID_REGEX, "/:id");
    const query = parsed.search ? "?***" : "";
    const origin =
      isAbsolute && parsed.origin !== "https://_" ? parsed.origin : "";
    return `${origin}${path}${query}`;
  } catch {
    let path = url.split("?")[0] ?? url;
    path = path.replace(UUID_REGEX, ":id");
    path = path.replace(NUMERIC_ID_REGEX, "/:id");
    return path;
  }
}

/**
 * Sanitize free-form text: truncate and strip control characters.
 */
export function sanitizeDisplayText(
  text: string,
  maxLength: number = MAX_DISPLAY_LENGTH,
): string {
  if (text == null || typeof text !== "string") return "";
  const trimmed = text.replace(/[\x00-\x1f\x7f]/g, "").trim();
  return trimmed.length > maxLength
    ? `${trimmed.slice(0, maxLength)}…`
    : trimmed;
}

/**
 * Sanitize a path-like string (e.g. navigation path): mask segment IDs.
 */
export function sanitizePath(path: string): string {
  if (!path || typeof path !== "string") return "";
  let out = path.replace(UUID_REGEX, ":id").replace(NUMERIC_ID_REGEX, "/:id");
  return sanitizeDisplayText(out, MAX_DISPLAY_LENGTH);
}
