/**
 * Decode a base64 data URL to a Blob and return an object URL for rendering.
 * Caller must revoke the returned URL with URL.revokeObjectURL when done to avoid leaks.
 */
export function decodeDataUrlToBlobUrl(dataUrl: string): string | null {
  if (!dataUrl || !dataUrl.startsWith("data:")) return null;
  const match = dataUrl.match(/^data:([^;]+);base64,(.+)$/);
  if (!match) return null;
  const mime = match[1].trim();
  const base64 = match[2].replace(/\s/g, "");
  try {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    const blob = new Blob([bytes], { type: mime });
    return URL.createObjectURL(blob);
  } catch {
    return null;
  }
}

export function isDataUrl(url: string): boolean {
  return typeof url === "string" && url.startsWith("data:");
}
