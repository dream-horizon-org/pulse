# pulse-android-otel — Plan Handbook

Rebuild guide for the Pulse Android SDK. Each sub-file follows the standard nine-section template (Purpose · Source location · Public surface · Internal design · Dependencies · Data contracts · Tests · History/decisions · Rebuild recipe).

Component brief: `/Users/ujjwal.bagrania/Desktop/pulse/docs/components/pulse-android-otel.md`.

## Module map

| Layer | Plan file |
|---|---|
| Core: SDK bootstrap (`PulseSDK.INSTANCE`, init, consent, shutdown) | [core/sdk-bootstrap.md](core/sdk-bootstrap.md) |
| Core: Semantic conventions (`com.pulse.semconv`) | [core/semconv.md](core/semconv.md) |
| Core: Sampling (`pulse-sampling/*`) | [core/sampling.md](core/sampling.md) |
| Core: Exporter chain (`BufferDelegating*`, ToDisk, OTLP) | [core/exporter-chain.md](core/exporter-chain.md) |
| Instrumentation: crash | [instrumentations/crash.md](instrumentations/crash.md) |
| Instrumentation: ANR | [instrumentations/anr.md](instrumentations/anr.md) |
| Instrumentation: network | [instrumentations/network.md](instrumentations/network.md) |
| Instrumentation: sessions | [instrumentations/session.md](instrumentations/session.md) |
| Instrumentation: screens (activity/fragment/startup) | [instrumentations/screen.md](instrumentations/screen.md) |
| Instrumentation: interaction (view-click/compose/rage) | [instrumentations/interaction.md](instrumentations/interaction.md) |
| Instrumentation: app-lifecycle | [instrumentations/app-lifecycle.md](instrumentations/app-lifecycle.md) |

## Cross-cutting invariants

- Two namespaces, never mixed: `io.opentelemetry.android.*` (upstream + OTel-side instrumentations) and `com.pulse.*` (Pulse-specific glue, semconv, sampling, SDK facade).
- Every auto-instrumentation registers via `@AutoService(AndroidInstrumentation::class)`; the OTel `OpenTelemetryRum` builder discovers them at init.
- `PulseSDK.initialize` is idempotent — second calls are ignored (see `PulseSDK.kt` docstring).
- StrictMode-clean init: heavy work runs on a background executor, telemetry produced before exporters attach is held in `BufferDelegating*Exporter` (`docs/STRICTMODE.md`, `docs/EXPORTER_CHAIN.md`).
- Consent (`PulseDataCollectionConsent`) gates exporters at init and at runtime via `setDataCollectionState`.
