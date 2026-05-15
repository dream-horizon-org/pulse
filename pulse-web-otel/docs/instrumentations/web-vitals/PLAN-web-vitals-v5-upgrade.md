# Plan: `web-vitals` v5+ upgrade

**Status:** Implemented in this branch (dependency `^5.2.0`, `onFID` removed, docs + gates green).

This plan supersedes ad-hoc notes and incorporates internal reviews: navigationType semantics, stale comments, PLAN-phase2, SPEC §5.7 vs duplicate ADR, data-contract narrowing, lockfile scope, Next.js E2E scope, **PR2 vs `e2e:web-sdk-gates` sequencing**, and **SPEC §5.1 / §6.1 / §6.2 FID string sweep**.

## Preconditions

- Read upstream [Upgrading to v5](https://github.com/GoogleChrome/web-vitals/blob/main/docs/upgrading-to-v5.md) and the v5.0+ changelog.
- After bumping, verify **`Metric.navigationType`** in installed TypeScript types (review assumed it is always present in v5; confirm before simplifying guards).

## 1. Product / contract: FID

- **v5 removes `onFID()`.** Default approach: **drop FID** from instrumentation; **INP** remains the interaction latency signal (aligned with Google).
- Alternative (only with explicit product sign-off): reimplement FID outside the library — high cost; not the default path.
- **[SPEC.md](./SPEC.md):** R2, §1, **§5.1** `web_vital.name` Notes column (drop `FID` from the LCP, INP, CLS, … list), §5.2 FID row, §5.6 FID row, §9 Q1 — update so FID is no longer wired; resolve deferred FID wording. **§6.1** row **W-E5**: “Playwright INP / FID paths” → INP-only paths. **§6.2** bullets: remove “FID (Chromium)” from the metrics list; “INP / FID tests skip non-Chromium” → INP-only wording.
- **[docs/sdk-core/data-contract/SPEC.md](../../sdk-core/data-contract/SPEC.md)** (~L77): Treat as **contract narrowing**, not a typo: after upgrade, `web_vital.name` will **not** be `FID` in production — adjust enumerated names and any Notes that still imply FID.

## 2. Dependency and lockfile

- **[package.json](../../../package.json):** bump `"web-vitals"` to **^5.x** (pin to latest stable 5.x at upgrade time, e.g. `^5.2.0`).
- Run **`yarn install`** from **`pulse-web-otel/`** (workspace root for this package). Commit **`pulse-web-otel/yarn.lock`** only (demos are workspaces; there is no separate demo lockfile under this tree).

## 3. Implementation — [`src/instrumentations/web-vitals.ts`](../../../src/instrumentations/web-vitals.ts)

- Remove **`onFID`** import and registration.
- **JSDoc (~L31–34):** Replace “v4 API” wording with **version-agnostic** text (e.g. `web-vitals` does not expose unsubscribe handles) so it stays accurate after the bump.
- **`navigationType` / context attrs:** If typings and runtime show **`navigationType` is always defined** in v5:
  - Document in a short comment that **`web_vital.navigation_type`** and **`web_vital.context`** are **always emitted** (semantics differ from v4 where the field was optional on `Metric`).
  - Either keep `if (metric.navigationType !== undefined)` with an explanatory comment, or assign unconditionally if the type is non-optional and the runtime contract is guaranteed.

## 4. Tests

| Area | Action |
|------|--------|
| [`src/__tests__/web-vitals-instrumentation.test.ts`](../../../src/__tests__/web-vitals-instrumentation.test.ts) | Remove `onFID` from mocks; expect **five** metric registrations. **Update or remove** the test titled like “omits `navigation_type` when callback has none” — if v5 always supplies `navigationType`, retitle and assert **presence** of `navigation_type` + `context`, or delete the dead branch. |
| [`examples/ecommerce-demo/e2e/web-vitals.spec.ts`](../../../examples/ecommerce-demo/e2e/web-vitals.spec.ts) | **Must ship in the same PR as the dependency + instrumentation change** (see §8): `yarn e2e:web-sdk-gates` includes this spec; the Chromium FID test (~L175) **fails** if `onFID` is removed but the test still expects a FID log. Remove or rewrite FID expectations in that PR. Re-validate **LCP** under v5 trusted-input finalization (`page.click("body")` is likely still trusted in Chromium — confirm in CI). |
| [`examples/nextjs-demo/e2e/web-vitals.spec.ts`](../../../examples/nextjs-demo/e2e/web-vitals.spec.ts) | **No FID coverage today** — only touch if shared helpers or types force it. |
| E2E `assertExportedWebVitalAttrs` (ecommerce) | Optional hardening: assert concrete `navigation_type` → `context` pairs. **Clarification:** if v5 always sets `navigationType`, `if (ctx !== undefined)` **always runs** — that is **not dead code**; the branch label shifts from “sometimes” to “always, still valid.” No change required unless you tighten assertions. |

## 5. Documentation (single source of truth)

| File | Change |
|------|--------|
| [SPEC.md §5.1](./SPEC.md) | **`web_vital.navigation_type` / `web_vital.context`:** update Required/Notes if v5 always emits them. **`web_vital.name` Notes:** remove `FID` from the enumerated name list (aligns with §1 contract narrowing). |
| [SPEC.md §5.7](./SPEC.md) | Replace pre-upgrade “Why not v5+ casually” with a **post-upgrade** block: dependency on `web-vitals` ^5.x, PR link, FID removed, pointer to upstream v5 guide and LCP/INP behaviour notes. **Do not** add a separate ADR that duplicates §5.7 unless you **move** the rationale out of SPEC in one edit. |
| [SPEC.md §6.1](./SPEC.md) | Row **W-E5**: text that mentions Playwright **INP / FID** paths → **INP** paths only (FID removed). |
| [SPEC.md §6.2](./SPEC.md) | Bullet listing **FID (Chromium)** in metrics → drop FID. Bullet “INP / FID tests skip non-Chromium” → **INP** tests skip non-Chromium. |
| [SPEC.md §7](./SPEC.md) | Align “Other gaps” bullets if `navigation_type` / `context` are no longer “only when defined”. |
| [PLAN-phase2-per-route-vitals.md](./PLAN-phase2-per-route-vitals.md) | **~L45:** “No `web-vitals` version upgrade required” → **superseded** (phase 2 deltas + `navigation_id` still valid on v5). **~L20:** Clarify “v5 gives nothing **for soft-nav**” so it is not read as “never upgrade”. **~L114, ~L179:** Update or footnote “no version bump” as historical. |
| [CLAUDE.md](../../../CLAUDE.md) | Optional one-line sharp edge for integrators (missing FID, baseline). |

## 6. Ship gates

Per [web-sdk-ship](../../../../.claude/skills/web-sdk-ship/SKILL.md) / package scripts:

- `yarn lint`
- `yarn test:run`
- `yarn e2e:web-sdk-gates` (includes ecommerce **web vitals** Playwright; must stay green on the PR that removes `onFID` — see §8)
- `yarn size-limit` ([`.size-limit.json`](../../../.size-limit.json))

FID removal can shrink library code, but **this branch raised** `.size-limit.json`
because **traced** `dist/index.js` / `dist/next.js` bundles include a larger OTel
graph than the old budget assumed — do not lower limits without re-running
`yarn size-limit` on a clean build.

## 7. Out of scope

- **`web-vitals/soft-navs`** / `reportSoftNavs` — separate initiative; not part of a normal `latest` v5 bump.

## 8. Suggested PR order

**Sequencing constraint:** Any PR that bumps `web-vitals` and removes `onFID` **must** include **ecommerce** [`e2e/web-vitals.spec.ts`](../../../examples/ecommerce-demo/e2e/web-vitals.spec.ts) FID test removal/rewrite in the **same** PR. Otherwise `yarn e2e:web-sdk-gates` on that PR fails (FID test runs on Chromium).

1. **Optional first slice:** Docs + data-contract + PLAN-phase2 + SPEC mechanical FID sweep (§5 table, §6.1 W-E5, §6.2) — can land early or be folded into PR 2.
2. **Code + gates PR (required bundle):** `package.json`, `yarn.lock`, `web-vitals.ts`, Vitest, **ecommerce E2E FID removal**, then run **`yarn lint`**, **`yarn test:run`**, **`yarn e2e:web-sdk-gates`**, **`yarn size-limit`**.
3. **Follow-up (no gate risk):** Remaining SPEC §5.7 narrative / §7 polish, optional `assertExportedWebVitalAttrs` hardening, CLAUDE nits — only after PR 2 is green, or fold into PR 2 if you prefer a single merge.

---

## Review checklist (folded in)

- [x] Confirm `Metric.navigationType` optionality in installed v5 types.
- [x] SPEC §5.7 closed in place; no conflicting standalone ADR.
- [x] data-contract row reflects **no FID** in emitted names.
- [x] `yarn install` only at `pulse-web-otel/`; one lockfile commit.
- [x] Next.js demo: no FID test — scope acknowledged.
- [x] PR that removes `onFID` includes ecommerce FID E2E update **before** declaring PR green (`e2e:web-sdk-gates`).
- [x] SPEC §5.1 name Notes + §6.1 W-E5 + §6.2 bullets updated (no stray “FID” in coverage copy).
