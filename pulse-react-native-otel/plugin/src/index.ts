import { createRunOncePlugin, type ConfigPlugin } from "@expo/config-plugins";

import type { PulsePluginProps } from "./types";
import { withAndroidPulsePlugin } from "./withAndroidPulsePlugin";

const pkg = require("../../package.json");

const withPulsePlugin: ConfigPlugin<PulsePluginProps> = (config, props: PulsePluginProps = {}) => {
  config = withAndroidPulsePlugin(config, props);

  return config;
};

export default createRunOncePlugin(withPulsePlugin, pkg.name, pkg.version);

