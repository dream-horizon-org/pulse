/**
 * withPulseConfig — Next.js build-config wrapper.
 *
 * Wraps your next.config.js to:
 *  1. Enable productionBrowserSourceMaps so .map files are emitted.
 *  2. Register a webpack plugin that uploads .js.map files to Pulse
 *     after each production browser build.
 *  3. Optionally removes .map files from the output so they are not
 *     publicly served (deleteAfterUpload, default true).
 *
 * Usage — next.config.js:
 *   const { withPulseConfig } = require("@dreamhorizon/pulse-web/next-config");
 *   module.exports = withPulseConfig({ ...yourNextConfig }, {
 *     apiKey: process.env.PULSE_API_KEY,
 *   });
 */

import { uploadSourceMaps } from "./upload-source-maps";
import type { SourceMapUploadOptions } from "./upload-source-maps";

// ─── Minimal webpack types ────────────────────────────────────────────────────
// We don't depend on webpack directly; Next.js bundles it. Using minimal
// structural types avoids a hard dependency while keeping full type safety.

interface WebpackAsset {
  source(): string | Buffer;
}

interface WebpackCompilation {
  assets: Record<string, WebpackAsset>;
}

interface WebpackCompilerHook {
  tapPromise(
    pluginName: string,
    handler: (compilation: WebpackCompilation) => Promise<void>,
  ): void;
}

interface WebpackCompiler {
  hooks: { emit: WebpackCompilerHook };
}

// ─── Next.js config types (minimal, no "next" import needed at build time) ────
type WebpackContext = {
  isServer: boolean;
  nextRuntime?: "nodejs" | "edge" | undefined;
  [key: string]: unknown;
};

type WebpackConfigFn = (
  config: Record<string, unknown>,
  context: WebpackContext,
) => Record<string, unknown>;

export interface PulseNextBaseConfig {
  webpack?: WebpackConfigFn;
  productionBrowserSourceMaps?: boolean;
  [key: string]: unknown;
}

// ─── Public API ───────────────────────────────────────────────────────────────

export interface PulseNextConfigOptions {
  /**
   * Your Pulse project API key. Required.
   */
  apiKey: string;

  /**
   * Base URL of the Pulse backend.
   * Defaults to `process.env.NEXT_PUBLIC_PULSE_SERVER_URL` or `http://localhost:8080`.
   */
  serverUrl?: string;

  /**
   * Version string that identifies this release — used to match source maps to
   * crash events. Defaults to `process.env.npm_package_version` (automatically
   * set by npm/yarn during build) or `process.env.NEXT_PUBLIC_APP_VERSION`.
   */
  appVersion?: string;

  /**
   * Bundle / application identifier.
   * Defaults to `process.env.npm_package_name`.
   */
  bundleId?: string;

  /**
   * Remove .map files from the webpack output after uploading so they are not
   * publicly served. Defaults to `true`.
   */
  deleteAfterUpload?: boolean;

  /**
   * Log upload intent but skip the actual HTTP request.
   * Useful for CI dry-run checks. Defaults to `false`.
   */
  dryRun?: boolean;

  /**
   * Skip all Pulse source-map handling (no upload, no map deletion).
   * Defaults to `true` when `NODE_ENV !== 'production'` so dev builds are
   * unaffected.
   */
  disabled?: boolean;
}

// ─── Webpack plugin ───────────────────────────────────────────────────────────

export class PulseSourceMapPlugin {
  readonly pluginName = "PulseSourceMapPlugin";

  constructor(private readonly opts: SourceMapUploadOptions & { deleteAfterUpload: boolean }) {}

  apply(compiler: WebpackCompiler): void {
    compiler.hooks.emit.tapPromise(
      this.pluginName,
      async (compilation: WebpackCompilation): Promise<void> => {
        const mapAssetNames = Object.keys(compilation.assets).filter((name) =>
          name.endsWith(".js.map"),
        );

        if (mapAssetNames.length === 0) return;

        const files = mapAssetNames.map((assetPath) => ({
          // Use the full relative asset path (not just basename) to avoid
          // collisions when two chunks in different subdirectories share a
          // filename (e.g. static/chunks/page.js.map in multiple routes).
          fileName: assetPath,
          content: String(compilation.assets[assetPath]!.source()),
        }));

        const uploadOk = await uploadSourceMaps(files, this.opts);

        // Only delete maps when upload succeeded — deleting on failure leaves
        // stacks permanently unreadable with no recovery path.
        if (uploadOk && this.opts.deleteAfterUpload) {
          for (const name of mapAssetNames) {
            delete (compilation.assets as Record<string, unknown>)[name];
          }
        }
      },
    );
  }
}

// ─── Main export ─────────────────────────────────────────────────────────────

/**
 * Wrap your Next.js config with Pulse source-map support.
 *
 * @param nextConfig  Your existing Next.js config object.
 * @param pulseOptions  Pulse upload options.
 * @returns Modified Next.js config.
 */
export function withPulseConfig(
  nextConfig: PulseNextBaseConfig,
  pulseOptions: PulseNextConfigOptions,
): PulseNextBaseConfig {
  const {
    disabled = process.env.NODE_ENV !== "production",
    dryRun = false,
    deleteAfterUpload = true,
    serverUrl =
      process.env["NEXT_PUBLIC_PULSE_SERVER_URL"] ?? "http://localhost:8080",
    apiKey,
    appVersion =
      process.env["npm_package_version"] ??
      process.env["NEXT_PUBLIC_APP_VERSION"] ??
      "unknown",
    bundleId = process.env["npm_package_name"] ?? "",
  } = pulseOptions;

  if (disabled) {
    return nextConfig;
  }

  const pluginOpts: SourceMapUploadOptions & { deleteAfterUpload: boolean } = {
    apiKey,
    serverUrl,
    appVersion,
    bundleId,
    dryRun,
    deleteAfterUpload,
  };

  const originalWebpack = nextConfig.webpack;

  return {
    ...nextConfig,
    // Ensure Next.js emits .map files for the browser build
    productionBrowserSourceMaps: true,
    webpack(
      config: Record<string, unknown>,
      context: WebpackContext,
    ): Record<string, unknown> {
      const resolved: Record<string, unknown> = originalWebpack
        ? originalWebpack(config, context)
        : config;

      // Only inject for the browser (client) build.
      // Next.js runs webpack twice — once for the server and once for the client.
      // Source maps from the server build aren't needed for browser crash reports.
      if (!context.isServer) {
        const existing = Array.isArray(resolved["plugins"])
          ? (resolved["plugins"] as unknown[])
          : [];
        resolved["plugins"] = [...existing, new PulseSourceMapPlugin(pluginOpts)];
      }

      return resolved;
    },
  };
}
