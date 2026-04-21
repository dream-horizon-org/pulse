/**
 * Parses a single Content-Length header value
 * Returns undefined if missing, non-numeric, not finite, or negative.
 */
export function parseContentLength(
  value: string | undefined | null
): number | undefined {
  if (value == null || value === '') {
    return undefined;
  }
  const trimmed = value.trim();
  if (trimmed === '') {
    return undefined;
  }
  const first = trimmed.split(/\s*,\s*/)[0] ?? trimmed;
  const n = Number(first);
  if (!Number.isFinite(n) || n < 0 || !Number.isInteger(n)) {
    return undefined;
  }
  return n;
}

/** Case-insensitive lookup in a header name -> value map (XHR / captured headers). */
export function getHeaderCaseInsensitive(
  headers: Record<string, string> | undefined,
  canonicalName: string
): string | undefined {
  if (!headers) {
    return undefined;
  }
  const lower = canonicalName.toLowerCase();
  for (const [k, v] of Object.entries(headers)) {
    if (k.toLowerCase() === lower) {
      return v;
    }
  }
  return undefined;
}

/** This is used to approximate request body size for typical JSON/string/blob payloads.*/
export function estimateRequestBodyByteLength(
  body?: Document | XMLHttpRequestBodyInit | null
): number | undefined {
  if (body == null) {
    return undefined;
  }
  if (typeof body === 'string') {
    if (body.length === 0) {
      return undefined;
    }
    try {
      return new TextEncoder().encode(body).length;
    } catch {
      return undefined;
    }
  }
  if (typeof Blob !== 'undefined' && body instanceof Blob) {
    const n = body.size;
    return n > 0 ? n : undefined;
  }
  if (body instanceof ArrayBuffer) {
    const n = body.byteLength;
    return n > 0 ? n : undefined;
  }
  if (ArrayBuffer.isView(body)) {
    const n = body.byteLength;
    return n > 0 ? n : undefined;
  }
  return undefined;
}
