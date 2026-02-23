/**
 * Normalizes header name according to OpenTelemetry HTTP semantic conventions:
 * - Lowercase
 * - Dashes replaced by underscores
 * - Reference: https://opentelemetry.io/docs/specs/semconv/registry/attributes/http/
 * @example
 * normalizeHeaderName('Content-Type') => 'content_type'
 * normalizeHeaderName('X-Request-ID') => 'x_request_id'
 */
export function normalizeHeaderName(headerName: string): string {
  return headerName.toLowerCase().replace(/-/g, '_');
}

/**
 * Checks if a header should be captured based on configuration.
 */
export function shouldCaptureHeader(
  headerName: string,
  headerList: string[]
): boolean {
  if (headerList.length === 0) return false;
  // Case-insensitive comparison
  return headerList.some(
    (configHeader) => configHeader.toLowerCase() === headerName.toLowerCase()
  );
}
