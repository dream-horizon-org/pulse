---
paths:
  - "pulse-android-otel/**/*.kt"
---

# Android SDK Conventions

## Tech Stack

Kotlin, OpenTelemetry Android SDK, Gradle multi-module, min API 21

## Package Roots (never mix)

- `io.opentelemetry.android.*` — OTel-upstream instrumentations and internals
- `com.pulse.*` — Pulse-specific API, semconv, sampling, utilities

## Instrumentation Modules (`instrumentation/`)

**Flat modules** (simple): `activity/`, `fragment/`, `anr/`, `crash/`, `network/`, `view-click/`, `compose/click/`

**Multi-submodule** (complex): `library/`, `agent/`, `testing/` pattern for bytecode instrumentation; `core/library/remote` for distinct API clients.

Use `@AutoService(AndroidInstrumentation::class)` for automatic discovery — no manual registration.

## Span Types (`pulse.type`)

`interaction` · `screen_session` · `screen_load` · `app_start` · `device.anr` · `device.crash` · `app.jank.frozen` · `app.jank.slow` · `network.*` · `session.start` · `session.end` · `app.click`

## Key Span Attributes

- `pulse.type`, `pulse.interaction.name`, `screen.name`
- `app.build_name` → `AppVersion` in ClickHouse
- `os.name` → `Platform`, `device.model.name` → `DeviceModel`

## Code Style

- `camelCase` functions/properties, `PascalCase` classes
- `internal` visibility for non-API classes
- `@JvmOverloads` / `@JvmStatic` where Java interop needed
- No wildcard imports
- Formatting enforced by Spotless (`otel.spotless-conventions`)
