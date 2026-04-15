# Testing & Quality — Flow & Summary

A multi-layer test suite that locks in correctness from unit level through real-browser cross-platform E2E. Runs in parallel with V2 Phase 4 (backend-ui). Everything except BrowserStack runs on every PR.

---

## Flow

```mermaid
flowchart TD
    PR["PR opened"] --> UNIT["Unit tests\nVitest + jsdom\n< 10s"]
    PR --> INTEG["Integration tests\nVitest + jsdom\n< 20s"]
    PR --> SIZE["Bundle size check\nsize-limit\n< 5s\nfail if core > 30 KB"]
    PR --> E2E_CHROME["Playwright E2E\nChrome (headless)\n~2 min"]
    PR --> E2E_FF["Playwright E2E\nFirefox + WebKit\n~4 min"]

    UNIT & INTEG & SIZE & E2E_CHROME & E2E_FF -->|"all green"| MERGE["Merge to main"]

    MERGE --> BS["BrowserStack\niPhone Safari · Chrome Android\nWindows Chrome/Edge · macOS Safari\n~15 min"]
    MERGE --> LH["Lighthouse CI\nbundle impact tracking\n~5 min"]

    subgraph MOCK["Test Infrastructure"]
        OTLP["MockOtlpReceiver\n(captures spans/logs/metrics)\nassert signal correctness"]
        FAKE["vi.useFakeTimers()\ncontrol batch flush without real 5s waits"]
    end

    E2E_CHROME -.->|"uses"| OTLP
    UNIT -.->|"uses"| FAKE
```

---

## Test Coverage Map

| Layer | Tool | What's Tested |
|---|---|---|
| Unit | Vitest | Session rotation, resource attributes, config parsing, APDEX scoring, interaction state machine |
| Integration | Vitest + jsdom | SDK singleton lifecycle, instrumentation toggling, consent gating |
| E2E Chrome | Playwright | Full signal flow: click → app.click log, fetch → http span, error → device.crash |
| E2E Firefox/WebKit | Playwright | Cross-browser correctness; longtask expected to skip gracefully |
| BrowserStack | Playwright | Real devices: iPhone Safari, Chrome Android |
| Bundle size | size-limit | Core < 30 KB, replay entry < 85 KB |

---

## Sub-Documents

| File | What It Covers |
|---|---|
| [index.md](./index.md) | Full Vitest config, MockOtlpReceiver implementation, Playwright config, BrowserStack setup, Lighthouse CI, done criteria, known risks |

---

## Key Design Decisions

| Decision | Rationale |
|---|---|
| `MockOtlpReceiver` as assertion target | Tests verify actual wire-format output, not internal state — catches serialisation bugs |
| Fake timers in unit tests | Eliminates 5s waits for batch flush; makes tests deterministic and < 1s each |
| `waitForRequest` in Playwright (not `waitForTimeout`) | Avoids flakiness from timer-dependent assertions in real browser tests |
| BrowserStack only on merge to main | Too slow (15 min) for every PR; catches real-device issues without blocking developer flow |
| Coverage thresholds: 80% lines / 75% branches | Pragmatic — high enough to catch regressions, low enough to not require tests for trivial code |
