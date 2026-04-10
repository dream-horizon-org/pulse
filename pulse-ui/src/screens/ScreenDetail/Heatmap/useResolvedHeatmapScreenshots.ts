import { useEffect, useMemo, useState } from "react";
import { resolveHeatmapScreenshot } from "./heatmapCaptureResolve";

export type UseResolvedHeatmapScreenshotsResult = {
  /** Displayable URLs for `<img src>` (data:, blob:, or https:). */
  displayUrls: string[];
  /**
   * Per-URL `appVersion` from JSON capture manifests; null when absent, not JSON, or raw image.
   * Same order and length as `displayUrls` after a successful resolve.
   */
  captureAppVersions: (string | null)[];
  /**
   * Per-URL `breakpoint` from JSON capture manifests; null when absent, not JSON, or raw image.
   */
  captureBreakpoints: (string | null)[];
  loading: boolean;
  /** Stable key when `screenshot_urls` from API change (for carousel reset). */
  sourceKey: string;
};

function sourcesKey(rawUrls: string[]): string {
  return JSON.stringify(rawUrls);
}

/**
 * Fetches each screenshot href. JSON capture manifests (base64 `image.data`) become data URLs;
 * raw `image/*` responses become blob URLs (revoked on change/unmount).
 */
export function useResolvedHeatmapScreenshots(
  rawUrls: string[],
): UseResolvedHeatmapScreenshotsResult {
  const key = useMemo(() => sourcesKey(rawUrls), [rawUrls]);

  const [displayUrls, setDisplayUrls] = useState<string[]>([]);
  const [captureAppVersions, setCaptureAppVersions] = useState<
    (string | null)[]
  >([]);
  const [captureBreakpoints, setCaptureBreakpoints] = useState<
    (string | null)[]
  >([]);
  const [loading, setLoading] = useState(() => rawUrls.length > 0);

  useEffect(() => {
    let urls: string[] = [];
    try {
      const parsed = JSON.parse(key) as unknown;
      if (Array.isArray(parsed)) urls = parsed.filter((u) => typeof u === "string");
    } catch {
      urls = [];
    }

    if (urls.length === 0) {
      setDisplayUrls([]);
      setCaptureAppVersions([]);
      setCaptureBreakpoints([]);
      setLoading(false);
      return undefined;
    }

    let dead = false;
    const blobUrls: string[] = [];

    setLoading(true);
    setDisplayUrls([]);
    setCaptureAppVersions([]);
    setCaptureBreakpoints([]);

    void (async () => {
      try {
        const out: string[] = [];
        const versions: (string | null)[] = [];
        const breakpoints: (string | null)[] = [];
        for (const u of urls) {
          const {
            displayUrl,
            captureAppVersion,
            captureBreakpoint,
          } = await resolveHeatmapScreenshot(u);
          if (dead) {
            if (displayUrl.startsWith("blob:")) URL.revokeObjectURL(displayUrl);
            return;
          }
          if (displayUrl.startsWith("blob:")) blobUrls.push(displayUrl);
          out.push(displayUrl);
          versions.push(captureAppVersion);
          breakpoints.push(captureBreakpoint);
        }

        if (dead) {
          blobUrls.forEach((b) => URL.revokeObjectURL(b));
          return;
        }

        setDisplayUrls(out);
        setCaptureAppVersions(versions);
        setCaptureBreakpoints(breakpoints);
        setLoading(false);
      } catch {
        if (dead) {
          blobUrls.forEach((b) => URL.revokeObjectURL(b));
          return;
        }
        blobUrls.forEach((b) => URL.revokeObjectURL(b));
        setDisplayUrls([...urls]);
        setCaptureAppVersions(urls.map(() => null));
        setCaptureBreakpoints(urls.map(() => null));
        setLoading(false);
      }
    })();

    return () => {
      dead = true;
      blobUrls.forEach((b) => URL.revokeObjectURL(b));
      blobUrls.length = 0;
    };
  }, [key]);

  return {
    displayUrls,
    captureAppVersions,
    captureBreakpoints,
    loading,
    sourceKey: key,
  };
}
