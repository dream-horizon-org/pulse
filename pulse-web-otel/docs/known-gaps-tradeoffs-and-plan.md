# Known gaps, tradeoffs, and plan

Package: `@dreamhorizonorg/pulse-web`  
Path: `pulse-web-otel/docs/known-gaps-tradeoffs-and-plan.md`

This file is the **product / API gap register** for the Web SDK (not a formal SPEC). Normative behaviour stays in `docs/sdk-core/**/SPEC.md` and `docs/instrumentations/**/SPEC.md`.

**Executable queue** (code + tests only): [`review-fix.md`](./review-fix.md) **§3**. **Implementation sketches:** **§7** (P0:1), **§8** (P2:13 feasibility), **§9** (P2:12). **Batch + changelog:** `review-fix` **§4–§5**.

**Stable IDs** (`P0:n`, `P1:n`, `P2:n`) are append-only. When an id **closes** or is **waived**, add a line under **§4.1 — Retired stable IDs**; do not reuse the id for a different topic without ADR.

---

## 1. Gaps, blocked, and deferred

This section lists topics that are **not** duplicated as full write-ups here when they already have a home in [`review-fix.md`](./review-fix.md):

- **§3** — Code + test backlog (**P2:12**, **RF-DC2–DC4**).
- **§7** — **P0:1** — singleton entry point vs free `init()`.
- **§8** — **P2:13** — `PulseAttributes` vs OTel types (feasibility).
- **§9** — **P2:12** — consent enum vs string union (implementation sketch).

### 1.1 Blocked

- **P1:7** — `globalAttributes` vs `resourceAttributes` tenant confusion vs Android’s different **surface** (`globalAttributes` lambda + `resource { }`). **Doc-first** diagram and optional `SignalAttributes` alias are **blocked** until **§3 Q2** below defines merge / overwrite order for any future `PulseGlobalAttributesProcessor` change. Until then, do not imply auto-merge in SPECs.

### 1.2 Deferred / not ready for §3

- **P2:11** — One drop-in `<PulseRouter />` for React Router + Next: needs **design spike / ADR** (bundle size, detection rules, breaking subpath imports); default **post-GA** unless PM pulls in.

---

## 2. Tradeoffs

Intentional product choices (not a single-repo “fix” without cross-SDK ADR or major version).

- **O1 — Identity API split** (`setUserId`, `setUserProperty`, `setUserProperties`, `clearUserIdentity`). Mirrors Android `PulseSDK`; a single-object API would need **iOS / RN + web ADR**. **Formerly P0:3.**

- **O2 — Non-Next bundlers + source maps** (formerly **P1:9**). `withPulseConfig` is Next-only; Vite / CRA / Webpack / Rspack need a **manual** symbolication / upload story; optional `vite-plugin-pulse` is backlog. When implementation or new automated tests exist, track them in [`review-fix.md`](./review-fix.md) **§3**; this file keeps the tradeoff blurb only.

---

## 3. Open questions

Unresolved decisions (may later unblock §1.1 or become `review-fix` work).

1. **`dataCollectionState` deprecation timeline.** Should we warn when the enum form is detected and steer hosts to a string-union shape before 1.0? (Touches **§9** / P2:12.)
2. **`globalAttributes` vs `resourceAttributes` merge strategy.** If we auto-merge later, define overwrite order before changing `PulseGlobalAttributesProcessor`. **Blocks P1:7** doc depth — see **§1.1** above.
3. **IndexedDB drain on slow networks.** Drain at init competes with first-batch / `session.start` — delay drain until after first flush, or lower-priority scheduling?
4. **`Pulse.whenReady()` semantics.** It always resolves today (including consent-blocked init). Should it reject, or resolve with a boolean, when init does not complete?
5. **React 19 / Strict Mode.** Double effect run: `_initializing` guards double init; double `shutdown` + re-init cycle in dev is not explicitly covered by tests.

---

## 4. Planned for fix

**Where to work:** [`review-fix.md`](./review-fix.md) **§3** (code + tests), **§4** (batch), **§5** (changelog), **§7–§9** (appendices). Data-contract matrices: [`sdk-core/data-contract/SPEC.md`](./sdk-core/data-contract/SPEC.md) §6. RF test index: [`sdk-core/test-coverage/SPEC.md`](./sdk-core/test-coverage/SPEC.md) §6.7.

When work completes: trim **§1.1 / §1.2** if an id is no longer blocked/deferred; add **§4.1** retired lines; update `review-fix` **§5**.

### 4.1 Retired stable IDs (archive — do not reuse without new ADR)

- **P0:2** — Capture API naming (`track*` / `report*` vs `capture*`). **Waived** (Android + RN parity). 2026-05-14.
- **P0:3** — Identity API split. **→ O1.** 2026-05-14.
- **P0:4** — `beforeSendData` naming vs market “`beforeSend`”. **Done** (docs + JSDoc: integration SPEC §5.9, `src/types/config.ts`, README, exporters cross-link). 2026-05-15.
- **P0:5** — `<PulseRouterEvents />` export path clarity. **Done** (README import path fix, `docs/publishing/INTEGRATION.md` notes, integration SPEC §5.4 / router narrative). 2026-05-15.
- **P0:6** — `shutdownOnUnmount` default/docs. **Done** (`src/types/react.ts`, `PulseProvider.tsx` JSDoc; integration SPEC §5.4; `INTEGRATION.md`). 2026-05-15.
- **P1:7** — `globalAttributes` vs `resourceAttributes`. **Blocked** on **§3 Q2** — see **§1.1** (not in `review-fix` §3 until unblocked). 2026-05-14.
- **P1:8** — Next ESM / `create-next-app` consumer path. **Process QA:** [`sdk-core/test-coverage/SPEC.md`](./sdk-core/test-coverage/SPEC.md) §6.6 (record result there or in `CHANGELOG.md`).
- **P1:9** — Vite / non-Next source maps. **→ O2.** 2026-05-14.
- **P1:10** — Web vs Android / RN manual error API naming. **Done** (integration SPEC §5.10 + config SPEC cross-link). 2026-05-15.
- **P2:11** — Unified `<PulseRouter />`. **Deferred** — see **§1.2** (not in `review-fix` §3). 2026-05-14.
- **P2:13** — `PulseAttributes` vs OTel type alignment. **Feasibility** in [`review-fix.md`](./review-fix.md) §8.

### 4.2 Absorbed legacy documents (historical)

Planning text from these paths was merged into `docs/sdk-core/` topic SPECs and this file’s register:

| Deleted path | Absorbed into |
|--------------|---------------|
| `web-sdk-plan/v1/01-foundation/README.md` | `sdk-core/architecture-and-bootstrap`, `sdk-core/test-coverage`, §4.2 here |
| `web-sdk-plan/v1/01-foundation/sdk-lifecycle.md` | `sdk-core/architecture-and-bootstrap`, `instrumentations/session`, `sdk-core/config-and-public-api` |
| `web-sdk-plan/INTEGRATION.md` | `sdk-core/config-and-public-api`; router + shutdown notes in `docs/publishing/INTEGRATION.md` + integration SPEC §5.4 |
| `docs/API-CRITIQUE.md` | This register + retired ids |
