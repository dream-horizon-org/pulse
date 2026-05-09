# M1 — Foundation

Lifecycle, configuration, consent gating, and identity attributes — summarized exit criteria stay in [`../MILESTONES.md`](../MILESTONES.md) § M1.

## Docs in this folder

| Doc | Purpose |
|-----|---------|
| [sdk-lifecycle.md](sdk-lifecycle.md) | SDK start / shutdown and initialization order |

## Code entrypoints

- `pulse-web-otel/src/sdk.ts` — `Pulse.init`, shutdown, feature wiring
- Feature gates — `PulseFeature` and instrumentation registration under `src/instrumentations/`

## Related

- Errors program: [`../../v1-errors/`](../../v1-errors/)
