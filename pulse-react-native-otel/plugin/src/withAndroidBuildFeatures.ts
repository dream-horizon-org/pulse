import {
  withAppBuildGradle,
  withProjectBuildGradle,
} from '@expo/config-plugins';
import type { ConfigPlugin } from '@expo/config-plugins';
import { mergeContents } from '@expo/config-plugins/build/utils/generateCode';
import type { ExpoConfig } from '@expo/config-types';

import {
  PULSE_BYTE_BUDDY_GRADLE_PLUGIN,
  PULSE_DREAMHORIZON_OKHTTP_INSTR_VERSION,
} from './androidBuildConstants';
import type { PulseAndroidBuildOptions } from './types';

const TAG_BYTEBUDDY_ROOT = 'pulse-expo-android-byte-buddy-classpath';
const TAG_BYTEBUDDY_APPLY = 'pulse-expo-android-apply-byte-buddy';
const TAG_OKHTTP_DEPS = 'pulse-expo-android-okhttp-deps';

function withPulseProjectBuildGradle(
  config: ExpoConfig,
  { okHttpInstrumentation }: Required<PulseAndroidBuildOptions>
) {
  if (!okHttpInstrumentation) {
    return config;
  }
  return withProjectBuildGradle(config, (mod) => {
    if (mod.modResults.language && mod.modResults.language !== 'groovy') {
      return mod;
    }
    const src = mod.modResults.contents;
    if (src.includes('net.bytebuddy:byte-buddy-gradle-plugin')) {
      return mod;
    }
    try {
      const block = `
        classpath("net.bytebuddy:byte-buddy-gradle-plugin:${PULSE_BYTE_BUDDY_GRADLE_PLUGIN}")
`;
      mod.modResults.contents = mergeContents({
        src: mod.modResults.contents,
        newSrc: block,
        tag: TAG_BYTEBUDDY_ROOT,
        comment: '//',
        anchor:
          /classpath\(['"]org\.jetbrains\.kotlin:kotlin-gradle-plugin['"]\)/,
        offset: 1,
      }).contents;
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
  { okHttpInstrumentation }: Required<PulseAndroidBuildOptions>
) {
  if (!okHttpInstrumentation) {
    return config;
  }

  return withAppBuildGradle(config, (mod) => {
    if (mod.modResults.language && mod.modResults.language !== 'groovy') {
      return mod;
    }
    let { contents } = mod.modResults;

    if (!contents.includes('net.bytebuddy.byte-buddy-gradle-plugin')) {
      try {
        contents = mergeContents({
          src: contents,
          newSrc: `
apply plugin: "net.bytebuddy.byte-buddy-gradle-plugin"`,
          tag: TAG_BYTEBUDDY_APPLY,
          comment: '//',
          anchor: /apply plugin:\s*["']com\.facebook\.react["']/,
          offset: 1,
        }).contents;
      } catch (e) {
        console.error(
          'Pulse: could not apply Byte Buddy plugin in app/build.gradle.',
          e
        );
      }
    }

    if (!contents.includes(`@generated begin ${TAG_OKHTTP_DEPS}`)) {
      const lines = [
        `    implementation("org.dreamhorizon.instrumentation:okhttp3-library:${PULSE_DREAMHORIZON_OKHTTP_INSTR_VERSION}")`,
        `    byteBuddy("org.dreamhorizon.instrumentation:okhttp3-agent:${PULSE_DREAMHORIZON_OKHTTP_INSTR_VERSION}")`,
      ];
      try {
        if (/implementation jscFlavor/.test(contents)) {
          contents = mergeContents({
            src: contents,
            newSrc: lines.join('\n'),
            tag: TAG_OKHTTP_DEPS,
            comment: '//',
            anchor: /^\s*implementation jscFlavor\s*$/m,
            offset: 2,
          }).contents;
        } else {
          contents = mergeContents({
            src: contents,
            newSrc: lines.join('\n'),
            tag: TAG_OKHTTP_DEPS,
            comment: '//',
            anchor: /^dependencies \{/m,
            offset: 1,
          }).contents;
        }
      } catch (e) {
        console.error(
          'Pulse: could not add OkHttp instrumentation dependencies.',
          e
        );
      }
    }

    mod.modResults.contents = contents;
    return mod;
  });
}

/** Android Gradle: address additional dependencies required for instrumentations. for eg: OkHttp/Byte Buddy instrumentation. */
export const withAndroidBuildFeatures: ConfigPlugin<
  Required<PulseAndroidBuildOptions>
> = (config, flags) => {
  if (flags.okHttpInstrumentation) {
    config = withPulseProjectBuildGradle(config, flags);
    config = withPulseAppBuildGradle(config, flags);
  }
  return config;
};
