import {
  mergePulseCoreLibraryDesugaringCompileOptions,
  PULSE_ANDROID_CORE_DESUGARING_TAG,
} from '../androidDesugarGradleMerge';

const MINIMAL_ANDROID = `android {
    defaultConfig {
        applicationId 'x'
    }
}
`;

const ANDROID_WITH_COMPILE_OPTIONS = `android {
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    defaultConfig {
        applicationId 'x'
    }
}
`;

describe('mergePulseCoreLibraryDesugaringCompileOptions', () => {
  it('inserts a compileOptions block before defaultConfig when none exists', () => {
    const out = mergePulseCoreLibraryDesugaringCompileOptions(MINIMAL_ANDROID);
    expect(out).toContain('compileOptions');
    expect(out).toContain('coreLibraryDesugaringEnabled true');
    expect(out).toContain(
      `@generated begin ${PULSE_ANDROID_CORE_DESUGARING_TAG}`
    );
    expect(out.indexOf('compileOptions')).toBeLessThan(
      out.indexOf('defaultConfig')
    );
  });

  it('injects only the property inside an existing compileOptions block', () => {
    const out = mergePulseCoreLibraryDesugaringCompileOptions(
      ANDROID_WITH_COMPILE_OPTIONS
    );
    const compileOptionsCount = (out.match(/\bcompileOptions\s*\{/g) ?? [])
      .length;
    expect(compileOptionsCount).toBe(1);
    expect(out).toContain('sourceCompatibility JavaVersion.VERSION_17');
    expect(out).toContain('coreLibraryDesugaringEnabled true');
    expect(out).toContain(
      `@generated begin ${PULSE_ANDROID_CORE_DESUGARING_TAG}`
    );
  });

  it('does not duplicate when coreLibraryDesugaringEnabled is already set', () => {
    const already = `android {
    compileOptions {
        coreLibraryDesugaringEnabled true
        sourceCompatibility JavaVersion.VERSION_17
    }
    defaultConfig { }
}
`;
    const out = mergePulseCoreLibraryDesugaringCompileOptions(already);
    expect(
      (out.match(/coreLibraryDesugaringEnabled\s+true/g) ?? []).length
    ).toBe(1);
    expect(out).not.toContain(
      `@generated begin ${PULSE_ANDROID_CORE_DESUGARING_TAG}`
    );
  });

  it('is idempotent when run twice on the same merged output', () => {
    const once = mergePulseCoreLibraryDesugaringCompileOptions(MINIMAL_ANDROID);
    const twice = mergePulseCoreLibraryDesugaringCompileOptions(once);
    expect(twice).toEqual(once);
  });

  it('targets only the first compileOptions block when multiple exist', () => {
    const src = `android {
    compileOptions {
        firstMarker
    }
    compileOptions {
        secondMarker
    }
    defaultConfig {
        applicationId 'x'
    }
}
`;
    const out = mergePulseCoreLibraryDesugaringCompileOptions(src);
    expect((out.match(/\bcompileOptions\s*\{/g) ?? []).length).toBe(2);
    expect(out).toContain('firstMarker');
    expect(out).toContain('secondMarker');
    const genBegin = out.indexOf(
      `@generated begin ${PULSE_ANDROID_CORE_DESUGARING_TAG}`
    );
    expect(genBegin).toBeGreaterThan(-1);
    expect(genBegin).toBeLessThan(out.indexOf('secondMarker'));
    expect(out.slice(0, out.indexOf('secondMarker'))).toContain(
      'coreLibraryDesugaringEnabled true'
    );
  });

  it('throws ERR_NO_MATCH when no compileOptions and no defaultConfig anchor', () => {
    const src = `android {
    namespace 'com.example'
}
`;
    let caught: unknown;
    try {
      mergePulseCoreLibraryDesugaringCompileOptions(src);
    } catch (e) {
      caught = e;
    }
    expect(caught).toBeInstanceOf(Error);
    expect((caught as Error & { code?: string }).code).toBe('ERR_NO_MATCH');
  });

  it('still injects Groovy-style line when only Kotlin assignment = true is present', () => {
    const kotlinStyle = `android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        coreLibraryDesugaringEnabled = true
    }
    defaultConfig {
        applicationId 'x'
    }
}
`;
    const out = mergePulseCoreLibraryDesugaringCompileOptions(kotlinStyle);
    expect(out).toContain('coreLibraryDesugaringEnabled = true');
    expect(out).toContain('coreLibraryDesugaringEnabled true');
    expect(out).toContain(
      `@generated begin ${PULSE_ANDROID_CORE_DESUGARING_TAG}`
    );
  });

  it('adds true when existing compileOptions sets coreLibraryDesugaringEnabled false', () => {
    const src = `android {
    compileOptions {
        coreLibraryDesugaringEnabled false
        sourceCompatibility JavaVersion.VERSION_17
    }
    defaultConfig {
        applicationId 'x'
    }
}
`;
    const out = mergePulseCoreLibraryDesugaringCompileOptions(src);
    expect(out).toContain('coreLibraryDesugaringEnabled false');
    expect(out).toContain('coreLibraryDesugaringEnabled true');
    expect(out).toContain(
      `@generated begin ${PULSE_ANDROID_CORE_DESUGARING_TAG}`
    );
  });

  it('does not use compileOptions inside a block comment as the merge anchor', () => {
    const src = `android {
    /* compileOptions { not a real block */
    defaultConfig {
        applicationId 'x'
    }
}
`;
    const out = mergePulseCoreLibraryDesugaringCompileOptions(src);
    expect(out).toContain('/* compileOptions { not a real block */');
    expect(out).toContain('compileOptions');
    expect(out).toContain('coreLibraryDesugaringEnabled true');
    expect(out.indexOf('/* compileOptions')).toBeLessThan(
      out.indexOf('@generated begin')
    );
  });
});
