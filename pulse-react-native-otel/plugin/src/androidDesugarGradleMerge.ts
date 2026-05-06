import {
  mergeContents,
  removeContents,
} from '@expo/config-plugins/build/utils/generateCode';

/** Same tag as legacy `withAndroidPulse` injections (must stay stable). */
export const PULSE_ANDROID_CORE_DESUGARING_TAG =
  'pulse-android-core-library-desugaring';

const COMPILE_OPTIONS_OPEN = /^\s*compileOptions\s*\{/m;

const SINGLE_PROPERTY_SRC = '        coreLibraryDesugaringEnabled true';

const COMPILE_OPTIONS_BLOCK = `    compileOptions {
        coreLibraryDesugaringEnabled true
    }
`;

/**
 * Ensures `coreLibraryDesugaringEnabled true` is present without duplicating `compileOptions { }`.
 * - Strips any previous Pulse-generated block for this tag.
 * - If the file already enables core library desugaring (outside our block), does nothing else.
 * - If a `compileOptions {` block exists, injects only the property (tagged) inside it.
 * - Otherwise inserts a full `compileOptions` block before `defaultConfig`.
 */
export function mergePulseCoreLibraryDesugaringCompileOptions(
  src: string
): string {
  let working = removeContents({
    src,
    tag: PULSE_ANDROID_CORE_DESUGARING_TAG,
  }).contents;

  if (/\bcoreLibraryDesugaringEnabled\s+true\b/.test(working)) {
    return working;
  }

  if (COMPILE_OPTIONS_OPEN.test(working)) {
    return mergeContents({
      src: working,
      newSrc: `${SINGLE_PROPERTY_SRC}\n`,
      tag: PULSE_ANDROID_CORE_DESUGARING_TAG,
      comment: '//',
      anchor: COMPILE_OPTIONS_OPEN,
      offset: 1,
    }).contents;
  }

  return mergeContents({
    src: working,
    newSrc: COMPILE_OPTIONS_BLOCK,
    tag: PULSE_ANDROID_CORE_DESUGARING_TAG,
    comment: '//',
    anchor: /defaultConfig\s*\{/,
    offset: 0,
  }).contents;
}
