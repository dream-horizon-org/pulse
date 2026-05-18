/**
 * Reads `email` from JWT payload without verifying signature (token already issued by Pulse).
 */

export function decodeAccessTokenEmail(
  accessToken: string,
): string | undefined {
  try {
    const parts = accessToken.split(".");
    if (parts.length < 2) return undefined;
    const payload = parts[1];
    const pad =
      payload.length % 4 === 2 ? "==" : payload.length % 4 === 3 ? "=" : "";
    const json = Buffer.from(
      payload.replace(/-/g, "+").replace(/_/g, "/") + pad,
      "base64",
    ).toString("utf8");
    const obj = JSON.parse(json) as { email?: unknown };
    return typeof obj.email === "string" && obj.email.trim()
      ? obj.email.trim()
      : undefined;
  } catch {
    return undefined;
  }
}
