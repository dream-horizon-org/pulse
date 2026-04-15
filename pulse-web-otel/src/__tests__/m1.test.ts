import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { getOrCreateInstallationId, SessionProvider, wasNewInstallation, _resetInstallationStateForTesting } from '../session';
import { validateConfig } from '../config';
import { buildResource, extractProjectId } from '../resource';
import { SdkConfigFetcher, DEFAULT_SDK_CONFIG, resolveConfigUrl } from '../remote-config';
import { FeatureGate } from '../feature-gate';
import type { PulseWebConfig } from '../config';
import type { PulseSdkConfig } from '../remote-config';

// Mock the exporters module to avoid real OTLP network calls in tests
vi.mock('../exporters', () => {
  const mockProvider = {
    addSpanProcessor: vi.fn(),
    getTracer: vi.fn().mockReturnValue({
      startSpan: vi.fn().mockReturnValue({
        setAttribute: vi.fn(),
        end: vi.fn(),
      }),
    }),
    forceFlush: vi.fn().mockResolvedValue(undefined),
    shutdown: vi.fn().mockResolvedValue(undefined),
    register: vi.fn(),
  };
  const mockLoggerProvider = {
    addLogRecordProcessor: vi.fn(),
    getLogger: vi.fn().mockReturnValue({
      emit: vi.fn(),
    }),
    forceFlush: vi.fn().mockResolvedValue(undefined),
    shutdown: vi.fn().mockResolvedValue(undefined),
  };
  const mockMeterProvider = {
    addMetricReader: vi.fn(),
    getMeter: vi.fn().mockReturnValue({}),
    forceFlush: vi.fn().mockResolvedValue(undefined),
    shutdown: vi.fn().mockResolvedValue(undefined),
  };

  return {
    createProviders: vi.fn().mockReturnValue({
      tracerProvider: mockProvider,
      loggerProvider: mockLoggerProvider,
      meterProvider: mockMeterProvider,
    }),
  };
});

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeConfig(overrides: Partial<PulseWebConfig> = {}): PulseWebConfig {
  return {
    endpointBaseUrl: 'https://collector.example.com',
    apiKey: 'proj_abc_supersecretkey',
    serviceName: 'test-app',
    ...overrides,
  };
}

function makeStorageThrowingMock() {
  return {
    getItem: vi.fn(() => { throw new Error('storage unavailable'); }),
    setItem: vi.fn(() => { throw new Error('storage unavailable'); }),
    removeItem: vi.fn(() => { throw new Error('storage unavailable'); }),
    clear: vi.fn(),
    length: 0,
    key: vi.fn(),
  } as unknown as Storage;
}

// ---------------------------------------------------------------------------
// M1 — Installation ID
// ---------------------------------------------------------------------------

describe('M1 — Installation ID', () => {
  let originalLocalStorage: Storage;
  let originalSessionStorage: Storage;

  beforeEach(() => {
    originalLocalStorage = window.localStorage;
    originalSessionStorage = window.sessionStorage;
    // Reset in-memory fallback by clearing storage state
    window.localStorage.clear();
    window.sessionStorage.clear();
  });

  afterEach(() => {
    Object.defineProperty(window, 'localStorage', { value: originalLocalStorage, writable: true });
    Object.defineProperty(window, 'sessionStorage', { value: originalSessionStorage, writable: true });
    vi.restoreAllMocks();
  });

  it('creates and persists installation ID in localStorage', () => {
    const id1 = getOrCreateInstallationId();
    expect(id1).toBeTruthy();
    expect(typeof id1).toBe('string');
    expect(id1.length).toBeGreaterThan(0);

    // Second call should return same ID
    const id2 = getOrCreateInstallationId();
    expect(id2).toBe(id1);

    // Should be in localStorage
    expect(window.localStorage.getItem('pulse_installation_id')).toBe(id1);
  });

  it('falls back to sessionStorage when localStorage throws', () => {
    const throwingLocal = makeStorageThrowingMock();
    Object.defineProperty(window, 'localStorage', { value: throwingLocal, writable: true });

    const id = getOrCreateInstallationId();
    expect(id).toBeTruthy();
    expect(typeof id).toBe('string');

    // Should be in sessionStorage
    expect(window.sessionStorage.getItem('pulse_installation_id')).toBe(id);
  });

  it('falls back to memory when both storages throw', () => {
    const throwingLocal = makeStorageThrowingMock();
    const throwingSession = makeStorageThrowingMock();

    Object.defineProperty(window, 'localStorage', { value: throwingLocal, writable: true });
    Object.defineProperty(window, 'sessionStorage', { value: throwingSession, writable: true });

    const id = getOrCreateInstallationId();
    expect(id).toBeTruthy();
    expect(typeof id).toBe('string');
  });

  it('returns same ID on repeated calls', () => {
    const id1 = getOrCreateInstallationId();
    const id2 = getOrCreateInstallationId();
    const id3 = getOrCreateInstallationId();
    expect(id1).toBe(id2);
    expect(id2).toBe(id3);
  });
});

// ---------------------------------------------------------------------------
// M1 — Session Provider
// ---------------------------------------------------------------------------

describe('M1 — Session Provider', () => {
  beforeEach(() => {
    window.sessionStorage.clear();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    window.sessionStorage.clear();
  });

  it('creates a valid UUID session ID on first call', () => {
    const provider = new SessionProvider();
    const id = provider.getSessionId();
    // UUID v4 format
    expect(id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i);
  });

  it('rotates session after inactivity timeout', () => {
    const timeoutMs = 1000;
    const provider = new SessionProvider(timeoutMs);

    const firstId = provider.getSessionId();
    expect(firstId).toBeTruthy();

    // Advance time past timeout
    vi.advanceTimersByTime(timeoutMs + 100);

    const secondId = provider.getSessionId();
    expect(secondId).not.toBe(firstId);
  });

  it('sets previousSessionId on rotation', () => {
    const timeoutMs = 1000;
    const provider = new SessionProvider(timeoutMs);

    let capturedPreviousId: string | undefined;
    provider.onSessionChange((event) => {
      if (event.type === 'start' && capturedPreviousId === undefined) {
        // Skip first start (sdk_init), capture the rotation start
        capturedPreviousId = 'FIRST_SEEN';
      } else if (event.type === 'start') {
        capturedPreviousId = event.previousSessionId;
      }
    });

    const firstId = provider.getSessionId();

    // Advance time past timeout
    vi.advanceTimersByTime(timeoutMs + 100);

    // Trigger rotation
    provider.getSessionId();

    // The rotation emits a start event with previousSessionId set
    expect(capturedPreviousId).toBe(firstId);
  });

  it('does not emit session.end on BFCache pagehide (persisted=true)', () => {
    const provider = new SessionProvider();
    const sessionId = provider.getSessionId();

    const endEvents: string[] = [];
    provider.onSessionChange((event) => {
      if (event.type === 'end') endEvents.push(event.sessionId ?? '');
    });

    // Simulate BFCache pagehide with persisted=true
    const pagehideEvent = new PageTransitionEvent('pagehide', { persisted: true });
    window.dispatchEvent(pagehideEvent);

    // Should NOT have emitted session.end
    expect(endEvents).toHaveLength(0);

    // Session ID should still be valid
    // (activity was updated, not ended)
    expect(sessionId).toBeTruthy();
  });
});

// ---------------------------------------------------------------------------
// M1 — Config validation
// ---------------------------------------------------------------------------

describe('M1 — Config validation', () => {
  it('throws when endpointBaseUrl is missing', () => {
    expect(() => validateConfig(makeConfig({ endpointBaseUrl: '' }))).toThrow(
      '[PulseWeb] endpointBaseUrl is required',
    );
  });

  it('throws when apiKey is missing', () => {
    expect(() => validateConfig(makeConfig({ apiKey: '' }))).toThrow(
      '[PulseWeb] apiKey is required',
    );
  });

  it('throws when serviceName is missing', () => {
    expect(() => validateConfig(makeConfig({ serviceName: '' }))).toThrow(
      '[PulseWeb] serviceName is required',
    );
  });

  it('does not throw with all required fields', () => {
    expect(() => validateConfig(makeConfig())).not.toThrow();
  });
});

// ---------------------------------------------------------------------------
// M1 — Resource builder
// ---------------------------------------------------------------------------

describe('M1 — Resource builder', () => {
  beforeEach(() => {
    window.localStorage.clear();
    window.sessionStorage.clear();
  });

  it('includes platform=web', () => {
    const resource = buildResource(makeConfig());
    expect(resource.attributes['platform']).toBe('web');
  });

  it('includes rum.sdk.name=pulse_web_js', () => {
    const resource = buildResource(makeConfig());
    expect(resource.attributes['rum.sdk.name']).toBe('pulse_web_js');
  });

  it('includes service.name from config', () => {
    const resource = buildResource(makeConfig({ serviceName: 'my-shop' }));
    expect(resource.attributes['service.name']).toBe('my-shop');
  });

  it('extracts project.id from api key', () => {
    const config = makeConfig({ apiKey: 'proj_abc123_secrettoken' });
    const resource = buildResource(config);
    expect(resource.attributes['project.id']).toBe('proj_abc123');
  });
});

// ---------------------------------------------------------------------------
// M1 — SDK singleton guard
// ---------------------------------------------------------------------------

describe('M1 — SDK singleton guard', () => {
  beforeEach(() => {
    // Mock fetch to prevent real network calls during SDK init background fetch
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      status: 404,
      json: () => Promise.resolve({}),
    }));

    // Mock XMLHttpRequest to prevent OTLP exporter from making network calls
    const mockXHR = {
      open: vi.fn(),
      send: vi.fn(),
      setRequestHeader: vi.fn(),
      abort: vi.fn(),
      readyState: 4,
      status: 200,
      responseText: '',
      onreadystatechange: null,
      onload: null,
      onerror: null,
      ontimeout: null,
      timeout: 0,
      withCredentials: false,
      upload: { addEventListener: vi.fn() },
    };
    vi.stubGlobal('XMLHttpRequest', vi.fn(() => mockXHR));

    window.localStorage.clear();
    window.sessionStorage.clear();
  });

  afterEach(async () => {
    // Import PulseWeb fresh each test via dynamic import to test singleton
    const { PulseWeb } = await import('../sdk');
    if (PulseWeb.isInitialized()) {
      await PulseWeb.shutdown();
    }
    vi.unstubAllGlobals();
  });

  it('second start() call is a no-op', async () => {
    const { PulseWeb } = await import('../sdk');
    const config = makeConfig();

    PulseWeb.start(config);
    expect(PulseWeb.isInitialized()).toBe(true);

    // Second call should be no-op
    PulseWeb.start(config);
    expect(PulseWeb.isInitialized()).toBe(true);
  });

  it('shutdown() allows re-initialization after complete', async () => {
    const { PulseWeb } = await import('../sdk');
    const config = makeConfig();

    PulseWeb.start(config);
    expect(PulseWeb.isInitialized()).toBe(true);

    await PulseWeb.shutdown();
    expect(PulseWeb.isInitialized()).toBe(false);

    // Should be able to re-initialize
    PulseWeb.start(config);
    expect(PulseWeb.isInitialized()).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// M1 — resolveConfigUrl
// ---------------------------------------------------------------------------

describe('M1 — resolveConfigUrl', () => {
  it('replaces :4318 with :8080 when no explicit configEndpointUrl', () => {
    expect(resolveConfigUrl(undefined, 'http://localhost:4318'))
      .toBe('http://localhost:8080/v1/configs/active/');
  });

  it('uses explicit configEndpointUrl as-is when provided', () => {
    expect(resolveConfigUrl('https://api.example.com/v1/configs/active/', 'http://localhost:4318'))
      .toBe('https://api.example.com/v1/configs/active/');
  });

  it('leaves non-4318 URLs unchanged', () => {
    expect(resolveConfigUrl(undefined, 'https://ingest.pulse.io'))
      .toBe('https://ingest.pulse.io/v1/configs/active/');
  });
});

// ---------------------------------------------------------------------------
// M1 — SdkConfigFetcher
// ---------------------------------------------------------------------------

describe('M1 — SdkConfigFetcher', () => {
  const CACHE_KEY = 'pulse_sdk_config';

  beforeEach(() => {
    window.localStorage.clear();
  });

  afterEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
  });

  it('loads cached config from localStorage', () => {
    const cachedConfig: PulseSdkConfig = {
      ...DEFAULT_SDK_CONFIG,
      version: 42,
      description: 'cached',
    };

    window.localStorage.setItem(CACHE_KEY, JSON.stringify(cachedConfig));

    const fetcher = new SdkConfigFetcher('https://api.example.com', 'proj_abc');
    const config = fetcher.loadCached();

    expect(config.version).toBe(42);
    expect(config.description).toBe('cached');
  });

  it('persists fetched config when version changes', async () => {
    const newConfig: PulseSdkConfig = {
      ...DEFAULT_SDK_CONFIG,
      version: 10,
    };

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(newConfig),
    }));

    const fetcher = new SdkConfigFetcher('https://api.example.com', 'proj_abc');
    fetcher.loadCached(); // version -1 (default)

    await fetcher.fetchInBackground();

    const stored = window.localStorage.getItem(CACHE_KEY);
    expect(stored).not.toBeNull();
    const parsed = JSON.parse(stored!) as PulseSdkConfig;
    expect(parsed.version).toBe(10);
  });

  it('skips write when version is same', async () => {
    const existingConfig: PulseSdkConfig = {
      ...DEFAULT_SDK_CONFIG,
      version: 5,
    };

    window.localStorage.setItem(CACHE_KEY, JSON.stringify(existingConfig));

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(existingConfig), // same version
    }));

    const fetcher = new SdkConfigFetcher('https://api.example.com', 'proj_abc');
    fetcher.loadCached();

    const setItemSpy = vi.spyOn(window.localStorage, 'setItem');

    await fetcher.fetchInBackground();

    // Should not have written again (same version)
    expect(setItemSpy).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------
// M1 — FeatureGate
// ---------------------------------------------------------------------------

describe('M1 — FeatureGate', () => {
  it('returns true for features not in config (default enabled)', () => {
    const config: PulseSdkConfig = {
      ...DEFAULT_SDK_CONFIG,
      features: [], // empty
    };

    const gate = new FeatureGate(config);
    expect(gate.isEnabled('session')).toBe(true);
    expect(gate.isEnabled('js_crash')).toBe(true);
    expect(gate.isEnabled('web_vitals')).toBe(true);
  });

  it('returns false when sessionSampleRate is 0', () => {
    const config: PulseSdkConfig = {
      ...DEFAULT_SDK_CONFIG,
      features: [
        {
          featureName: 'session',
          sessionSampleRate: 0,
          sdks: ['pulse_web_js'],
        },
      ],
    };

    const gate = new FeatureGate(config);
    expect(gate.isEnabled('session')).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// M1 — wasNewInstallation
// ---------------------------------------------------------------------------

describe('M1 — wasNewInstallation', () => {
  let originalLocalStorage: Storage;
  let originalSessionStorage: Storage;

  beforeEach(() => {
    originalLocalStorage = window.localStorage;
    originalSessionStorage = window.sessionStorage;
  });

  afterEach(() => {
    Object.defineProperty(window, 'localStorage', { value: originalLocalStorage, writable: true, configurable: true });
    Object.defineProperty(window, 'sessionStorage', { value: originalSessionStorage, writable: true, configurable: true });
    vi.restoreAllMocks();
  });

  it('returns true when localStorage is empty (fresh install)', () => {
    _resetInstallationStateForTesting();
    window.localStorage.clear();
    window.sessionStorage.clear();

    getOrCreateInstallationId();

    expect(wasNewInstallation()).toBe(true);
  });

  it('returns false when installation ID already in localStorage (returning user)', () => {
    _resetInstallationStateForTesting();
    window.localStorage.setItem('pulse_installation_id', 'existing-uuid');

    getOrCreateInstallationId();

    expect(wasNewInstallation()).toBe(false);
  });

  it('returns false when installation ID already in sessionStorage (localStorage unavailable)', () => {
    _resetInstallationStateForTesting();

    const throwingLocal = {
      getItem: vi.fn(() => { throw new Error('storage unavailable'); }),
      setItem: vi.fn(() => { throw new Error('storage unavailable'); }),
      removeItem: vi.fn(() => { throw new Error('storage unavailable'); }),
      clear: vi.fn(),
      length: 0,
      key: vi.fn(),
    } as unknown as Storage;
    Object.defineProperty(window, 'localStorage', { value: throwingLocal, writable: true, configurable: true });

    window.sessionStorage.setItem('pulse_installation_id', 'existing-uuid');

    getOrCreateInstallationId();

    expect(wasNewInstallation()).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// M1 — SDK public API signals
// ---------------------------------------------------------------------------

// Helper: build a mock provider bundle with a custom emitSpy for the logger.
function makeMockBundle(emitSpy: ReturnType<typeof vi.fn>) {
  return {
    tracerProvider: {
      addSpanProcessor: vi.fn(),
      getTracer: vi.fn().mockReturnValue({
        startSpan: vi.fn().mockReturnValue({ setAttribute: vi.fn(), end: vi.fn() }),
      }),
      forceFlush: vi.fn().mockResolvedValue(undefined),
      shutdown: vi.fn().mockResolvedValue(undefined),
      register: vi.fn(),
    },
    loggerProvider: {
      addLogRecordProcessor: vi.fn(),
      getLogger: vi.fn().mockReturnValue({ emit: emitSpy }),
      forceFlush: vi.fn().mockResolvedValue(undefined),
      shutdown: vi.fn().mockResolvedValue(undefined),
    },
    meterProvider: {
      addMetricReader: vi.fn(),
      getMeter: vi.fn().mockReturnValue({}),
      forceFlush: vi.fn().mockResolvedValue(undefined),
      shutdown: vi.fn().mockResolvedValue(undefined),
    },
  };
}

describe('M1 — SDK public API signals', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      status: 404,
      json: () => Promise.resolve({}),
    }));

    const mockXHR = {
      open: vi.fn(),
      send: vi.fn(),
      setRequestHeader: vi.fn(),
      abort: vi.fn(),
      readyState: 4,
      status: 200,
      responseText: '',
      onreadystatechange: null,
      onload: null,
      onerror: null,
      ontimeout: null,
      timeout: 0,
      withCredentials: false,
      upload: { addEventListener: vi.fn() },
    };
    vi.stubGlobal('XMLHttpRequest', vi.fn(() => mockXHR));

    window.localStorage.clear();
    window.sessionStorage.clear();
  });

  afterEach(async () => {
    const { PulseWeb } = await import('../sdk');
    if (PulseWeb.isInitialized()) {
      await PulseWeb.shutdown();
    }
    vi.unstubAllGlobals();
  });

  it('reportException emits log with body = error message', async () => {
    const emitSpy = vi.fn();
    // Override the module-level vi.mock for this one call
    const { createProviders } = await import('../exporters');
    vi.mocked(createProviders).mockReturnValueOnce(
      makeMockBundle(emitSpy) as unknown as ReturnType<typeof createProviders>,
    );

    const { PulseWeb } = await import('../sdk');
    PulseWeb.start(makeConfig());

    // Clear calls from sdk.init and session.start that happen during start()
    emitSpy.mockClear();

    PulseWeb.reportException(new Error('something broke'));

    expect(emitSpy).toHaveBeenCalled();
    const call = emitSpy.mock.calls[0]?.[0] as { body: string; attributes: Record<string, unknown> };
    expect(call.body).toBe('something broke');
    expect(call.attributes['pulse.type']).toBe('non_fatal');
    expect(call.attributes['exception.type']).toBe('Error');
    expect(call.attributes['non_fatal.is_manual']).toBe(true);
  });

  it('trackNonFatal emits non_fatal log with name as body', async () => {
    const emitSpy = vi.fn();
    const { createProviders } = await import('../exporters');
    vi.mocked(createProviders).mockReturnValueOnce(
      makeMockBundle(emitSpy) as unknown as ReturnType<typeof createProviders>,
    );

    const { PulseWeb } = await import('../sdk');
    PulseWeb.start(makeConfig());

    emitSpy.mockClear();

    PulseWeb.trackNonFatal('payment_declined', { amount: 99 });

    expect(emitSpy).toHaveBeenCalled();
    const call = emitSpy.mock.calls[0]?.[0] as { body: string; attributes: Record<string, unknown> };
    expect(call.body).toBe('payment_declined');
    expect(call.attributes['pulse.type']).toBe('non_fatal');
    expect(call.attributes['non_fatal.type']).toBe('payment_declined');
    expect(call.attributes['non_fatal.is_manual']).toBe(true);
  });

  it('trackEvent emits custom_event log (not span)', async () => {
    const emitSpy = vi.fn();
    const { createProviders } = await import('../exporters');
    vi.mocked(createProviders).mockReturnValueOnce(
      makeMockBundle(emitSpy) as unknown as ReturnType<typeof createProviders>,
    );

    const { PulseWeb } = await import('../sdk');
    PulseWeb.start(makeConfig());

    emitSpy.mockClear();

    PulseWeb.trackEvent('shop_now_click');

    expect(emitSpy).toHaveBeenCalled();
    const call = emitSpy.mock.calls[0]?.[0] as { body: string; attributes: Record<string, unknown> };
    expect(call.attributes['pulse.type']).toBe('custom_event');
    expect(call.attributes['event.name']).toBe('pulse.custom_event');
  });
});
