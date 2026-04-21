import { PluginError } from '@expo/config-plugins';

import type {
  PulseDataCollectionState,
  PulseNativeInitFields,
  PulsePluginProps,
  PulsePlatformInitProps,
  ResolvedAndroidPulseProps,
  ResolvedIosPulseProps,
} from './types';

function parseConsent(value: unknown, label: string): PulseDataCollectionState {
  if (value === 'PENDING' || value === 'ALLOWED' || value === 'DENIED') {
    return value;
  }
  throw new PluginError(
    `Pulse config plugin: ${label} must be one of "PENDING", "ALLOWED", or "DENIED".`,
    'INVALID_PLUGIN_TYPE'
  );
}

function mergePlatformInit(
  root: PulsePluginProps,
  section?: PulseNativeInitFields
): PulsePlatformInitProps {
  const endpointBaseUrl = section?.endpointBaseUrl ?? root.endpointBaseUrl;
  const apiKey = section?.apiKey ?? root.apiKey;
  if (!endpointBaseUrl?.trim() || !apiKey?.trim()) {
    throw new PluginError(
      'Pulse config plugin: each platform needs non-empty endpointBaseUrl and apiKey after merging top-level defaults with the "android" / "ios" block for that platform.',
      'INVALID_PLUGIN_TYPE'
    );
  }
  const dataCollectionState = parseConsent(
    section?.dataCollectionState ?? root.dataCollectionState,
    'dataCollectionState (merge top-level with platform "android" / "ios")'
  );
  return {
    endpointBaseUrl,
    apiKey,
    dataCollectionState,
    endpointHeaders: section?.endpointHeaders ?? root.endpointHeaders,
    configEndpointUrl: section?.configEndpointUrl ?? root.configEndpointUrl,
    customEventCollectorUrl:
      section?.customEventCollectorUrl ?? root.customEventCollectorUrl,
    globalAttributes: section?.globalAttributes,
  };
}

/** Validates plugin props and that Android + iOS merge succeed. */
export function assertPulsePluginProps(
  props: unknown
): asserts props is PulsePluginProps {
  if (typeof props !== 'object' || props === null) {
    throw new PluginError(
      'Pulse config plugin: plugin options must be a non-null object.',
      'INVALID_PLUGIN_TYPE'
    );
  }
  const p = props as Record<string, unknown>;

  const rootEndpoint = p.endpointBaseUrl;
  const rootApiKey = p.apiKey;
  if (typeof rootEndpoint !== 'string' || rootEndpoint.trim() === '') {
    throw new PluginError(
      'Pulse config plugin: top-level "endpointBaseUrl" is required (non-empty string). Override per platform under "android" / "ios" if needed.',
      'INVALID_PLUGIN_TYPE'
    );
  }
  if (typeof rootApiKey !== 'string' || rootApiKey.trim() === '') {
    throw new PluginError(
      'Pulse config plugin: top-level "apiKey" is required (non-empty string). Override per platform under "android" / "ios" if needed.',
      'INVALID_PLUGIN_TYPE'
    );
  }

  parseConsent(
    p.dataCollectionState,
    'top-level "dataCollectionState" (required; override per platform under "android" / "ios" if needed)'
  );

  const forbiddenRoot = [
    'globalAttributes',
    'instrumentation',
    'configuration',
  ] as const;
  for (const key of forbiddenRoot) {
    if (key in p && p[key] !== undefined) {
      throw new PluginError(
        `Pulse config plugin: "${key}" belongs under "android" or "ios" only (not at the top level).`,
        'INVALID_PLUGIN_TYPE'
      );
    }
  }
  if (p.android !== undefined && typeof p.android !== 'object') {
    throw new PluginError(
      'Pulse config plugin: "android" must be an object when set.',
      'INVALID_PLUGIN_TYPE'
    );
  }
  if (p.ios !== undefined && typeof p.ios !== 'object') {
    throw new PluginError(
      'Pulse config plugin: "ios" must be an object when set.',
      'INVALID_PLUGIN_TYPE'
    );
  }

  const typed = props as PulsePluginProps;
  resolveAndroidProps(typed);
  resolveIosProps(typed);
}

export function resolveAndroidProps(
  props: PulsePluginProps
): ResolvedAndroidPulseProps {
  const merged = mergePlatformInit(props, props.android);
  return {
    ...merged,
    instrumentation: props.android?.instrumentation,
  };
}

export function resolveIosProps(
  props: PulsePluginProps
): ResolvedIosPulseProps {
  const merged = mergePlatformInit(props, props.ios);
  return {
    ...merged,
    configuration: props.ios?.configuration,
    instrumentation: props.ios?.instrumentation,
  };
}
