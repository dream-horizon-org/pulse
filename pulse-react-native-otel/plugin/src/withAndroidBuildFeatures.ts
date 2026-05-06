import {
  withAppBuildGradle,
  withGradleProperties,
  withProjectBuildGradle,
} from '@expo/config-plugins';
import type { ConfigPlugin } from '@expo/config-plugins';
import type { ExpoConfig } from '@expo/config-types';

import { PULSE_DREAMHORIZON_OKHTTP_INSTR_VERSION } from './androidBuildConstants';
import {
  mergeJetifierIgnorelistForNetByteBuddy,
  type GradlePropertiesItem,
} from './androidJetifierGradlePropertiesMerge';
import {
  mergePulseOkHttpAppGradle,
  mergePulseOkHttpByteBuddyClasspath,
} from './androidOkHttpGradleMerge';
import type { ResolvedAndroidPulseProps } from './types';

type ResolvedOkHttpGradle = ResolvedAndroidPulseProps['okHttpInstrumentation'];

function withPulseJetifierIgnoreByteBuddy(
  config: ExpoConfig,
  okHttp: ResolvedOkHttpGradle
) {
  if (!okHttp.enabled || !okHttp.ensureJetifierIgnoresByteBuddy) {
    return config;
  }
  return withGradleProperties(config, (mod) => {
    mod.modResults = mergeJetifierIgnorelistForNetByteBuddy(
      mod.modResults as GradlePropertiesItem[]
    ) as typeof mod.modResults;
    return mod;
  });
}

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
        PULSE_DREAMHORIZON_OKHTTP_INSTR_VERSION
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
    config = withPulseJetifierIgnoreByteBuddy(config, okHttp);
    config = withPulseProjectBuildGradle(config, okHttp);
    config = withPulseAppBuildGradle(config, okHttp);
  }
  return config;
};
