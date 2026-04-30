import { PluginError } from '@expo/config-plugins';

import type {
  PulseDataCollectionState,
  PulseLogLevelConfig,
  PulseLogLevelValue,
  PulseNativeInitFields,
  PulsePluginProps,
  PulsePlatformInitProps,
  ResolvedAndroidPulseProps,
  ResolvedIosPulseProps,
} from './types';
import { PulseLogLevelValue as PulseLogLevel } from './types';
import {
  PULSE_BYTE_BUDDY_GRADLE_PLUGIN,
  PULSE_DEFAULT_DESUGAR_JDK_LIBS_VERSION,
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

/** Names allowed in app.json / plugin config (case-insensitive). */
const LOG_LEVEL_BY_NAME: Record<PulseLogLevelConfig, PulseLogLevelValue> = {
  VERBOSE: PulseLogLevel.VERBOSE,
  DEBUG: PulseLogLevel.DEBUG,
  INFO: PulseLogLevel.INFO,
  WARN: PulseLogLevel.WARN,
  ERROR: PulseLogLevel.ERROR,
  NONE: PulseLogLevel.NONE,
};

const LOG_LEVEL_ERROR_HINT =
  'one of: "VERBOSE", "DEBUG", "INFO", "WARN", "ERROR", "NONE"';

/**
 * Parses `logLevel` from plugin options (string label only). Used for validation and native codegen.
 */
function parseLogLevel(
  value: unknown,
  label: string
): PulseLogLevelValue | undefined {
  if (value === undefined) {
    return undefined;
  }
  if (typeof value !== 'string') {
    throw new PluginError(
      `Pulse config plugin: ${label} must be ${LOG_LEVEL_ERROR_HINT}.`,
      'INVALID_PLUGIN_TYPE'
    );
  }
  const key = value.trim().toUpperCase();
  if (key.length === 0) {
    throw new PluginError(
      `Pulse config plugin: ${label} must be ${LOG_LEVEL_ERROR_HINT}.`,
      'INVALID_PLUGIN_TYPE'
    );
  }
  const ord = LOG_LEVEL_BY_NAME[key as PulseLogLevelConfig];
  if (ord === undefined) {
    throw new PluginError(
      `Pulse config plugin: ${label} must be ${LOG_LEVEL_ERROR_HINT}.`,
      'INVALID_PLUGIN_TYPE'
    );
  }
  return ord;
}

function mergeLogLevel(
  root: PulsePluginProps,
  section?: PulseNativeInitFields
): PulseLogLevelConfig | undefined {
  return section?.logLevel ?? root.logLevel;
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
  const rawLogLevel = mergeLogLevel(root, section);
  return {
    apiKey,
    dataCollectionState,
    globalAttributes: section?.globalAttributes,
    logLevel:
      rawLogLevel === undefined
        ? undefined
        : parseLogLevel(rawLogLevel, 'logLevel'),
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

  parseLogLevel(p.logLevel, 'top-level "logLevel"');

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
        o.byteBuddyGradlePluginVersion !== undefined &&
        typeof o.byteBuddyGradlePluginVersion !== 'string'
      ) {
        throw new PluginError(
          'Pulse config plugin: "android.okHttpInstrumentation.byteBuddyGradlePluginVersion" must be a string when set.',
          'INVALID_PLUGIN_TYPE'
        );
      }
      if (
        o.ensureJetifierIgnoresByteBuddy !== undefined &&
        typeof o.ensureJetifierIgnoresByteBuddy !== 'boolean'
      ) {
        throw new PluginError(
          'Pulse config plugin: "android.okHttpInstrumentation.ensureJetifierIgnoresByteBuddy" must be a boolean when set.',
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

  parseLogLevel(typed.android?.logLevel, 'android.logLevel');
  parseLogLevel(typed.ios?.logLevel, 'ios.logLevel');

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
  const rawBb = okHttp?.byteBuddyGradlePluginVersion?.trim();
  const byteBuddyGradlePluginVersion =
    rawBb && rawBb.length > 0 ? rawBb : PULSE_BYTE_BUDDY_GRADLE_PLUGIN;
  const ensureJetifierIgnoresByteBuddy =
    okHttpEnabled && okHttp?.ensureJetifierIgnoresByteBuddy !== false;

  return {
    ...merged,
    instrumentation: props.android?.instrumentation,
    coreLibraryDesugaring: {
      enabled: desugarEnabled,
      version: desugarVersion,
    },
    okHttpInstrumentation: {
      enabled: okHttpEnabled,
      byteBuddyGradlePluginVersion,
      ensureJetifierIgnoresByteBuddy,
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
