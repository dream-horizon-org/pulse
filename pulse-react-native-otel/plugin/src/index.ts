import { createRunOncePlugin, type ConfigPlugin } from '@expo/config-plugins';

import type { PulsePluginProps } from './types';
import {
  assertPulsePluginProps,
  resolveAndroidProps,
  resolveIosProps,
} from './resolvePluginProps';
import { withAndroidBuildFeatures } from './withAndroidBuildFeatures';
import { withAndroidPhoneStatePermissions } from './withAndroidPhoneStatePermissions';
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

  // Add Android manifest permissions required for carrier/network subtype attributes.
  config = withAndroidPhoneStatePermissions(config);
  config = withAndroidPulse(config, android);
  config = withIosPulse(config, ios);

  // Android only: OkHttp / Byte Buddy Gradle wiring (resolved with android props, same pass as desugaring).
  config = withAndroidBuildFeatures(config, android.okHttpInstrumentation);

  return config;
};

export default createRunOncePlugin(withPulsePlugin, pkg.name, pkg.version);
