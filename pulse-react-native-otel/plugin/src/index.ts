import { createRunOncePlugin, type ConfigPlugin } from '@expo/config-plugins';

import type { PulsePluginProps } from './types';
import {
  assertPulsePluginProps,
  resolveAndroidProps,
  resolveIosProps,
} from './resolvePluginProps';
import { withAndroidPulse } from './withAndroidPulse';
import { withIosPulse } from './withIosPulse';

const pkg = require('../../package.json');

const withPulsePlugin: ConfigPlugin<PulsePluginProps> = (
  config,
  props: PulsePluginProps
) => {
  assertPulsePluginProps(props);
  const android = resolveAndroidProps(props);
  const ios = resolveIosProps(props);
  config = withAndroidPulse(config, android);
  config = withIosPulse(config, ios);

  return config;
};

export default createRunOncePlugin(withPulsePlugin, pkg.name, pkg.version);
