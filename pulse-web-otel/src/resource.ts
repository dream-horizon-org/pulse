// M1: Builds the OTEL Resource with 18 static browser attributes
// (platform, browser.name, screen.resolution, timezone, etc.).
// See: web-sdk-plan/v1/01-foundation/resource.md

import { Resource } from '@opentelemetry/resources';
import type { PulseWebConfig } from './config';
import { getOrCreateInstallationId } from './session';
import { SDK_VERSION } from './version';
import { parseUserAgent } from './utils/ua-parser';

export function extractProjectId(apiKey: string): string {
  // Format: '<project_name>-<random_id>_<api_key_portion>' → '<project_name>-<random_id>'
  // Everything before the last underscore is the project ID
  const lastUnderscoreIdx = apiKey.lastIndexOf('_');
  if (lastUnderscoreIdx > 0) {
    return apiKey.substring(0, lastUnderscoreIdx);
  }
  return apiKey;
}

function gcd(a: number, b: number): number {
  while (b !== 0) {
    const t = b;
    b = a % b;
    a = t;
  }
  return a;
}

export function computeAspectRatio(w: number, h: number): string {
  if (w === 0 || h === 0) return '0:0';
  const divisor = gcd(w, h);
  return `${w / divisor}:${h / divisor}`;
}

export function buildResource(config: PulseWebConfig): Resource {
  const parsedUA = parseUserAgent();
  const installationId = getOrCreateInstallationId();

  const attrs: Record<string, string | number | boolean> = {
    'service.name': config.serviceName,
    'service.version': config.serviceVersion ?? '0.0.0',
    'platform': 'web',
    'rum.sdk.name': 'pulse_web_js',
    'rum.sdk.version': SDK_VERSION,
    'installation.id': installationId,
    'project.id': extractProjectId(config.apiKey),
    'browser.name': parsedUA.browserName,
    'browser.version': parsedUA.browserVersion,
    'os.name': parsedUA.osName,
    'os.version': parsedUA.osVersion,
    'device.type': parsedUA.deviceType,
  };

  if (typeof window !== 'undefined') {
    if (typeof screen !== 'undefined') {
      const w = screen.width ?? 0;
      const h = screen.height ?? 0;
      attrs['screen.resolution'] = `${w}x${h}`;
      attrs['screen.aspect_ratio'] = computeAspectRatio(w, h);
      attrs['screen.color_depth'] = screen.colorDepth ?? 0;
    }

    if (typeof navigator !== 'undefined') {
      attrs['browser.language'] = navigator.language ?? '';
      attrs['network.online'] = navigator.onLine ?? true;
    }

    try {
      attrs['timezone'] = Intl.DateTimeFormat().resolvedOptions().timeZone ?? '';
    } catch {
      attrs['timezone'] = '';
    }
  }

  return new Resource(attrs);
}
