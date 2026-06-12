export function nowMs(): number {
  const perf = global.performance;
  if (perf && typeof perf.now === 'function') return perf.now();
  return Date.now();
}

export function getAbsoluteUrl(url: string, baseUrl?: string): string {
  // if it looks like an absolute url do nothing
  if (url.indexOf('https://') === 0 || url.indexOf('http://') === 0) return url;

  try {
    const absoluteUrl = new URL(url, baseUrl).href;

    // if a trailing slash has been added inadvertently remove it
    if (!url.endsWith('/') && absoluteUrl.endsWith('/')) {
      return absoluteUrl.slice(0, -1);
    }

    return absoluteUrl;
  } catch {
    // not a valid URL for some reason - simply return it
    return url;
  }
}

export function extractErrorDetails(error: Error): {
  message: string;
  stackTrace: string;
  errorType: string;
} {
  const message = error.message || 'Unknown error';
  const stackTrace = error.stack || '';
  const errorType = error.name || '';

  return { message, stackTrace, errorType };
}
