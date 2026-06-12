import {
  mergePulseOkHttpAppGradle,
  mergePulseOkHttpByteBuddyClasspath,
  PULSE_OKHTTP_TAG_KOTLIN19_STDLIB_FORCE,
  PULSE_OKHTTP_TAG_OKHTTP_DEPS,
} from '../androidOkHttpGradleMerge';

const ROOT_MINIMAL = `buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin")
    }
}
`;

const APP_MINIMAL = `apply plugin: "com.android.application"
apply plugin: "com.facebook.react"

dependencies {
    implementation("com.facebook.react:react-android")
    implementation jscFlavor
}
`;

describe('mergePulseOkHttpByteBuddyClasspath', () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('adds Byte Buddy classpath after Kotlin classpath', () => {
    const warn = jest.spyOn(console, 'warn').mockImplementation(() => {});
    const out = mergePulseOkHttpByteBuddyClasspath(ROOT_MINIMAL, '1.17.8');
    expect(out).toContain('byte-buddy-gradle-plugin:1.17.8');
    expect(out.indexOf('kotlin-gradle-plugin')).toBeLessThan(
      out.indexOf('byte-buddy-gradle-plugin')
    );
    expect(warn).not.toHaveBeenCalled();
  });

  it('skips merge and warns when Byte Buddy classpath already exists (any version)', () => {
    const warn = jest.spyOn(console, 'warn').mockImplementation(() => {});
    const already = `buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin")
        classpath("net.bytebuddy:byte-buddy-gradle-plugin:1.17.0")
    }
}
`;
    const out = mergePulseOkHttpByteBuddyClasspath(already, '1.17.8');
    expect(out).toEqual(already);
    expect(warn).toHaveBeenCalledWith(
      expect.stringMatching(/skipping Pulse classpath merge/i)
    );
  });

  it('skips merge and warns when classpath version already matches config', () => {
    const warn = jest.spyOn(console, 'warn').mockImplementation(() => {});
    const already = `buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin")
        classpath("net.bytebuddy:byte-buddy-gradle-plugin:1.17.8")
    }
}
`;
    const out = mergePulseOkHttpByteBuddyClasspath(already, '1.17.8');
    expect(out).toEqual(already);
    expect(warn).toHaveBeenCalled();
  });

  it('is idempotent when run twice', () => {
    jest.spyOn(console, 'warn').mockImplementation(() => {});
    const once = mergePulseOkHttpByteBuddyClasspath(ROOT_MINIMAL, '1.17.8');
    const twice = mergePulseOkHttpByteBuddyClasspath(once, '1.17.8');
    expect(twice).toEqual(once);
  });

  it('updates Pulse-tagged classpath when Byte Buddy version changes', () => {
    jest.spyOn(console, 'warn').mockImplementation(() => {});
    const once = mergePulseOkHttpByteBuddyClasspath(ROOT_MINIMAL, '1.17.0');
    expect(once).toContain('byte-buddy-gradle-plugin:1.17.0');
    const bumped = mergePulseOkHttpByteBuddyClasspath(once, '1.17.8');
    expect(bumped).toContain('byte-buddy-gradle-plugin:1.17.8');
    expect((bumped.match(/byte-buddy-gradle-plugin/g) ?? []).length).toBe(1);
  });
});

describe('mergePulseOkHttpAppGradle', () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('adds apply plugin, library, and agent after strip', () => {
    const warn = jest.spyOn(console, 'warn').mockImplementation(() => {});
    const out = mergePulseOkHttpAppGradle(APP_MINIMAL, '0.0.10-alpha');
    expect(out).toContain('net.bytebuddy.byte-buddy-gradle-plugin');
    expect(out).toContain(
      'org.dreamhorizon.instrumentation:okhttp3-library:0.0.10-alpha'
    );
    expect(out).toContain(
      'org.dreamhorizon.instrumentation:okhttp3-agent:0.0.10-alpha'
    );
    expect(out).toContain(`@generated begin ${PULSE_OKHTTP_TAG_OKHTTP_DEPS}`);
    expect(warn).not.toHaveBeenCalled();
  });

  it('skips dependency merge and warns when Dream Horizon deps already exist (same version)', () => {
    const warn = jest.spyOn(console, 'warn').mockImplementation(() => {});
    const manual = `${APP_MINIMAL.replace(
      'dependencies {',
      `dependencies {
    implementation("org.dreamhorizon.instrumentation:okhttp3-library:0.0.10-alpha")
    byteBuddy("org.dreamhorizon.instrumentation:okhttp3-agent:0.0.10-alpha")
`
    )}`;
    const out = mergePulseOkHttpAppGradle(manual, '0.0.10-alpha');
    expect(out).toEqual(manual);
    expect(warn).toHaveBeenCalledWith(
      expect.stringMatching(/skipping Pulse OkHttp Gradle edits/i)
    );
    expect(out).not.toContain(
      `@generated begin ${PULSE_OKHTTP_TAG_OKHTTP_DEPS}`
    );
  });

  it('skips dependency merge and warns when Dream Horizon deps already exist (older version)', () => {
    const warn = jest.spyOn(console, 'warn').mockImplementation(() => {});
    const manual = `${APP_MINIMAL.replace(
      'dependencies {',
      `dependencies {
    implementation("org.dreamhorizon.instrumentation:okhttp3-library:0.0.9-alpha")
    byteBuddy("org.dreamhorizon.instrumentation:okhttp3-agent:0.0.9-alpha")
`
    )}`;
    const out = mergePulseOkHttpAppGradle(manual, '0.0.10-alpha');
    expect(out).toEqual(manual);
    expect(out).toContain('0.0.9-alpha');
    expect(warn).toHaveBeenCalled();
  });

  it('replaces Pulse-tagged OkHttp version when target instrumentation version changes', () => {
    jest.spyOn(console, 'warn').mockImplementation(() => {});
    const v1 = mergePulseOkHttpAppGradle(APP_MINIMAL, '0.0.9-alpha');
    expect(v1).toContain('okhttp3-library:0.0.9-alpha');
    const v2 = mergePulseOkHttpAppGradle(v1, '0.0.10-alpha');
    expect(v2).toContain('okhttp3-library:0.0.10-alpha');
    expect(v2).not.toContain('okhttp3-library:0.0.9-alpha');
    expect(
      (v2.match(/org\.dreamhorizon\.instrumentation:okhttp3-library/g) ?? [])
        .length
    ).toBe(1);
  });

  it('is idempotent when run twice on merged output', () => {
    const warn = jest.spyOn(console, 'warn').mockImplementation(() => {});
    const once = mergePulseOkHttpAppGradle(APP_MINIMAL, '0.0.10-alpha');
    const twice = mergePulseOkHttpAppGradle(once, '0.0.10-alpha');
    expect(twice).toEqual(once);
    expect(warn).not.toHaveBeenCalled();
  });

  it('omits the kotlin-stdlib force block by default (kotlin19Compat off)', () => {
    jest.spyOn(console, 'warn').mockImplementation(() => {});
    const out = mergePulseOkHttpAppGradle(APP_MINIMAL, '0.0.10-alpha');
    expect(out).not.toContain(
      `@generated begin ${PULSE_OKHTTP_TAG_KOTLIN19_STDLIB_FORCE}`
    );
    expect(out).not.toContain('resolutionStrategy');
  });

  it('emits a kotlin-stdlib force block when kotlin19Compat is true', () => {
    jest.spyOn(console, 'warn').mockImplementation(() => {});
    const out = mergePulseOkHttpAppGradle(APP_MINIMAL, '0.0.10-alpha', true);
    expect(out).toContain(
      `@generated begin ${PULSE_OKHTTP_TAG_KOTLIN19_STDLIB_FORCE}`
    );
    expect(out).toContain('force "org.jetbrains.kotlin:kotlin-stdlib:1.9.25"');
    expect(out).toContain(
      'force "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.25"'
    );
    expect(out).toContain(
      'force "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.25"'
    );
    expect(out).toContain(
      'force "org.jetbrains.kotlin:kotlin-stdlib-common:1.9.25"'
    );
    // Force block must sit at top-level, not inside dependencies { ... }.
    const forceIdx = out.indexOf('resolutionStrategy');
    const depsIdx = out.indexOf('dependencies {');
    expect(forceIdx).toBeLessThan(depsIdx);
  });

  it('strips a stale force block when kotlin19Compat is toggled off', () => {
    jest.spyOn(console, 'warn').mockImplementation(() => {});
    const withForce = mergePulseOkHttpAppGradle(
      APP_MINIMAL,
      '0.0.10-alpha',
      true
    );
    expect(withForce).toContain('resolutionStrategy');
    const withoutForce = mergePulseOkHttpAppGradle(
      withForce,
      '0.0.10-alpha',
      false
    );
    expect(withoutForce).not.toContain(
      `@generated begin ${PULSE_OKHTTP_TAG_KOTLIN19_STDLIB_FORCE}`
    );
    expect(withoutForce).not.toContain('resolutionStrategy');
  });

  it('is idempotent when kotlin19Compat=true and run twice', () => {
    jest.spyOn(console, 'warn').mockImplementation(() => {});
    const once = mergePulseOkHttpAppGradle(APP_MINIMAL, '0.0.10-alpha', true);
    const twice = mergePulseOkHttpAppGradle(once, '0.0.10-alpha', true);
    expect(twice).toEqual(once);
    expect((twice.match(/resolutionStrategy/g) ?? []).length).toBe(1);
  });
});
