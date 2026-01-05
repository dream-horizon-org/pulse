export const PULSE_IMPORT = 'import com.pulsereactnativeotel.Pulse\n';

import type { PulsePluginProps } from './types';

export function buildPulseInitializationCode(options: {
  endpointBaseUrl: string;
  instrumentation?: PulsePluginProps['instrumentation'];
}): string {
  const { endpointBaseUrl, instrumentation } = options;

  let code = `\n    Pulse.initialize(this, "${endpointBaseUrl}") {\n`;

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
