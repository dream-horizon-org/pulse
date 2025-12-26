export const PULSE_IMPORT = "import com.pulse.android.sdk.PulseSDK\n";

export function buildPulseInitializationCode(options: {
  endpointBaseUrl: string;
  enableInteraction?: boolean;
  interactionConfigUrl?: string;
  enableActivity?: boolean;
  enableNetwork?: boolean;
  enableAnr?: boolean;
  enableSlowRendering?: boolean;
}): string {
  const {
    endpointBaseUrl,
    enableInteraction = true,
    interactionConfigUrl,
    enableActivity = true,
    enableNetwork = true,
    enableAnr = true,
    enableSlowRendering = true,
  } = options;

  let code = `\n    PulseSDK.INSTANCE.initialize(this, "${endpointBaseUrl}") {\n`;

  if (enableInteraction) {
    if (interactionConfigUrl) {
      code += `      interaction { enabled(true); setConfigUrl { "${interactionConfigUrl}" } }\n`;
    } else {
      code += `      interaction { enabled(true) }\n`;
    }
  }

  if (enableActivity) {
    code += `      activity { enabled(true) }\n`;
  }

  if (enableNetwork) {
    code += `      networkMonitoring { enabled(true) }\n`;
  }

  if (enableAnr) {
    code += `      anrReporter { enabled(true) }\n`;
  }

  if (enableSlowRendering) {
    code += `      slowRenderingReporter { enabled(true) }\n`;
  }

  code += "    }\n";

  return code;
}

