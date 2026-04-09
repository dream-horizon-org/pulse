/**
 * Heatmap metadata may list capture manifest URLs (often `.json`) that return JSON
 * with base64 image bytes instead of a raw image response.
 */

export type HeatmapCaptureImageWire = {
  encoding?: string;
  mimeType?: string;
  data?: string;
};

export type HeatmapCaptureManifestWire = {
  schemaVersion?: number;
  image?: HeatmapCaptureImageWire;
};

function isRecord(v: unknown): v is Record<string, unknown> {
  return v != null && typeof v === "object" && !Array.isArray(v);
}

function dataUrlFromCaptureJson(body: unknown): string | null {
  if (!isRecord(body)) return null;
  const img = body.image;
  if (!isRecord(img)) return null;
  const data = img.data;
  if (typeof data !== "string" || data.length === 0) return null;
  let mime = "image/png";
  const mt = img.mimeType;
  if (typeof mt === "string" && mt.trim().length > 0) {
    mime = mt.trim();
  }
  return `data:${mime};base64,${data.replace(/\s/g, "")}`;
}

/**
 * Turn a heatmap screenshot href into something `<img src>` can render:
 * - `data:` / `blob:` unchanged
 * - JSON capture manifest → `data:&lt;mime&gt;;base64,...`
 * - `image/*` response → `blob:` URL (caller must revoke)
 * - otherwise returns original URL (direct image/CDN)
 */
export async function resolveHeatmapScreenshotUrl(url: string): Promise<string> {
  const trimmed = url.trim();
  if (!trimmed) {
    throw new Error("resolveHeatmapScreenshotUrl: empty url");
  }
  if (trimmed.startsWith("data:") || trimmed.startsWith("blob:")) {
    return trimmed;
  }

  const res = await fetch(trimmed, { credentials: "omit", mode: "cors" });
  if (!res.ok) {
    return trimmed;
  }

  const ctRaw = res.headers.get("content-type") ?? "";
  const ct = ctRaw.split(";")[0]?.trim() ?? "";

  if (ct.startsWith("image/")) {
    const blob = await res.blob();
    return URL.createObjectURL(blob);
  }

  const text = await res.text();
  try {
    const body = JSON.parse(text) as HeatmapCaptureManifestWire;
    const dataUrl = dataUrlFromCaptureJson(body);
    if (dataUrl) return dataUrl;
  } catch {
    /* not JSON or unexpected shape */
  }

  return trimmed;
}
