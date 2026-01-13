/**
 * Utility functions for encoding/decoding network API IDs
 * ID format: base64(url) - URL-safe encoding
 */

/**
 * Encodes url into a URL-safe ID
 * @param url - API endpoint URL
 * @returns Encoded ID string
 */
export function encodeNetworkId(url: string): string {
  return btoa(encodeURIComponent(url))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=/g, "");
}

/**
 * Decodes an ID back to url
 * @param id - Encoded ID string
 * @returns Object with url, or null if decoding fails
 */
export function decodeNetworkId(id: string): { url: string } | null {
  try {
    // Reverse the URL-safe base64 encoding
    const base64 = id.replace(/-/g, "+").replace(/_/g, "/");
    const decoded = decodeURIComponent(atob(base64));

    // Handle backward compatibility with old format (method|url)
    // If the decoded string contains a pipe that's not part of a URL, extract the URL
    if (decoded.includes("|")) {
      const pipeIndex = decoded.indexOf("|");
      const beforePipe = decoded.substring(0, pipeIndex);
      // Check if the part before pipe looks like an HTTP method (all uppercase, short)
      if (
        beforePipe.length <= 10 &&
        /^[A-Z]+$/.test(beforePipe) &&
        ["GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "CONNECT", "TRACE", "UNKNOWN"].includes(beforePipe)
      ) {
        // Old format detected - extract URL after the pipe
        const url = decoded.substring(pipeIndex + 1);
        return { url };
      }
    }

    return { url: decoded };
  } catch (error) {
    console.error("Failed to decode network ID:", error);
    return null;
  }
}
