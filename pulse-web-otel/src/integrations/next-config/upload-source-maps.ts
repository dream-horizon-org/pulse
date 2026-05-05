/**
 * Upload source map files to the Pulse symbolication endpoint.
 * Runs in Node.js at build time — uses native fetch + FormData (Node 18+).
 */

export interface SourceMapFile {
  /** Basename of the map file, e.g. "main.js.map" */
  fileName: string;
  /** Raw source-map content (always a string — JSON or VLQ-encoded). */
  content: string;
}

export interface SourceMapUploadOptions {
  apiKey: string;
  serverUrl: string;
  appVersion: string;
  bundleId: string;
  dryRun: boolean;
}

interface UploadMetadata {
  type: string;
  fileName: string;
  appVersion: string;
  versionCode: string;
  platform: string;
  bundleId: string;
}

/**
 * Upload one or more source map files to `POST /v1/symbolicate/file/upload`.
 * Returns true on success, false on failure (never throws).
 */
export async function uploadSourceMaps(
  files: SourceMapFile[],
  options: SourceMapUploadOptions,
): Promise<boolean> {
  if (files.length === 0) {
    return true;
  }

  if (options.dryRun) {
    console.log(
      `[Pulse] dry-run: would upload ${files.length} source map(s) for v${options.appVersion}`,
    );
    return true;
  }

  const metadata: UploadMetadata[] = files.map((f) => ({
    type: "js",
    fileName: f.fileName,
    appVersion: options.appVersion,
    versionCode: options.appVersion,
    platform: "web",
    bundleId: options.bundleId,
  }));

  const form = new FormData();
  form.append("metadata", JSON.stringify(metadata));

  for (const file of files) {
    const blob = new Blob([file.content], { type: "application/json" });
    form.append("fileContent", blob, file.fileName);
  }

  const url = `${options.serverUrl.replace(/\/$/, "")}/v1/symbolicate/file/upload`;

  try {
    const response = await fetch(url, {
      method: "POST",
      headers: { "X-API-KEY": options.apiKey },
      body: form,
    });

    if (!response.ok) {
      const body = await response.text().catch(() => "");
      console.error(
        `[Pulse] Source map upload failed: HTTP ${response.status} — ${body.slice(0, 200)}`,
      );
      return false;
    }

    console.log(
      `[Pulse] Uploaded ${files.length} source map(s) (v${options.appVersion})`,
    );
    return true;
  } catch (err: unknown) {
    console.error(
      "[Pulse] Source map upload error:",
      err instanceof Error ? err.message : String(err),
    );
    return false;
  }
}
