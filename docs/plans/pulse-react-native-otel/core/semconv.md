# RN · Semconv

Attribute keys and `pulse.type` enum the RN SDK emits. Must stay in lockstep with web / Android / iOS.

Brief: [../../../components/pulse-react-native-otel.md](../../../components/pulse-react-native-otel.md) · Peers: [facade](./facade.md).

## Source location

- `pulse-react-native-otel/src/pulse.constants.ts` — constants (keys + type values).
- `pulse-react-native-otel/src/pulse.interface.ts` — TS types.
- `pulse-react-native-otel/src/globalAttributes.ts` — global-attr merging.

## `pulse.type` values

| Value | Signal kind | Emitted by |
|---|---|---|
| `session.start` | log | session instr |
| `session.end` | log | session instr |
| `device.crash` | log | native crash bridge |
| `non_fatal` | log | `Pulse.captureError`, ErrorBoundary |
| `http` | span | network interceptor |
| `app.click` | log | interaction instr |
| `screen_load` | span | screen instr |
| `screen_interactive` | span | screen instr |
| `screen_session` | span | screen instr |

## Mandatory attributes (every signal)

- `platform = react-native`
- `session.id`
- `project.id` (resource attr, set from config)
- `app.build_name`
- `installation.id`

## Tests

`src/__tests__/semconv.test.ts` (if present) compares emitted shapes against fixtures.

## History / decisions

Parity table reviewed every time a new `pulse.type` is added to any SDK. Ownership: [web-sdk](../../pulse-web-otel/core/semconv.md) is the canonical source; mobile SDKs mirror.

## Rebuild recipe

1. Export constants as a frozen object.
2. Reuse ClickHouse column names (`ProjectId`, `PulseType`, etc.) conceptually — don't invent new ones.
3. Anything sent as a resource attr must map to a materialized ClickHouse column (see [../../pulse-db/clickhouse/materialized-columns.md](../../pulse-db/clickhouse/materialized-columns.md)).
