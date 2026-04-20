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
  /** Present on ingestion-written capture JSON; drives per-screenshot UI labels. */
  appVersion?: string | null;
  /** Viewport bucket when the capture was taken; drives per-screenshot phone frame. */
  breakpoint?: string | null;
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

function captureAppVersionFromManifest(body: unknown): string | null {
  if (!isRecord(body)) return null;
  const v = body.appVersion;
  if (typeof v !== "string") return null;
  const t = v.trim();
  return t.length > 0 ? t : null;
}

function captureBreakpointFromManifest(body: unknown): string | null {
  if (!isRecord(body)) return null;
  const v = body.breakpoint;
  if (typeof v !== "string") return null;
  const t = v.trim();
  return t.length > 0 ? t : null;
}

export type ResolveHeatmapScreenshotResult = {
  displayUrl: string;
  /** From JSON capture manifest when present and non-empty. */
  captureAppVersion: string | null;
  /** From JSON capture manifest when present and non-empty. */
  captureBreakpoint: string | null;
};

/**
 * Fetches a heatmap screenshot href and returns a display URL plus optional `appVersion`
 * from ingestion JSON manifests.
 */
export async function resolveHeatmapScreenshot(
  url: string,
): Promise<ResolveHeatmapScreenshotResult> {
  const trimmed = url.trim();
  if (!trimmed) {
    throw new Error("resolveHeatmapScreenshot: empty url");
  }
  if (trimmed.startsWith("data:") || trimmed.startsWith("blob:")) {
    return {
      displayUrl: trimmed,
      captureAppVersion: null,
      captureBreakpoint: null,
    };
  }

  const res = await fetch(trimmed, { credentials: "omit", mode: "cors" });
  if (!res.ok) {
    return {
      displayUrl: trimmed,
      captureAppVersion: null,
      captureBreakpoint: null,
    };
  }

  const ctRaw = res.headers.get("content-type") ?? "";
  const ct = ctRaw.split(";")[0]?.trim() ?? "";

  if (ct.startsWith("image/")) {
    const blob = await res.blob();
    return {
      displayUrl: URL.createObjectURL(blob),
      captureAppVersion: null,
      captureBreakpoint: null,
    };
  }

  const text = await res.text();
  try {
    const body = JSON.parse(text) as unknown;
    const captureAppVersion = captureAppVersionFromManifest(body);
    const captureBreakpoint = captureBreakpointFromManifest(body);
    const dataUrl = dataUrlFromCaptureJson(body);
    if (dataUrl) {
      return { displayUrl: dataUrl, captureAppVersion, captureBreakpoint };
    }
    return { displayUrl: trimmed, captureAppVersion, captureBreakpoint };
  } catch {
    /* not JSON or unexpected shape */
  }

  return {
    displayUrl: trimmed,
    captureAppVersion: null,
    captureBreakpoint: null,
  };
}

/**
 * Turn a heatmap screenshot href into something `<img src>` can render:
 * - `data:` / `blob:` unchanged
 * - JSON capture manifest → `data:&lt;mime&gt;;base64,...`
 * - `image/*` response → `blob:` URL (caller must revoke)
 * - otherwise returns original URL (direct image/CDN)
 */
export async function resolveHeatmapScreenshotUrl(url: string): Promise<string> {
  const { displayUrl } = await resolveHeatmapScreenshot(url);
  return displayUrl;
}
