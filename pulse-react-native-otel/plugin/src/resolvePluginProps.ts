import { PluginError } from '@expo/config-plugins';

import type {
  PulseDataCollectionState,
  PulseNativeInitFields,
  PulsePluginProps,
  PulsePlatformInitProps,
  ResolvedAndroidPulseProps,
  ResolvedIosPulseProps,
} from './types';
import {
  PULSE_BYTE_BUDDY_GRADLE_PLUGIN,
  PULSE_DEFAULT_DESUGAR_JDK_LIBS_VERSION,
  PULSE_DREAMHORIZON_OKHTTP_INSTR_VERSION,
} from './androidBuildConstants';

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
  const apiKey = section?.apiKey ?? root.apiKey;
  if (!apiKey?.trim()) {
    throw new PluginError(
      'Pulse config plugin: each platform needs non-empty apiKey after merging top-level defaults with the "android" / "ios" block for that platform.',
      'INVALID_PLUGIN_TYPE'
    );
  }
  const dataCollectionState = parseConsent(
    section?.dataCollectionState ?? root.dataCollectionState,
    'dataCollectionState (merge top-level with platform "android" / "ios")'
  );
  return {
    apiKey,
    dataCollectionState,
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

  const rootApiKey = p.apiKey;
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

  if (p.android != null && typeof p.android === 'object') {
    const v = (p.android as Record<string, unknown>).okHttpInstrumentation;
    if (v === undefined) {
      // ok
    } else if (typeof v === 'object' && v !== null) {
      const o = v as Record<string, unknown>;
      if (o.enabled !== undefined && typeof o.enabled !== 'boolean') {
        throw new PluginError(
          'Pulse config plugin: "android.okHttpInstrumentation.enabled" must be a boolean when set.',
          'INVALID_PLUGIN_TYPE'
        );
      }
      if (
        o.libraryVersion !== undefined &&
        typeof o.libraryVersion !== 'string'
      ) {
        throw new PluginError(
          'Pulse config plugin: "android.okHttpInstrumentation.libraryVersion" must be a string when set.',
          'INVALID_PLUGIN_TYPE'
        );
      }
      if (
        o.byteBuddyGradlePluginVersion !== undefined &&
        typeof o.byteBuddyGradlePluginVersion !== 'string'
      ) {
        throw new PluginError(
          'Pulse config plugin: "android.okHttpInstrumentation.byteBuddyGradlePluginVersion" must be a string when set.',
          'INVALID_PLUGIN_TYPE'
        );
      }
    } else {
      throw new PluginError(
        'Pulse config plugin: "android.okHttpInstrumentation" must be an object when set (e.g. { "enabled": true }).',
        'INVALID_PLUGIN_TYPE'
      );
    }
  }

  const typed = props as PulsePluginProps;

  if (typed.android?.coreLibraryDesugaring !== undefined) {
    const d = typed.android.coreLibraryDesugaring;
    if (typeof d !== 'object' || d === null) {
      throw new PluginError(
        'Pulse config plugin: "android.coreLibraryDesugaring" must be an object when set.',
        'INVALID_PLUGIN_TYPE'
      );
    }
    const o = d as Record<string, unknown>;
    if (o.enabled !== undefined && typeof o.enabled !== 'boolean') {
      throw new PluginError(
        'Pulse config plugin: "android.coreLibraryDesugaring.enabled" must be a boolean when set.',
        'INVALID_PLUGIN_TYPE'
      );
    }
    if (o.version !== undefined && typeof o.version !== 'string') {
      throw new PluginError(
        'Pulse config plugin: "android.coreLibraryDesugaring.version" must be a string when set.',
        'INVALID_PLUGIN_TYPE'
      );
    }
  }

  resolveAndroidProps(typed);
  resolveIosProps(typed);
}

export function resolveAndroidProps(
  props: PulsePluginProps
): ResolvedAndroidPulseProps {
  const merged = mergePlatformInit(props, props.android);
  const desugaring = props.android?.coreLibraryDesugaring;
  const desugarEnabled = desugaring?.enabled === true;
  const rawDesugarVersion = desugaring?.version?.trim();
  const desugarVersion =
    rawDesugarVersion && rawDesugarVersion.length > 0
      ? rawDesugarVersion
      : PULSE_DEFAULT_DESUGAR_JDK_LIBS_VERSION;

  const okHttp = props.android?.okHttpInstrumentation;
  const okHttpEnabled = okHttp?.enabled === true;
  const rawLib = okHttp?.libraryVersion?.trim();
  const rawBb = okHttp?.byteBuddyGradlePluginVersion?.trim();
  const libraryVersion =
    rawLib && rawLib.length > 0
      ? rawLib
      : PULSE_DREAMHORIZON_OKHTTP_INSTR_VERSION;
  const byteBuddyGradlePluginVersion =
    rawBb && rawBb.length > 0 ? rawBb : PULSE_BYTE_BUDDY_GRADLE_PLUGIN;

  return {
    ...merged,
    instrumentation: props.android?.instrumentation,
    coreLibraryDesugaring: {
      enabled: desugarEnabled,
      version: desugarVersion,
    },
    okHttpInstrumentation: {
      enabled: okHttpEnabled,
      libraryVersion,
      byteBuddyGradlePluginVersion,
    },
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
