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
import { mergeKotlin19CompatFlag } from './androidKotlin19CompatGradlePropertiesMerge';
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
  okHttp: ResolvedOkHttpGradle,
  kotlin19Compat: boolean
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
        PULSE_DREAMHORIZON_OKHTTP_INSTR_VERSION,
        kotlin19Compat
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

/** Args struct for `withAndroidBuildFeatures` — both flags travel together because the okhttp3-library injection at `:app` needs a kotlin-stdlib force when `kotlin19Compat` is also on (otherwise Gradle resolution fails). */
export type AndroidBuildFeaturesArgs = {
  okHttp: ResolvedOkHttpGradle;
  kotlin19Compat: boolean;
};

/** Android Gradle: Byte Buddy + Dream Horizon OkHttp instrumentation when `okHttpInstrumentation.enabled`. When `kotlin19Compat` is also on, additionally force `kotlin-stdlib` at consumer level so both flags coexist. */
export const withAndroidBuildFeatures: ConfigPlugin<
  AndroidBuildFeaturesArgs
> = (config, { okHttp, kotlin19Compat }) => {
  if (okHttp.enabled) {
    config = withPulseJetifierIgnoreByteBuddy(config, okHttp);
    config = withPulseProjectBuildGradle(config, okHttp);
    config = withPulseAppBuildGradle(config, okHttp, kotlin19Compat);
  }
  return config;
};

/**
 * Writes `PulseReactNativeOtel_kotlin19Compat=true` into `android/gradle.properties` so the SDK's
 * Gradle constraints block (which caps transitive Kotlin runtime artifacts to 1.9-compatible
 * versions) activates without consumers editing the file by hand. Removes the key when off.
 */
export const withAndroidKotlin19Compat: ConfigPlugin<boolean> = (
  config,
  enabled
) => {
  return withGradleProperties(config, (mod) => {
    mod.modResults = mergeKotlin19CompatFlag(
      mod.modResults as GradlePropertiesItem[],
      enabled
    ) as typeof mod.modResults;
    return mod;
  });
};
