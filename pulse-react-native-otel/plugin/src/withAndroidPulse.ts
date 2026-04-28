import type { ConfigPlugin } from '@expo/config-plugins';
import { withAppBuildGradle, withMainApplication } from '@expo/config-plugins';
import { mergeContents } from '@expo/config-plugins/build/utils/generateCode';

import { mergePulseCoreLibraryDesugaringCompileOptions } from './androidDesugarGradleMerge';
import {
  PULSE_IMPORT,
  PULSE_DATA_COLLECTION_CONSENT_IMPORT,
  PULSE_LOG_LEVEL_IMPORT,
  ATTRIBUTES_IMPORT,
  buildPulseInitializationCode,
} from './utils';
import type { ResolvedAndroidPulseProps } from './types';

export const withAndroidPulse: ConfigPlugin<ResolvedAndroidPulseProps> = (
  config,
  props: ResolvedAndroidPulseProps
) => {
  config = withMainApplication(config, (modConfig) => {
    try {
      const {
        apiKey,
        dataCollectionState,
        globalAttributes,
        logLevel,
        instrumentation,
      } = props;

      // 1. Add import statements
      modConfig.modResults.contents = mergeContents({
        src: modConfig.modResults.contents,
        newSrc: PULSE_IMPORT,
        tag: 'pulse-sdk-import',
        comment: '//',
        anchor: /import\s+com\.facebook\.react\.ReactApplication/,
        offset: 1,
      }).contents;

      modConfig.modResults.contents = mergeContents({
        src: modConfig.modResults.contents,
        newSrc: PULSE_DATA_COLLECTION_CONSENT_IMPORT,
        tag: 'pulse-data-collection-consent-import',
        comment: '//',
        anchor: /import\s+com\.pulsereactnativeotel\.Pulse/,
        offset: 1,
      }).contents;

      if (globalAttributes && Object.keys(globalAttributes).length > 0) {
        modConfig.modResults.contents = mergeContents({
          src: modConfig.modResults.contents,
          newSrc: ATTRIBUTES_IMPORT,
          tag: 'pulse-attributes-import',
          comment: '//',
          anchor: /import\s+com\.pulsereactnativeotel\.Pulse/,
          offset: 1,
        }).contents;
      }

      if (logLevel !== undefined) {
        modConfig.modResults.contents = mergeContents({
          src: modConfig.modResults.contents,
          newSrc: PULSE_LOG_LEVEL_IMPORT,
          tag: 'pulse-log-level-import',
          comment: '//',
          anchor: /import\s+com\.pulsereactnativeotel\.Pulse/,
          offset: 1,
        }).contents;
      }

      const initCode = buildPulseInitializationCode({
        apiKey,
        dataCollectionState,
        globalAttributes,
        logLevel,
        instrumentation,
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

  config = withAppBuildGradle(config, (modConfig) => {
    try {
      const { coreLibraryDesugaring } = props;
      if (!coreLibraryDesugaring.enabled) {
        return modConfig;
      }

      const version = coreLibraryDesugaring.version;
      const desugarDep = `    coreLibraryDesugaring 'com.android.tools:desugar_jdk_libs:${version}'\n`;

      modConfig.modResults.contents =
        mergePulseCoreLibraryDesugaringCompileOptions(
          modConfig.modResults.contents
        );

      modConfig.modResults.contents = mergeContents({
        src: modConfig.modResults.contents,
        newSrc: desugarDep,
        tag: 'pulse-android-desugar-jdk-libs',
        comment: '//',
        anchor: /dependencies\s*\{/,
        offset: 1,
      }).contents;

      return modConfig;
    } catch (error) {
      console.error('Error modifying app build.gradle:', error);
      return modConfig;
    }
  });

  return config;
};
