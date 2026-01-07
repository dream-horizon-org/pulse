export const PULSE_IMPORT = 'import com.pulsereactnativeotel.Pulse\n';

export const ATTRIBUTES_IMPORT =
  'import io.opentelemetry.api.common.Attributes\nimport io.opentelemetry.api.common.AttributeKey\n';

import type { PulsePluginProps, PulseAttributes } from './types';

function buildEndpointHeadersMap(headers: Record<string, string>): string {
  const entries = Object.entries(headers).map(([k, v]) => {
    return '"' + k + '" to "' + v + '"';
  });
  return `mapOf(${entries.join(', ')})`;
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
  endpointBaseUrl: string;
  endpointHeaders?: Record<string, string>;
  globalAttributes?: PulsePluginProps['globalAttributes'];
  instrumentation?: PulsePluginProps['instrumentation'];
}): string {
  const {
    endpointBaseUrl,
    endpointHeaders,
    globalAttributes,
    instrumentation,
  } = options;
  const params: string[] = [];

  if (endpointHeaders && Object.keys(endpointHeaders).length > 0) {
    params.push(
      `endpointHeaders = ${buildEndpointHeadersMap(endpointHeaders)}`
    );
  }

  const attributesLambda = globalAttributes
    ? buildGlobalAttributesLambda(globalAttributes)
    : null;
  if (attributesLambda && attributesLambda !== 'null') {
    params.push(`globalAttributes = ${attributesLambda}`);
  }

  let code = `\n    Pulse.initialize(\n      this,\n      "${endpointBaseUrl}"${params.length > 0 ? `,\n      ${params.join(',\n      ')}` : ''}\n    ) {\n`;

  if (instrumentation?.interaction !== undefined) {
    if (instrumentation.interaction.url) {
      code += `      interaction { enabled(${instrumentation.interaction.enabled}); setConfigUrl { "${instrumentation.interaction.url}" } }\n`;
    } else {
      code += `      interaction { enabled(${instrumentation.interaction.enabled}) }\n`;
    }
  }

  if (instrumentation?.activity !== undefined) {
    code += `      activity { enabled(${instrumentation.activity}) }\n`;
  }

  if (instrumentation?.network !== undefined) {
    code += `      networkMonitoring { enabled(${instrumentation.network}) }\n`;
  }

  if (instrumentation?.anr !== undefined) {
    code += `      anrReporter { enabled(${instrumentation.anr}) }\n`;
  }

  if (instrumentation?.slowRendering !== undefined) {
    code += `      slowRenderingReporter { enabled(${instrumentation.slowRendering}) }\n`;
  }

  if (instrumentation?.fragment !== undefined) {
    code += `      fragment { enabled(${instrumentation.fragment}) }\n`;
  }

  if (instrumentation?.crash !== undefined) {
    code += `      crashReporter { enabled(${instrumentation.crash}) }\n`;
  }

  code += '    }\n';

  return code;
}
