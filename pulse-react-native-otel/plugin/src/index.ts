import { createRunOncePlugin, type ConfigPlugin } from '@expo/config-plugins';

import type { PulsePluginProps } from './types';
import { withAndroidPulse } from './withAndroidPulse';

const pkg = require('../../package.json');

const withPulsePlugin: ConfigPlugin<PulsePluginProps> = (
  config,
  props: PulsePluginProps
) => {
  config = withAndroidPulse(config, props);

  return config;
};

export default createRunOncePlugin(withPulsePlugin, pkg.name, pkg.version);
