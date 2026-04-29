/** ObjC + `AppDelegate` snippets for `pulseInitialize:` (see `README-OBJC.md` + `PulseSDK.swift`). */

import type {
  PulseAttributes,
  PulseDataCollectionState,
  PulseInstrumentationEnabled,
  PulseIosInteractionInstrumentation,
  PulseIosInstrumentationProps,
  PulseIosKitConfigurationProps,
  PulseIosSessionReplayInstrumentation,
  PulseIosSessionsInstrumentation,
  PulseIosUIKitTapInstrumentation,
  PulseIosUrlSessionInstrumentation,
  ResolvedIosPulseProps,
} from './types';

const IND = '  ';

/** Injected into `AppDelegate.m` / `.mm`; matches `ios/README-OBJC.md` (`#import <…-Swift.h>`). */
export const PULSE_OBJC_PULSE_SWIFT_HEADER =
  '#import <PulseReactNativeOtel-Swift.h>\n';

function escapeObjCString(value: string): string {
  return value.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
}

function objcDataCollectionString(state: PulseDataCollectionState): string {
  switch (state) {
    case 'ALLOWED':
      return 'ALLOWED';
    case 'DENIED':
      return 'DENIED';
    default:
      return 'PENDING';
  }
}


/**
 * `NSDictionary<NSString*, PulseAttributeValue*>` or `nil`.
 */
function buildObjcGlobalAttributesVar(attributes: PulseAttributes): {
  decl: string;
  varName: string;
} {
  const entries: { key: string; expr: string }[] = [];
  for (const [k, v] of Object.entries(attributes)) {
    if (v === null || v === undefined) {
      continue;
    }
    if (typeof v === 'string' && v === '') {
      continue;
    }
    if (Array.isArray(v) && v.length === 0) {
      continue;
    }
    const key = escapeObjCString(k);
    if (typeof v === 'string') {
      entries.push({
        key,
        expr: `[PulseAttributeValue string:@"${escapeObjCString(v)}"]`,
      });
    } else if (typeof v === 'number') {
      if (Number.isInteger(v)) {
        entries.push({ key, expr: `[PulseAttributeValue int:${v}]` });
      } else {
        entries.push({ key, expr: `[PulseAttributeValue double:${v}]` });
      }
    } else if (typeof v === 'boolean') {
      entries.push({
        key,
        expr: `[PulseAttributeValue bool:${v ? 'YES' : 'NO'}]`,
      });
    } else if (Array.isArray(v)) {
      const first = (v as unknown[])[0];
      if (typeof first === 'string') {
        const es = (v as string[])
          .map((s) => `@"${escapeObjCString(s)}"`)
          .join(', ');
        entries.push({
          key,
          expr: `[PulseAttributeValue stringArray:@[${es}]]`,
        });
      } else if (typeof first === 'number') {
        const allInt = (v as number[]).every((n) => Number.isInteger(n));
        const es = (v as number[]).map((n) => `@(${n})`);
        if (allInt) {
          entries.push({
            key,
            expr: `[PulseAttributeValue intArray:@[${es.join(', ')}]]`,
          });
        } else {
          entries.push({
            key,
            expr: `[PulseAttributeValue doubleArray:@[${es.join(', ')}]]`,
          });
        }
      } else if (typeof first === 'boolean') {
        const es = (v as boolean[]).map((b) => `@(${b ? 'YES' : 'NO'})`);
        entries.push({
          key,
          expr: `[PulseAttributeValue boolArray:@[${es.join(', ')}]]`,
        });
      } else {
        // Mixed / unknown — string fallbacks
        const es = (v as string[]).map(
          (s) =>
            `[PulseAttributeValue string:@"${escapeObjCString(String(s))}"]`
        );
        entries.push({
          key,
          expr: `[PulseAttributeValue array:@[${es.join(', ')}]]`,
        });
      }
    }
  }
  if (entries.length === 0) {
    return { decl: '', varName: 'nil' };
  }
  const varName = 'pulseRNGlobalAttributes';
  const body = entries.map((e) => `${IND}@"${e.key}": ${e.expr}`).join(',\n');
  const decl = `NSDictionary<NSString*, PulseAttributeValue*> *${varName} = @{\n${body}\n};`;
  return { decl, varName };
}

/**
 * `PulseObjcKitConfiguration` or `nil` (literal).
 */
function buildObjcConfigurationVar(
  cfg: PulseIosKitConfigurationProps | undefined
): { decl: string; varName: string } {
  if (!cfg) {
    return { decl: '', varName: 'nil' };
  }
  const lines: string[] = [];
  if (cfg.includeScreenAttributes !== undefined) {
    lines.push(
      `.includeScreenAttributes = @(${cfg.includeScreenAttributes ? 'YES' : 'NO'})`
    );
  }
  if (cfg.includeNetworkAttributes !== undefined) {
    lines.push(
      `.includeNetworkAttributes = @(${cfg.includeNetworkAttributes ? 'YES' : 'NO'})`
    );
  }
  if (cfg.includeGlobalAttributes !== undefined) {
    lines.push(
      `.includeGlobalAttributes = @(${cfg.includeGlobalAttributes ? 'YES' : 'NO'})`
    );
  }
  if (lines.length === 0) {
    return { decl: '', varName: 'nil' };
  }
  const n = 'pulseRNKitConfig';
  const d = `PulseObjcKitConfiguration *${n} = [PulseObjcKitConfiguration new];
${lines.map((line) => `${n}${line}`).join(';\n')};`;
  return { decl: d, varName: n };
}

function nsBool(v: boolean): string {
  return v ? 'YES' : 'NO';
}

function emitObjcEnabled(
  out: string[],
  inst: string,
  key:
    | 'urlSession'
    | 'signPost'
    | 'interaction'
    | 'location'
    | 'crash'
    | 'appLifecycle'
    | 'screenLifecycle'
    | 'appStartup',
  c: PulseInstrumentationEnabled | undefined
): void {
  if (c === undefined || c.enabled === undefined) {
    return;
  }
  out.push(
    `${inst}.${key} = ${
      c.enabled
        ? '[PulseObjcEnabledConfig enabled]'
        : '[PulseObjcEnabledConfig disabled]'
    };`
  );
}

function emitObjcUrlSession(
  out: string[],
  inst: string,
  c: PulseIosUrlSessionInstrumentation | undefined
): void {
  if (c === undefined || c.enabled === undefined) {
    return;
  }
  out.push(
    `${inst}.urlSession = ${
      c.enabled
        ? '[PulseObjcEnabledConfig enabled]'
        : '[PulseObjcEnabledConfig disabled]'
    };`
  );
}

function emitObjcSessions(
  out: string[],
  inst: string,
  c: PulseIosSessionsInstrumentation | undefined
): void {
  if (c === undefined) {
    return;
  }
  const has =
    c.enabled !== undefined ||
    c.maxLifetimeSeconds !== undefined ||
    c.backgroundInactivityTimeoutSeconds !== undefined ||
    c.shouldPersist !== undefined;
  if (!has) {
    return;
  }
  const v = 'pulseRNSessCfg';
  if (c.enabled !== undefined) {
    out.push(
      `PulseObjcSessionsConfig *${v} = [PulseObjcSessionsConfig ${c.enabled ? 'enabled' : 'disabled'}];`
    );
  } else {
    out.push(`PulseObjcSessionsConfig *${v} = [PulseObjcSessionsConfig new];`);
  }
  if (c.maxLifetimeSeconds !== undefined) {
    out.push(`${v}.maxLifetimeSeconds = @(${c.maxLifetimeSeconds});`);
  }
  if (c.backgroundInactivityTimeoutSeconds !== undefined) {
    out.push(
      `${v}.backgroundInactivityTimeoutSeconds = @(${c.backgroundInactivityTimeoutSeconds});`
    );
  }
  if (c.shouldPersist !== undefined) {
    out.push(`${v}.shouldPersist = @(${nsBool(c.shouldPersist)});`);
  }
  out.push(`${inst}.sessions = ${v};`);
}

function emitObjcInteraction(
  out: string[],
  inst: string,
  c: PulseIosInteractionInstrumentation | undefined
): void {
  emitObjcEnabled(out, inst, 'interaction', c);
}

function emitObjcUIKitTap(
  out: string[],
  inst: string,
  c: PulseIosUIKitTapInstrumentation | undefined
): void {
  if (c === undefined) {
    return;
  }
  const hasTap =
    c.captureContext !== undefined ||
    c.rage !== undefined;
  if (!hasTap) {
    return;
  }
  const t = 'pulseRNTapCfg';
  out.push(`PulseObjcUIKitTapConfig *${t} = [PulseObjcUIKitTapConfig new];`);
  if (c.captureContext !== undefined) {
    out.push(`${t}.captureContext = @(${nsBool(c.captureContext)});`);
  }
  if (c.rage) {
    const r = c.rage;
    const rv = 'pulseRNRageCfg';
    out.push(`PulseObjcRageConfig *${rv} = [PulseObjcRageConfig new];`);
    if (r.timeWindowMs !== undefined) {
      out.push(`${rv}.timeWindowMs = @(${r.timeWindowMs});`);
    }
    if (r.rageThreshold !== undefined) {
      out.push(`${rv}.rageThreshold = @(${r.rageThreshold});`);
    }
    if (r.radiusPt !== undefined) {
      out.push(`${rv}.radiusPt = @(${r.radiusPt});`);
    }
    out.push(`${t}.rage = ${rv};`);
  }
  out.push(`${inst}.uiKitTap = ${t};`);
}

function emitObjcSessionReplay(
  out: string[],
  inst: string,
  c: PulseIosSessionReplayInstrumentation | undefined
): void {
  if (c === undefined) {
    return;
  }
  const hasNested =
    (c.maskViewClasses && c.maskViewClasses.length > 0) ||
    (c.unmaskViewClasses && c.unmaskViewClasses.length > 0);
  if (!hasNested) {
    return;
  }
  const sr = 'pulseRNSRcfg';
  out.push(
    `PulseObjcSessionReplayConfig *${sr} = [PulseObjcSessionReplayConfig new];`
  );
  if (c.maskViewClasses && c.maskViewClasses.length > 0) {
    const a = c.maskViewClasses
      .map((x) => `@"${escapeObjCString(x)}"`)
      .join(', ');
    out.push(`${sr}.maskViewClasses = @[${a}];`);
  }
  if (c.unmaskViewClasses && c.unmaskViewClasses.length > 0) {
    const a = c.unmaskViewClasses
      .map((x) => `@"${escapeObjCString(x)}"`)
      .join(', ');
    out.push(`${sr}.unmaskViewClasses = @[${a}];`);
  }
  out.push(`${inst}.sessionReplay = ${sr};`);
}

/** `PulseObjcInstrumentations` var name or `nil` string. */
function buildObjcInstrumentationsVar(
  inst: PulseIosInstrumentationProps | undefined
): { decl: string; varName: string } {
  if (!inst) {
    return { decl: '', varName: 'nil' };
  }
  const n = 'pulseRNInstCfg';
  const lines: string[] = [];
  emitObjcUrlSession(lines, n, inst.urlSession);
  emitObjcSessions(lines, n, inst.sessions);
  emitObjcEnabled(lines, n, 'signPost', inst.signPost);
  emitObjcInteraction(lines, n, inst.interaction);
  emitObjcEnabled(lines, n, 'location', inst.location);
  emitObjcEnabled(lines, n, 'crash', inst.crash);
  emitObjcEnabled(lines, n, 'appLifecycle', inst.appLifecycle);
  emitObjcEnabled(lines, n, 'screenLifecycle', inst.screenLifecycle);
  emitObjcEnabled(lines, n, 'appStartup', inst.appStartup);
  emitObjcUIKitTap(lines, n, inst.uiKitTap);
  emitObjcSessionReplay(lines, n, inst.sessionReplay);
  if (lines.length === 0) {
    return { decl: '', varName: 'nil' };
  }
  return {
    decl: `PulseObjcInstrumentations *${n} = [PulseObjcInstrumentations new];\n${lines.join(
      '\n'
    )}`,
    varName: n,
  };
}

/** `pulseInitialize:` for AppDelegate (see `README-OBJC.md`). */
export function buildObjcPulseSdkInitialization(
  props: ResolvedIosPulseProps
): string {
  const {
    apiKey,
    dataCollectionState,
    globalAttributes,
    configuration,
    instrumentation,
  } = props;
  const dc = `@\"${objcDataCollectionString(dataCollectionState)}\"`;
  const g =
    globalAttributes && Object.keys(globalAttributes).length > 0
      ? buildObjcGlobalAttributesVar(globalAttributes)
      : { decl: '', varName: 'nil' };
  const k = buildObjcConfigurationVar(configuration);
  const i = buildObjcInstrumentationsVar(instrumentation);
  const parts: string[] = [];
  if (g.decl) {
    parts.push(g.decl);
  }
  if (k.decl) {
    parts.push(k.decl);
  }
  if (i.decl) {
    parts.push(i.decl);
  }
  const keyLit = `@\"${escapeObjCString(apiKey)}\"`;
  const call = `[PulseSDK pulseInitialize:${keyLit}
    dataCollectionState:${dc}
       globalAttributes:${g.varName}
          configuration:${k.varName}
        instrumentations:${i.varName}];`;
  parts.push(call);
  return (
    parts
      .map((line) =>
        line
          .split('\n')
          .map((l) => `${IND}${l}`)
          .join('\n')
      )
      .join('\n') + '\n'
  );
}

export function isObjCAppDelegateLanguage(
  language: string | undefined,
  appDelegatePath: string | undefined
): boolean {
  const p = (appDelegatePath || '').toLowerCase();
  if (p.endsWith('.m') || p.endsWith('.mm')) {
    return true;
  }
  const lang = (language || '').toLowerCase();
  if (!lang || lang === 'swift') {
    return false;
  }
  return (
    lang === 'objc' ||
    lang === 'objective-c' ||
    lang === 'objective-c++' ||
    lang === 'objectivec' ||
    lang === 'objectivec++' ||
    lang === 'mm' ||
    lang === 'objcpp'
  );
}

/** Heuristic when Expo does not set `path` or `language` to ObjC. */
export function looksLikeObjCAppDelegateSource(contents: string): boolean {
  if (
    /\bimport\s+React\b/.test(contents) ||
    /\bimport\s+Expo\b/.test(contents)
  ) {
    return false;
  }
  return /#import/.test(contents) && /@implementation/.test(contents);
}

export function getAppDelegatePrebuildKind(modResults: {
  language?: string;
  contents: string;
  path?: string;
}): 'swift' | 'objc' | 'unknown' {
  const p = (modResults.path || '').toLowerCase();
  if (p.endsWith('.swift') || p.endsWith('.m') || p.endsWith('.mm')) {
    if (p.endsWith('.swift')) {
      return 'swift';
    }
    return 'objc';
  }
  if (modResults.language === 'swift') {
    return 'swift';
  }
  if (isObjCAppDelegateLanguage(modResults.language, modResults.path)) {
    return 'objc';
  }
  if (looksLikeObjCAppDelegateSource(modResults.contents)) {
    return 'objc';
  }
  if (/\bimport\s+ReactAppDependencyProvider\b/.test(modResults.contents)) {
    return 'swift';
  }
  if (/reactNativeFactory\s*=\s*factory/.test(modResults.contents)) {
    return 'swift';
  }
  return 'unknown';
}
