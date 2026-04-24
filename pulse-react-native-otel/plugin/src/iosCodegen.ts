/** Swift snippets for Expo AppDelegate injection (see Android `buildPulseInitializationCode`). */

import type {
  PulseAttributes,
  PulseDataCollectionState,
  PulseInstrumentationEnabled,
  PulseIosInstrumentationProps,
  PulseIosInteractionInstrumentation,
  PulseIosKitConfigurationProps,
  PulseIosSessionReplayImagePrivacy,
  PulseIosSessionReplayInstrumentation,
  PulseIosSessionReplayTextPrivacy,
  PulseIosSessionsInstrumentation,
  PulseIosUIKitTapInstrumentation,
  PulseIosUrlSessionInstrumentation,
  ResolvedIosPulseProps,
} from './types';

export const PULSE_IOS_IMPORT = 'import PulseReactNativeOtel\n';

export const PULSE_IOS_OTEL_API_IMPORT = 'import OpenTelemetryApi\n';

function escapeSwiftString(value: string): string {
  return value.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
}

function buildSwiftGlobalAttributesLiteral(
  attributes: PulseAttributes
): string {
  const lines: string[] = [];

  Object.entries(attributes)
    .filter(([, value]) => {
      if (value === null || value === undefined) return false;
      if (typeof value === 'string' && value === '') return false;
      if (Array.isArray(value) && value.length === 0) return false;
      return true;
    })
    .forEach(([k, v]) => {
      const key = escapeSwiftString(k);
      if (typeof v === 'string') {
        lines.push(
          `      "${key}": AttributeValue.string("${escapeSwiftString(v)}")`
        );
      } else if (typeof v === 'number') {
        if (Number.isInteger(v)) {
          lines.push(`      "${key}": AttributeValue.int(${v})`);
        } else {
          lines.push(`      "${key}": AttributeValue.double(${v})`);
        }
      } else if (typeof v === 'boolean') {
        lines.push(`      "${key}": AttributeValue.bool(${v})`);
      } else if (Array.isArray(v)) {
        const first = v[0];
        if (typeof first === 'string') {
          const elems = (v as string[])
            .map((x) => `AttributeValue.string("${escapeSwiftString(x)}")`)
            .join(', ');
          lines.push(
            `      "${key}": AttributeValue.array(AttributeArray(values: [${elems}]))`
          );
        } else if (typeof first === 'number') {
          const allInts = (v as number[]).every((x) => Number.isInteger(x));
          const elems = (v as number[])
            .map((x) =>
              allInts
                ? `AttributeValue.int(${x})`
                : `AttributeValue.double(${x})`
            )
            .join(', ');
          lines.push(
            `      "${key}": AttributeValue.array(AttributeArray(values: [${elems}]))`
          );
        } else if (typeof first === 'boolean') {
          const elems = (v as boolean[])
            .map((x) => `AttributeValue.bool(${x})`)
            .join(', ');
          lines.push(
            `      "${key}": AttributeValue.array(AttributeArray(values: [${elems}]))`
          );
        }
      }
    });

  if (lines.length === 0) {
    return 'nil';
  }

  return `[\n${lines.join(',\n')}\n    ]`;
}

function swiftConsentCase(state: PulseDataCollectionState): string {
  switch (state) {
    case 'ALLOWED':
      return 'allowed';
    case 'DENIED':
      return 'denied';
    default:
      return 'pending';
  }
}

/** Swift `configuration:` or `nil`. */
export function buildSwiftConfigurationArg(
  cfg: PulseIosKitConfigurationProps | undefined
): string {
  if (!cfg) {
    return 'nil';
  }
  const lines: string[] = [];
  if (cfg.includeScreenAttributes !== undefined) {
    lines.push(
      `      kit.includeScreenAttributes = ${cfg.includeScreenAttributes}`
    );
  }
  if (cfg.includeNetworkAttributes !== undefined) {
    lines.push(
      `      kit.includeNetworkAttributes = ${cfg.includeNetworkAttributes}`
    );
  }
  if (cfg.includeGlobalAttributes !== undefined) {
    lines.push(
      `      kit.includeGlobalAttributes = ${cfg.includeGlobalAttributes}`
    );
  }
  if (lines.length === 0) {
    return 'nil';
  }
  return `{ kit in\n${lines.join('\n')}\n    }`;
}

function swiftTextAndInputPrivacy(v: PulseIosSessionReplayTextPrivacy): string {
  switch (v) {
    case 'maskAllInputs':
      return '.maskAllInputs';
    case 'maskSensitiveInputs':
      return '.maskSensitiveInputs';
    default:
      return '.maskAll';
  }
}

function swiftImagePrivacy(v: PulseIosSessionReplayImagePrivacy): string {
  return v === 'maskNone' ? '.maskNone' : '.maskAll';
}

function emitIosInstrumentationEnabled(
  body: string[],
  method: string,
  cfg: PulseInstrumentationEnabled | undefined
): void {
  if (cfg === undefined || cfg.enabled === undefined) {
    return;
  }
  body.push(`      config.${method} { $0.enabled(${cfg.enabled}) }`);
}

function emitIosUrlSession(
  body: string[],
  cfg: PulseIosUrlSessionInstrumentation | undefined
): void {
  if (cfg === undefined || cfg.enabled === undefined) {
    return;
  }
  body.push(`      config.urlSession { $0.enabled(${cfg.enabled}) }`);
}

function emitIosSessions(
  body: string[],
  cfg: PulseIosSessionsInstrumentation | undefined
): void {
  if (cfg === undefined) {
    return;
  }
  const hasAny =
    cfg.enabled !== undefined ||
    cfg.maxLifetimeSeconds !== undefined ||
    cfg.backgroundInactivityTimeoutSeconds !== undefined ||
    cfg.shouldPersist !== undefined;
  if (!hasAny) {
    return;
  }
  const lines: string[] = [];
  if (cfg.enabled !== undefined) {
    lines.push(`s.enabled(${cfg.enabled})`);
  }
  if (cfg.maxLifetimeSeconds !== undefined) {
    lines.push(`s.maxLifetime(${cfg.maxLifetimeSeconds})`);
  }
  if (cfg.backgroundInactivityTimeoutSeconds !== undefined) {
    lines.push(
      `s.backgroundInactivityTimeout(${cfg.backgroundInactivityTimeoutSeconds})`
    );
  }
  if (cfg.shouldPersist !== undefined) {
    lines.push(`s.shouldPersist(${cfg.shouldPersist})`);
  }
  body.push(
    `      config.sessions { s in\n        ${lines.join('\n        ')}\n      }`
  );
}

function emitIosInteraction(
  body: string[],
  cfg: PulseIosInteractionInstrumentation | undefined
): void {
  if (cfg === undefined) {
    return;
  }
  const parts: string[] = [];
  if (cfg.enabled !== undefined) {
    parts.push(`$0.enabled(${cfg.enabled})`);
  }
  if (parts.length > 0) {
    body.push(`      config.interaction { ${parts.join('; ')} }`);
  }
}

function emitIosUIKitTap(
  body: string[],
  cfg: PulseIosUIKitTapInstrumentation | undefined
): void {
  if (cfg === undefined) {
    return;
  }
  const inner: string[] = [];
  if (cfg.enabled !== undefined) {
    inner.push(`tap.enabled(${cfg.enabled})`);
  }
  if (cfg.captureContext !== undefined) {
    inner.push(`tap.captureContext(${cfg.captureContext})`);
  }
  if (cfg.rage) {
    const rage = cfg.rage;
    const rageLines: string[] = [];
    if (rage.timeWindowMs !== undefined) {
      rageLines.push(`r.timeWindowMs = ${rage.timeWindowMs}`);
    }
    if (rage.rageThreshold !== undefined) {
      rageLines.push(`r.rageThreshold = ${rage.rageThreshold}`);
    }
    if (rage.radiusPt !== undefined) {
      rageLines.push(`r.radiusPt = ${rage.radiusPt}`);
    }
    if (rageLines.length > 0) {
      inner.push(
        `tap.rage { r in\n        ${rageLines.join('\n        ')}\n      }`
      );
    }
  }
  if (inner.length > 0) {
    body.push(
      `      config.uiKitTap { tap in\n        ${inner.join('\n        ')}\n      }`
    );
  }
}

function emitIosSessionReplay(
  body: string[],
  cfg: PulseIosSessionReplayInstrumentation | undefined
): void {
  if (cfg === undefined) {
    return;
  }
  const replayLines: string[] = [];
  if (cfg.enabled !== undefined) {
    replayLines.push(`replay.enabled(${cfg.enabled})`);
  }
  const cfgLines: string[] = [];
  if (cfg.replayEndpointBaseUrl !== undefined) {
    cfgLines.push(
      `local.replayEndpointBaseUrl = "${escapeSwiftString(cfg.replayEndpointBaseUrl)}"`
    );
  }
  if (cfg.textAndInputPrivacy !== undefined) {
    cfgLines.push(
      `local.textAndInputPrivacy = ${swiftTextAndInputPrivacy(cfg.textAndInputPrivacy)}`
    );
  }
  if (cfg.imagePrivacy !== undefined) {
    cfgLines.push(
      `local.imagePrivacy = ${swiftImagePrivacy(cfg.imagePrivacy)}`
    );
  }
  if (cfg.captureIntervalMs !== undefined) {
    cfgLines.push(`local.captureIntervalMs = ${cfg.captureIntervalMs}`);
  }
  if (cfg.compressionQuality !== undefined) {
    cfgLines.push(`local.compressionQuality = ${cfg.compressionQuality}`);
  }
  if (cfg.screenshotScale !== undefined) {
    cfgLines.push(`local.screenshotScale = ${cfg.screenshotScale}`);
  }
  if (cfg.flushIntervalSeconds !== undefined) {
    cfgLines.push(`local.flushIntervalSeconds = ${cfg.flushIntervalSeconds}`);
  }
  if (cfg.flushAt !== undefined) {
    cfgLines.push(`local.flushAt = ${cfg.flushAt}`);
  }
  if (cfg.maxBatchSize !== undefined) {
    cfgLines.push(`local.maxBatchSize = ${cfg.maxBatchSize}`);
  }
  if (cfg.maskViewClasses !== undefined && cfg.maskViewClasses.length > 0) {
    const elems = cfg.maskViewClasses
      .map((c) => `"${escapeSwiftString(c)}"`)
      .join(', ');
    cfgLines.push(`local.maskViewClasses = Set([${elems}])`);
  }
  if (cfg.unmaskViewClasses !== undefined && cfg.unmaskViewClasses.length > 0) {
    const elems = cfg.unmaskViewClasses
      .map((c) => `"${escapeSwiftString(c)}"`)
      .join(', ');
    cfgLines.push(`local.unmaskViewClasses = Set([${elems}])`);
  }
  if (cfgLines.length > 0) {
    replayLines.push(`replay.configure { local in`);
    for (const line of cfgLines) {
      replayLines.push(`        ${line}`);
    }
    replayLines.push(`        }`);
  }
  if (replayLines.length > 0) {
    body.push(
      `      config.sessionReplay { replay in\n        ${replayLines.join('\n        ')}\n      }`
    );
  }
}

/** Swift `instrumentations:` closure or `nil`. */
export function buildSwiftInstrumentationsArg(
  inst: PulseIosInstrumentationProps | undefined
): string {
  if (!inst) {
    return 'nil';
  }
  const body: string[] = [];
  emitIosUrlSession(body, inst.urlSession);
  emitIosSessions(body, inst.sessions);
  emitIosInstrumentationEnabled(body, 'signPost', inst.signPost);
  emitIosInteraction(body, inst.interaction);
  emitIosInstrumentationEnabled(body, 'location', inst.location);
  emitIosInstrumentationEnabled(body, 'crash', inst.crash);
  emitIosInstrumentationEnabled(body, 'appLifecycle', inst.appLifecycle);
  emitIosInstrumentationEnabled(body, 'screenLifecycle', inst.screenLifecycle);
  emitIosInstrumentationEnabled(body, 'appStartup', inst.appStartup);
  emitIosUIKitTap(body, inst.uiKitTap);
  emitIosSessionReplay(body, inst.sessionReplay);
  if (body.length === 0) {
    return 'nil';
  }
  return `{ config in\n${body.join('\n')}\n    }`;
}

/** Swift `PulseSDK.initialize` for AppDelegate (before `startReactNative`). */
export function buildSwiftPulseSdkInitialization(
  props: ResolvedIosPulseProps
): string {
  const {
    apiKey,
    dataCollectionState,
    globalAttributes,
    configuration,
    instrumentation,
  } = props;

  const globalAttrsArg =
    globalAttributes && Object.keys(globalAttributes).length > 0
      ? buildSwiftGlobalAttributesLiteral(globalAttributes)
      : 'nil';

  const consent = swiftConsentCase(dataCollectionState);
  const configurationArg = buildSwiftConfigurationArg(configuration);
  const instrumentationsArg = buildSwiftInstrumentationsArg(instrumentation);

  return `
    PulseSDK.initialize(
      apiKey: "${escapeSwiftString(apiKey)}",
      dataCollectionState: .${consent},
      globalAttributes: ${globalAttrsArg},
      resource: nil,
      configuration: ${configurationArg},
      instrumentations: ${instrumentationsArg},
      beforeSendSpan: nil,
      beforeSendLog: nil,
      beforeSendMetric: nil,
      tracerProviderCustomizer: nil,
      loggerProviderCustomizer: nil
    )
`;
}
