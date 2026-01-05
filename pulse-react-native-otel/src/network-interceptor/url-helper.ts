/**
 * URL parsing helper for React Native
 * Provides consistent URL parsing across all RN versions
 *
 * React Native < 0.80 throws errors for URL properties like hostname, protocol, etc.
 * This helper uses the same regex approach as RN 0.80+ to ensure compatibility.
 *
 * Based on: https://github.com/facebook/react-native/blob/v0.80.0/packages/react-native/Libraries/Blob/URL.js
 */

export class SearchParams {
  private params: Map<string, string> = new Map();

  constructor(search: string) {
    if (!search || typeof search !== 'string') {
      return;
    }

    // Remove leading '?' if present
    const queryString = search.startsWith('?') ? search.slice(1) : search;

    if (!queryString) {
      return;
    }

    try {
      // Split by '&' to get individual parameters
      const pairs = queryString.split('&');

      for (const pair of pairs) {
        if (!pair) continue;

        // Split by '=' to get key and value
        const equalIndex = pair.indexOf('=');
        if (equalIndex === -1) {
          // Parameter without value
          const key = decodeURIComponent(pair);
          if (key) {
            this.params.set(key, '');
          }
        } else {
          const key = decodeURIComponent(pair.substring(0, equalIndex));
          const value = decodeURIComponent(pair.substring(equalIndex + 1));
          if (key) {
            this.params.set(key, value);
          }
        }
      }
    } catch (e) {
      // If decoding fails, params remain empty
      console.warn('[Pulse] Query parameter parsing failed:', e);
    }
  }

  get(name: string): string | null {
    return this.params.has(name) ? this.params.get(name)! : null;
  }

  has(name: string): boolean {
    return this.params.has(name);
  }

  keys(): string[] {
    return Array.from(this.params.keys());
  }

  values(): string[] {
    return Array.from(this.params.values());
  }
}

export interface ParsedUrl {
  protocol: string;
  hostname: string;
  host: string;
  port: string;
  pathname: string;
  search: string;
  hash: string;
  href: string;
  searchParams: SearchParams;
}

/**
 * Safely extract a regex match group
 * Returns empty string if match fails or group is undefined
 */
function safeMatch(
  text: string,
  regex: RegExp,
  groupIndex: number = 1
): string {
  try {
    const match = text.match(regex);
    return match && match[groupIndex] ? match[groupIndex] : '';
  } catch (e) {
    return '';
  }
}

/**
 * Parse a URL string into its components
 * Returns null if URL is invalid or cannot be parsed
 *
 * This function is defensive and will return empty strings for any component
 * that cannot be extracted, rather than throwing errors.
 */
export function parseUrl(url: string): ParsedUrl | null {
  // Validate input
  if (!url || typeof url !== 'string' || url.trim().length === 0) {
    return null;
  }

  // Ensure URL has a protocol (required for proper parsing)
  if (!url.match(/^[a-zA-Z][a-zA-Z\d+\-.]*:/)) {
    // Not a valid absolute URL
    return null;
  }

  try {
    // Protocol - e.g., "https:"
    const protocol = safeMatch(url, /^([a-zA-Z][a-zA-Z\d+\-.]*):/);
    const protocolWithColon = protocol ? `${protocol}:` : '';

    // Hostname - e.g., "example.com"
    const hostname = safeMatch(url, /^https?:\/\/(?:[^@]+@)?([^:/?#]+)/);

    // Port - e.g., "8080"
    const port = safeMatch(url, /:(\d+)(?=[/?#]|$)/);

    // Host (hostname + port if present) - e.g., "example.com:8080"
    const host = hostname ? (port ? `${hostname}:${port}` : hostname) : '';

    // Pathname - e.g., "/api/users"
    const pathMatch = url.match(/https?:\/\/[^/]+(\/[^?#]*)?/);
    const pathname = pathMatch && pathMatch[1] ? pathMatch[1] : '/';

    // Search - e.g., "?id=123"
    const searchContent = safeMatch(url, /\?([^#]*)/);
    const search = searchContent ? `?${searchContent}` : '';

    // Hash - e.g., "#section"
    const hashContent = safeMatch(url, /#([^/]*)/);
    const hash = hashContent ? `#${hashContent}` : '';

    // Create SearchParams instance for query parameters
    const searchParams = new SearchParams(search);

    return {
      protocol: protocolWithColon,
      hostname: hostname,
      host: host,
      port: port,
      pathname: pathname,
      search: search,
      hash: hash,
      href: url,
      searchParams: searchParams,
    };
  } catch (e) {
    // Any unexpected error during parsing - return null
    console.warn('[Pulse] URL parsing failed:', e);
    return null;
  }
}

/**
 * Extract HTTP attributes from a URL string for OpenTelemetry span attributes
 */
export function extractHttpAttributes(url: string): {
  'http.scheme'?: string;
  'http.host'?: string;
  'http.target'?: string;
  'net.peer.name'?: string;
  'net.peer.port'?: number;
} {
  try {
    const parsed = parseUrl(url);
    if (!parsed) {
      return {};
    }

    const attributes: {
      'http.scheme'?: string;
      'http.host'?: string;
      'http.target'?: string;
      'net.peer.name'?: string;
      'net.peer.port'?: number;
    } = {};

    // http.scheme - protocol without colon (e.g., "https")
    if (parsed.protocol && parsed.protocol.length > 0) {
      attributes['http.scheme'] = parsed.protocol.replace(':', '');
    }

    // http.host and net.peer.name - hostname (e.g., "api.example.com")
    if (parsed.hostname && parsed.hostname.length > 0) {
      attributes['http.host'] = parsed.hostname;
      attributes['net.peer.name'] = parsed.hostname;
    }

    // http.target - pathname + search (e.g., "/api/users?id=123")
    if (parsed.pathname && parsed.pathname.length > 0) {
      attributes['http.target'] = parsed.pathname + parsed.search;
    }

    // net.peer.port - port number (e.g., 8080)
    if (parsed.port && parsed.port.length > 0) {
      const portNum = parseInt(parsed.port, 10);
      if (!isNaN(portNum) && portNum > 0 && portNum <= 65535) {
        attributes['net.peer.port'] = portNum;
      }
    }

    return attributes;
  } catch (e) {
    // Absolutely ensure this function never throws
    console.warn('[Pulse] Failed to extract HTTP attributes from URL:', e);
    return {};
  }
}
