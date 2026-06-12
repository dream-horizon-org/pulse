/**
 * Utility functions for encoding/decoding network API IDs
 * ID format: base64(url) - URL-safe encoding
 */

/**
 * Encodes url (and optional operation details) into a URL-safe ID
 * @param url - API endpoint URL
 * @param operationName - Optional GraphQL operation name
 * @param operationType - Optional GraphQL operation type
 * @returns Encoded ID string
 */
export function encodeNetworkId(
  url: string,
  operationName?: string,
  operationType?: string
): string {
  const normalizedOperationName = operationName?.trim();
  const normalizedOperationType = operationType?.trim();
  const payload =
    normalizedOperationName && normalizedOperationName.length > 0
      ? [
          url,
          normalizedOperationName,
          normalizedOperationType && normalizedOperationType.length > 0
            ? normalizedOperationType
            : undefined,
        ]
          .filter(Boolean)
          .join("||")
      : url;
  return btoa(encodeURIComponent(payload))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=/g, "");
}

/**
 * Decodes an ID back to url and optional operation details
 * @param id - Encoded ID string
 * @returns Object with url and optional operationName/operationType, or null if decoding fails
 */
export function decodeNetworkId(
  id: string
): { url: string; operationName?: string; operationType?: string } | null {
  try {
    // Reverse the URL-safe base64 encoding
    const base64 = id.replace(/-/g, "+").replace(/_/g, "/");
    const decoded = decodeURIComponent(atob(base64));

    // New format: url||operationName||operationType
    if (decoded.includes("||")) {
      const parts = decoded.split("||");
      const url = parts[0];
      const operationName = parts[1] || undefined;
      const operationType = parts[2] || undefined;
      return { url, operationName, operationType };
    }

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
