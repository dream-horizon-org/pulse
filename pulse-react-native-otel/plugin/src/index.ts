import { createRunOncePlugin, type ConfigPlugin } from '@expo/config-plugins';

import type { PulsePluginProps } from './types';
import {
  assertPulsePluginProps,
  resolveAndroidBuildFlags,
  resolveAndroidProps,
  resolveIosProps,
} from './resolvePluginProps';
import { withAndroidBuildFeatures } from './withAndroidBuildFeatures';
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

  // Android only: address additional dependencies required for instrumentations. for eg: OkHttp/Byte Buddy instrumentation.
  const buildFlags = resolveAndroidBuildFlags(props.android);
  config = withAndroidBuildFeatures(config, buildFlags);

  return config;
};

export default createRunOncePlugin(withPulsePlugin, pkg.name, pkg.version);
