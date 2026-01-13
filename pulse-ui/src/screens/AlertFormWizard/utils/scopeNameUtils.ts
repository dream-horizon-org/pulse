/**
 * Utilities for parsing and formatting network_api scope names
 * 
 * Network API scope names use format: {method}_{url}
 * e.g., "get_https://www.fancode.com/graphql"
 */

export interface ParsedNetworkScopeName {
  method: string;
  url: string;
}

/**
 * Parse a network_api scope name into method and URL components
 * Format: {method}_{url} (e.g., "get_https://www.fancode.com/graphql")
 */
export function parseNetworkApiScopeName(scopeName: string): ParsedNetworkScopeName | null {
  if (!scopeName || !scopeName.includes("_")) {
    return null;
  }

  const underscoreIdx = scopeName.indexOf("_");
  const method = scopeName.substring(0, underscoreIdx);
  const url = scopeName.substring(underscoreIdx + 1);

  // Validate that the method looks like an HTTP method
  const validMethods = ["get", "post", "put", "patch", "delete", "head", "options", "connect", "trace"];
  if (!validMethods.includes(method.toLowerCase())) {
    return null;
  }

  return {
    method: method.toLowerCase(),
    url,
  };
}

/**
 * Format a network_api scope name for display
 * Converts "get_https://www.fancode.com/graphql" to "GET https://www.fancode.com/graphql"
 */
export function formatNetworkApiScopeName(scopeName: string): string {
  const parsed = parseNetworkApiScopeName(scopeName);
  if (!parsed) {
    return scopeName;
  }
  return `${parsed.method.toUpperCase()} ${parsed.url}`;
}

/**
 * Format a network_api scope name for compact display (truncated URL)
 * Converts "get_https://www.fancode.com/v1/contests/live" to "GET .../contests/live"
 */
export function formatNetworkApiScopeNameCompact(scopeName: string, maxUrlLength: number = 30): string {
  const parsed = parseNetworkApiScopeName(scopeName);
  if (!parsed) {
    return scopeName.length > maxUrlLength 
      ? `${scopeName.substring(0, maxUrlLength)}...` 
      : scopeName;
  }

  const { method, url } = parsed;
  
  // Extract just the path from the URL for compact display
  try {
    const urlObj = new URL(url);
    const path = urlObj.pathname;
    const displayPath = path.length > maxUrlLength 
      ? `...${path.slice(-maxUrlLength)}`
      : path;
    return `${method.toUpperCase()} ${displayPath}`;
  } catch {
    // If URL parsing fails, just truncate
    const displayUrl = url.length > maxUrlLength 
      ? `...${url.slice(-maxUrlLength)}`
      : url;
    return `${method.toUpperCase()} ${displayUrl}`;
  }
}

/**
 * Check if a scope name is a network_api format
 */
export function isNetworkApiScopeName(scopeName: string): boolean {
  return parseNetworkApiScopeName(scopeName) !== null;
}

/**
 * Create a network_api scope name from method and URL
 */
export function createNetworkApiScopeName(method: string, url: string): string {
  return `${method.toLowerCase()}_${url}`;
}

