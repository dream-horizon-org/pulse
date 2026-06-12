import {
  mergeContents,
  removeContents,
} from '@expo/config-plugins/build/utils/generateCode';

/** Stable tags for Expo prebuild idempotency (must match historical injections). */
export const PULSE_OKHTTP_TAG_BYTEBUDDY_ROOT =
  'pulse-expo-android-byte-buddy-classpath';
export const PULSE_OKHTTP_TAG_BYTEBUDDY_APPLY =
  'pulse-expo-android-apply-byte-buddy';
export const PULSE_OKHTTP_TAG_OKHTTP_DEPS = 'pulse-expo-android-okhttp-deps';
export const PULSE_OKHTTP_TAG_KOTLIN19_STDLIB_FORCE =
  'pulse-expo-android-kotlin19-stdlib-force';

/**
 * Pinned Kotlin stdlib version emitted by the force block when both
 * `okHttpInstrumentation` and `kotlin19Compat` are enabled. Highest patch in
 * the 1.9 line; sits inside the SDK's `[1.9, 2.1)` cap.
 */
const KOTLIN19_STDLIB_FORCE_VERSION = '1.9.25';

const KOTLIN_GRADLE_PLUGIN_CLASSPATH =
  /classpath\(['"]org\.jetbrains\.kotlin:kotlin-gradle-plugin['"]\)/;
const REACT_APPLY_PLUGIN = /apply plugin:\s*["']com\.facebook\.react["']/;
const JSC_FLAVOR_LINE = /^\s*implementation jscFlavor\s*$/m;
const DEPENDENCIES_OPEN = /^dependencies \{/m;

const BYTE_BUDDY_CLASSPATH_COORD = /net\.bytebuddy:byte-buddy-gradle-plugin/;
/** Groovy `apply plugin` or Kotlin DSL `id(...)` / `id "..."` for Byte Buddy. */
const BYTE_BUDDY_ALREADY_APPLIED =
  /net\.bytebuddy\.byte-buddy-gradle-plugin|id\s*\(\s*["']net\.bytebuddy\.byte-buddy-gradle-plugin["']\s*\)|id\s+["']net\.bytebuddy\.byte-buddy-gradle-plugin["']/;

const DREAMHORIZON_OKHTTP_LIBRARY =
  /org\.dreamhorizon\.instrumentation:okhttp3-library/;
const DREAMHORIZON_OKHTTP_AGENT =
  /org\.dreamhorizon\.instrumentation:okhttp3-agent/;

/**
 * `android/build.gradle`: root `buildscript` classpath for Byte Buddy Gradle plugin.
 * - Strips any previous Pulse-generated block for this tag (`removeContents` only).
 * - If a Byte Buddy Gradle plugin classpath entry already exists, logs a warning and returns unchanged.
 * - Otherwise inserts one tagged line after the Kotlin Gradle plugin classpath.
 */
export function mergePulseOkHttpByteBuddyClasspath(
  src: string,
  byteBuddyPluginVersion: string
): string {
  let working = removeContents({
    src,
    tag: PULSE_OKHTTP_TAG_BYTEBUDDY_ROOT,
  }).contents;

  if (BYTE_BUDDY_CLASSPATH_COORD.test(working)) {
    console.warn(
      'Pulse: net.bytebuddy:byte-buddy-gradle-plugin is already on the buildscript classpath; skipping Pulse classpath merge. Remove duplicates or edit the version in android/build.gradle yourself.'
    );
    return working;
  }

  return mergeContents({
    src: working,
    newSrc: `
        classpath("net.bytebuddy:byte-buddy-gradle-plugin:${byteBuddyPluginVersion}")
`,
    tag: PULSE_OKHTTP_TAG_BYTEBUDDY_ROOT,
    comment: '//',
    anchor: KOTLIN_GRADLE_PLUGIN_CLASSPATH,
    offset: 1,
  }).contents;
}

/**
 * `android/app/build.gradle`: `apply plugin` for Byte Buddy + Dream Horizon OkHttp deps.
 * - Strips prior Pulse-generated blocks for these tags (`removeContents` only).
 * - If both Dream Horizon dependency lines already exist, logs a warning and returns the file unchanged (no apply / no deps merge).
 * - Otherwise adds tagged `apply plugin` when Byte Buddy is not already applied, then merges tagged `implementation` + `byteBuddy` lines.
 */
export function mergePulseOkHttpAppGradle(
  src: string,
  okhttpLibraryVersion: string,
  kotlin19Compat: boolean = false
): string {
  let working = removeContents({
    src,
    tag: PULSE_OKHTTP_TAG_BYTEBUDDY_APPLY,
  }).contents;
  working = removeContents({
    src: working,
    tag: PULSE_OKHTTP_TAG_OKHTTP_DEPS,
  }).contents;
  // Always strip any prior force block first so toggling `kotlin19Compat` off
  // cleans up after itself on the next prebuild.
  working = removeContents({
    src: working,
    tag: PULSE_OKHTTP_TAG_KOTLIN19_STDLIB_FORCE,
  }).contents;

  if (
    DREAMHORIZON_OKHTTP_LIBRARY.test(working) &&
    DREAMHORIZON_OKHTTP_AGENT.test(working)
  ) {
    console.warn(
      'Pulse: Dream Horizon okhttp3-library / okhttp3-agent dependencies already present in app build.gradle; skipping Pulse OkHttp Gradle edits for this file. Align versions with app.json or remove duplicates manually.'
    );
    return working;
  }

  if (!BYTE_BUDDY_ALREADY_APPLIED.test(working)) {
    working = mergeContents({
      src: working,
      newSrc: `
apply plugin: "net.bytebuddy.byte-buddy-gradle-plugin"`,
      tag: PULSE_OKHTTP_TAG_BYTEBUDDY_APPLY,
      comment: '//',
      anchor: REACT_APPLY_PLUGIN,
      offset: 1,
    }).contents;
  }

  const newSrc = [
    `    implementation("org.dreamhorizon.instrumentation:okhttp3-library:${okhttpLibraryVersion}")`,
    `    byteBuddy("org.dreamhorizon.instrumentation:okhttp3-agent:${okhttpLibraryVersion}")`,
  ].join('\n');

  if (JSC_FLAVOR_LINE.test(working)) {
    working = mergeContents({
      src: working,
      newSrc,
      tag: PULSE_OKHTTP_TAG_OKHTTP_DEPS,
      comment: '//',
      anchor: JSC_FLAVOR_LINE,
      offset: 2,
    }).contents;
  } else {
    working = mergeContents({
      src: working,
      newSrc,
      tag: PULSE_OKHTTP_TAG_OKHTTP_DEPS,
      comment: '//',
      anchor: DEPENDENCIES_OPEN,
      offset: 1,
    }).contents;
  }

  // When `kotlin19Compat` is also on, the okhttp3-library AAR's transitive
  // `kotlin-stdlib:2.1.x` request lands on a `:app` path that bypasses the
  // SDK module's strict `[1.9, 2.1)` constraint, and Gradle errors out
  // ("Cannot find a version of kotlin-stdlib that satisfies the version
  // constraints"). Pin the stdlib at consumer level so both flags coexist.
  // No effect when `kotlin19Compat` is off.
  if (kotlin19Compat) {
    const forceBlock = `
configurations.all {
    resolutionStrategy {
        force "org.jetbrains.kotlin:kotlin-stdlib:${KOTLIN19_STDLIB_FORCE_VERSION}"
        force "org.jetbrains.kotlin:kotlin-stdlib-jdk7:${KOTLIN19_STDLIB_FORCE_VERSION}"
        force "org.jetbrains.kotlin:kotlin-stdlib-jdk8:${KOTLIN19_STDLIB_FORCE_VERSION}"
        force "org.jetbrains.kotlin:kotlin-stdlib-common:${KOTLIN19_STDLIB_FORCE_VERSION}"
    }
}`;
    working = mergeContents({
      src: working,
      newSrc: forceBlock,
      tag: PULSE_OKHTTP_TAG_KOTLIN19_STDLIB_FORCE,
      comment: '//',
      anchor: REACT_APPLY_PLUGIN,
      offset: 1,
    }).contents;
  }

  return working;
}
