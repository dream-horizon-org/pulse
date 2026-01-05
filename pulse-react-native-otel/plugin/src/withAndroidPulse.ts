import type { ConfigPlugin } from '@expo/config-plugins';
import { withMainApplication } from '@expo/config-plugins';
import { mergeContents } from '@expo/config-plugins/build/utils/generateCode';

import { PULSE_IMPORT, buildPulseInitializationCode } from './utils';
import type { PulsePluginProps } from './types';

export const withAndroidPulse: ConfigPlugin<PulsePluginProps> = (
  config,
  props: PulsePluginProps = {}
) => {
  return withMainApplication(config, (modConfig) => {
    try {
      const {
        endpointBaseUrl = 'https://your-backend-url.com',
        enableInteraction = true,
        interactionConfigUrl,
        enableActivity = true,
        enableNetwork = true,
        enableAnr = true,
        enableSlowRendering = true,
      } = props;

      // 1. Add import statement
      modConfig.modResults.contents = mergeContents({
        src: modConfig.modResults.contents,
        newSrc: PULSE_IMPORT,
        tag: 'pulse-sdk-import',
        comment: '//',
        anchor: /import\s+com\.facebook\.react\.ReactApplication/,
        offset: 1,
      }).contents;

      const initCode = buildPulseInitializationCode({
        endpointBaseUrl,
        enableInteraction,
        interactionConfigUrl,
        enableActivity,
        enableNetwork,
        enableAnr,
        enableSlowRendering,
      });

      // 2. Add initialization code after super.onCreate()
      modConfig.modResults.contents = mergeContents({
        src: modConfig.modResults.contents,
        newSrc: initCode,
        tag: 'pulse-sdk-initialization',
        comment: '//',
        anchor: /super\.onCreate\(\)/,
        offset: 1,
      }).contents;

      return modConfig;
    } catch (error) {
      console.error('Error modifying MainApplication:', error);
      return modConfig;
    }
  });
};
