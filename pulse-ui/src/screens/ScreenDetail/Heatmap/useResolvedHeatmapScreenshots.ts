import { useEffect, useMemo, useState } from "react";
import { resolveHeatmapScreenshotUrl } from "./heatmapCaptureResolve";

export type UseResolvedHeatmapScreenshotsResult = {
  /** Displayable URLs for `<img src>` (data:, blob:, or https:). */
  displayUrls: string[];
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
      setLoading(false);
      return undefined;
    }

    let dead = false;
    const blobUrls: string[] = [];

    setLoading(true);
    setDisplayUrls([]);

    void (async () => {
      try {
        const out: string[] = [];
        for (const u of urls) {
          const resolved = await resolveHeatmapScreenshotUrl(u);
          if (dead) {
            if (resolved.startsWith("blob:")) URL.revokeObjectURL(resolved);
            return;
          }
          if (resolved.startsWith("blob:")) blobUrls.push(resolved);
          out.push(resolved);
        }

        if (dead) {
          blobUrls.forEach((b) => URL.revokeObjectURL(b));
          return;
        }

        setDisplayUrls(out);
        setLoading(false);
      } catch {
        if (dead) {
          blobUrls.forEach((b) => URL.revokeObjectURL(b));
          return;
        }
        blobUrls.forEach((b) => URL.revokeObjectURL(b));
        setDisplayUrls([...urls]);
        setLoading(false);
      }
    })();

    return () => {
      dead = true;
      blobUrls.forEach((b) => URL.revokeObjectURL(b));
      blobUrls.length = 0;
    };
  }, [key]);

  return { displayUrls, loading, sourceKey: key };
}
