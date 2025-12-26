import type { ConfigPlugin } from "@expo/config-plugins";

import { withPulseMainApplication } from "./android-config/withPulseMainApplication";
import type { PulsePluginProps } from "./types";

export const withAndroidPulsePlugin: ConfigPlugin<PulsePluginProps> = (
  config,
  props = {}
) => {
  config = withPulseMainApplication(config, props);

  return config;
};

