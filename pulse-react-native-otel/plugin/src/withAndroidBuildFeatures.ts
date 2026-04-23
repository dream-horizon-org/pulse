import {
  withAppBuildGradle,
  withProjectBuildGradle,
} from '@expo/config-plugins';
import type { ConfigPlugin } from '@expo/config-plugins';
import type { ExpoConfig } from '@expo/config-types';

import {
  mergePulseOkHttpAppGradle,
  mergePulseOkHttpByteBuddyClasspath,
} from './androidOkHttpGradleMerge';
import type { ResolvedAndroidPulseProps } from './types';

type ResolvedOkHttpGradle = ResolvedAndroidPulseProps['okHttpInstrumentation'];

function withPulseProjectBuildGradle(
  config: ExpoConfig,
  okHttp: ResolvedOkHttpGradle
) {
  if (!okHttp.enabled) {
    return config;
  }
  return withProjectBuildGradle(config, (mod) => {
    if (mod.modResults.language && mod.modResults.language !== 'groovy') {
      return mod;
    }
    try {
      mod.modResults.contents = mergePulseOkHttpByteBuddyClasspath(
        mod.modResults.contents,
        okHttp.byteBuddyGradlePluginVersion
      );
    } catch (e) {
      console.error(
        'Pulse: could not add Byte Buddy to android/build.gradle (expected Kotlin plugin classpath).',
        e
      );
    }
    return mod;
  });
}

function withPulseAppBuildGradle(
  config: ExpoConfig,
  okHttp: ResolvedOkHttpGradle
) {
  if (!okHttp.enabled) {
    return config;
  }

  return withAppBuildGradle(config, (mod) => {
    if (mod.modResults.language && mod.modResults.language !== 'groovy') {
      return mod;
    }
    try {
      mod.modResults.contents = mergePulseOkHttpAppGradle(
        mod.modResults.contents,
        okHttp.libraryVersion
      );
    } catch (e) {
      console.error(
        'Pulse: could not merge OkHttp instrumentation into app/build.gradle.',
        e
      );
    }
    return mod;
  });
}

/** Android Gradle: Byte Buddy + Dream Horizon OkHttp instrumentation when `okHttpInstrumentation.enabled`. */
export const withAndroidBuildFeatures: ConfigPlugin<ResolvedOkHttpGradle> = (
  config,
  okHttp
) => {
  if (okHttp.enabled) {
    config = withPulseProjectBuildGradle(config, okHttp);
    config = withPulseAppBuildGradle(config, okHttp);
  }
  return config;
};
