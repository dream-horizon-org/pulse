export const PULSE_IMPORT = 'import com.pulsereactnativeotel.Pulse\n';
export const PULSE_DATA_COLLECTION_CONSENT_IMPORT =
  'import com.pulse.android.api.otel.PulseDataCollectionConsent\n';

export const PULSE_LOG_LEVEL_IMPORT = 'import com.pulse.utils.PulseLogLevel\n';

export const ATTRIBUTES_IMPORT =
  'import io.opentelemetry.api.common.Attributes\nimport io.opentelemetry.api.common.AttributeKey\n';

function escapeKotlinString(value: string): string {
  return value
    .replace(/\\/g, '\\\\')
    .replace(/"/g, '\\"')
    .replace(/\$/g, '\\$');
}

import type {
  PulseAndroidInstrumentationProps,
  PulseAttributes,
  PulseDataCollectionState,
  PulseLogLevelValue,
} from './types';

const KOTLIN_LOG_LEVEL_NAMES = [
  'VERBOSE',
  'DEBUG',
  'INFO',
  'WARN',
  'ERROR',
  'NONE',
] as const;

function kotlinPulseLogLevelExpr(level: PulseLogLevelValue): string {
  return `PulseLogLevel.${KOTLIN_LOG_LEVEL_NAMES[level]}`;
}

function buildGlobalAttributesLambda(attributes: PulseAttributes): string {
  const puts: string[] = [];

  Object.entries(attributes)
    .filter(([, value]) => {
      if (value === null || value === undefined) return false;
      if (typeof value === 'string' && value === '') return false;
      if (Array.isArray(value) && value.length === 0) return false;
      return true;
    })
    .forEach(([k, v]) => {
      if (typeof v === 'string') {
        puts.push(`put(AttributeKey.stringKey("${k}"), "${v}")`);
      } else if (typeof v === 'number') {
        puts.push(
          `put(AttributeKey.${Number.isInteger(v) ? 'long' : 'double'}Key("${k}"), ${v}${Number.isInteger(v) ? 'L' : ''})`
        );
      } else if (typeof v === 'boolean') {
        puts.push(`put(AttributeKey.booleanKey("${k}"), ${v})`);
      } else if (Array.isArray(v)) {
        const first = v[0];
        if (typeof first === 'string') {
          puts.push(
            `put(AttributeKey.stringArrayKey("${k}"), listOf(${(v as string[]).map((x) => `"${x}"`).join(', ')}))`
          );
        } else if (typeof first === 'number') {
          const allInts = (v as number[]).every((x) => Number.isInteger(x));
          const values = allInts
            ? (v as number[]).map((x) => `${x}L`).join(', ')
            : (v as number[])
                .map((x) => (Number.isInteger(x) ? `${x}.0` : `${x}`))
                .join(', ');
          puts.push(
            `put(AttributeKey.${allInts ? 'long' : 'double'}ArrayKey("${k}"), listOf(${values}))`
          );
        } else if (typeof first === 'boolean') {
          puts.push(
            `put(AttributeKey.booleanArrayKey("${k}"), listOf(${(v as boolean[]).join(', ')}))`
          );
        }
      }
    });

  if (puts.length === 0) return 'null';

  const formatted = puts
    .map((put) => `                        ${put}`)
    .join('\n');

  return `{\n                    Attributes.builder().apply {\n${formatted}\n                    }.build()\n                }`;
}

export function buildPulseInitializationCode(options: {
  apiKey: string;
  dataCollectionState: PulseDataCollectionState;
  globalAttributes?: PulseAttributes;
  logLevel?: PulseLogLevelValue;
  instrumentation?: PulseAndroidInstrumentationProps;
}): string {
  const {
    apiKey,
    dataCollectionState,
    globalAttributes,
    logLevel,
    instrumentation,
  } = options;
  const params: string[] = [];

  params.push(`apiKey = "${escapeKotlinString(apiKey)}"`);

  params.push(
    `dataCollectionState = PulseDataCollectionConsent.${dataCollectionState}`
  );

  const attributesLambda = globalAttributes
    ? buildGlobalAttributesLambda(globalAttributes)
    : null;
  if (attributesLambda && attributesLambda !== 'null') {
    params.push(`globalAttributes = ${attributesLambda}`);
  }

  // TODO: beforeSendData requires a Kotlin subclass of PulseBeforeSendData which cannot
  // be expressed in a static app.json config. Expo users should configure it directly
  // in their native MainApplication.kt instead of through this plugin.
  params.push('beforeSendData = null');

  if (logLevel !== undefined) {
    params.push(`logLevel = ${kotlinPulseLogLevelExpr(logLevel)}`);
  }

  if (logLevel !== undefined) {
    params.push(`logLevel = ${kotlinPulseLogLevelExpr(logLevel)}`);
  }

  // Use string concatenation (not Kotlin string interpolation) for timing log lines.
  let code =
    '\n    val pulseInitT0Ms = System.currentTimeMillis()\n' +
    '    android.util.Log.i("pulse.expo", "PULSE_INIT_T0_MS=".plus(pulseInitT0Ms))\n' +
    `    Pulse.initialize(\n      this,\n      ${params.join(',\n      ')}\n    ) {\n`;

  if (instrumentation?.interaction !== undefined) {
    code += `      interaction { enabled(${instrumentation.interaction.enabled}) }\n`;
  }

  if (instrumentation?.activity?.enabled !== undefined) {
    code += `      activity { enabled(${instrumentation.activity.enabled}) }\n`;
  }

  if (instrumentation?.network?.enabled !== undefined) {
    code += `      networkMonitoring { enabled(${instrumentation.network.enabled}) }\n`;
  }

  if (instrumentation?.anr?.enabled !== undefined) {
    code += `      anrReporter { enabled(${instrumentation.anr.enabled}) }\n`;
  }

  if (instrumentation?.slowRendering?.enabled !== undefined) {
    code += `      slowRenderingReporter { enabled(${instrumentation.slowRendering.enabled}) }\n`;
  }

  if (instrumentation?.fragment?.enabled !== undefined) {
    code += `      fragment { enabled(${instrumentation.fragment.enabled}) }\n`;
  }

  if (instrumentation?.crash?.enabled !== undefined) {
    code += `      crashReporter { enabled(${instrumentation.crash.enabled}) }\n`;
  }

  code +=
    '    }\n' +
    '    val pulseInitT1Ms = System.currentTimeMillis()\n' +
    '    android.util.Log.i("pulse.expo", "PULSE_INIT_T1_MS=".plus(pulseInitT1Ms))\n' +
    '    android.util.Log.i("pulse.expo", "PULSE_INIT_DURATION_MS=".plus(pulseInitT1Ms - pulseInitT0Ms))\n';

  return code;
}
