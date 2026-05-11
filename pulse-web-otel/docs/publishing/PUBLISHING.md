# Publishing `@dreamhorizonorg/pulse-web` to npm

> Audience: maintainers shipping `@dreamhorizonorg/pulse-web` to npm for the first time and on every subsequent release. **Scope index:** [`./SPEC.md`](./SPEC.md). This file is the **long-form runbook** for packaging, versioning, release flow, and the “subpath vs sibling package” decision.

Sibling docs:

- [`../../README.md`](../../README.md) — consumer install + quick wiring
- [`../instrumentations/integration/SPEC.md`](../instrumentations/integration/SPEC.md) — host-app integration (exports, `Pulse.init`, React / Next.js / vanilla)
- [`./SPEC.md`](./SPEC.md) — canonical publishing scope + pointers (operational index)
- [`../../CHANGELOG.md`](../../CHANGELOG.md) — release notes (currently `0.1.0-alpha.x`)
- [`../../../.github/workflows/publish-react-native.yml`](../../../.github/workflows/publish-react-native.yml) — sister-SDK publish workflow we should mirror for the web SDK

There is **no** `RELEASE.md` or existing `release-web-sdk.yml` in the repo today, so this document is creating the workflow from scratch and should be wired up before the first publish.

---

## 0. TL;DR

1. Fix the `package.json` gaps in [§1.3](#13-packagejson-audit) — without these, the first `npm publish` either fails or creates an unusable package.
2. First release stays on the `alpha` dist-tag (`npm publish --tag alpha`). `latest` is reserved for the first non-alpha cut.
3. Single package today: `@dreamhorizonorg/pulse-web` with subpath exports `./react`, `./next`, `./next-config`. Sibling packages (`@dreamhorizonorg/pulse-web-next`, `…-vue`) only when the trigger conditions in [§6](#6-subpath-exports-vs-sibling-packages-the-big-question) are met.
4. CI publishes via GitHub Actions with **npm trusted publishing (OIDC)** — no long-lived `NPM_TOKEN` after the first manual run.

---

## 1. Pre-publish checklist

### 1.1 SemVer and dist-tag policy

Current version: `0.1.0-alpha.1` (`pulse-web-otel/package.json:3`); `CHANGELOG.md` already announces `0.1.0-alpha.2` with breaking renames (`PulseWeb` → `Pulse`, `Pulse.start` → `Pulse.init`, `PulseNavigationEvents` → `PulseRouterEvents`).


| Phase                          | Version pattern | dist-tag                 |
| ------------------------------ | --------------- | ------------------------ |
| Internal monorepo only (today) | `0.1.0-alpha.x` | `alpha`                  |
| External-friendly preview      | `0.1.0-beta.x`  | `beta`                   |
| Production-ready               | `1.0.0`         | `latest`                 |
| Pre-release of next minor      | `1.1.0-next.x`  | `next`                   |
| Patch on previous major        | `1.x.y`         | `legacy` (or `1-latest`) |


Rules during alpha (`0.x`):

- Breaking signal-contract or public-API changes are allowed but **must** appear in `CHANGELOG.md` under "Breaking changes".
- Each alpha bumps the third segment: `0.1.0-alpha.1` → `0.1.0-alpha.2` → … No backports; consumers always upgrade to the latest alpha.

After `1.0.0`:

- **Major:** breaking signal contract, removed `pulse.type` values, removed public API symbols, changed exports map shape.
- **Minor:** new instrumentation, new public API symbol, new optional config, new subpath export.
- **Patch:** bug fix, perf, internal refactor with no consumer-visible change.

Reference: [npm dist-tag docs](https://docs.npmjs.com/cli/v10/commands/npm-dist-tag), [semver.org](https://semver.org).

### 1.2 Compare to existing alpha tag policy in the wild

- **PostHog** publishes `posthog-js` to `latest` from the moment the package is non-alpha and uses Changesets-driven release on push to `main` ([RELEASING.md](https://github.com/PostHog/posthog-js/blob/main/RELEASING.md)).
- **Sentry** uses pre-release branches like `9.7.0-alpha` and the `Prepare Release` workflow with explicit branch + version inputs for alpha/beta cuts ([publishing-a-release.md](https://github.com/getsentry/sentry-javascript/blob/develop/docs/publishing-a-release.md)).

We follow Sentry's branch-driven model for the first non-alpha cut and PostHog's Changesets-on-merge model for ongoing releases (see [§4](#4-automated-release-pipeline)).

### 1.3 `package.json` audit

Below is the **current** `pulse-web-otel/package.json` overlaid with required fixes. Quoted snippets are exactly what is in the file today.

#### 1.3.1 Identity / discovery — **gaps**

The package today is missing every metadata field that npm shows on the package page:

```json
// MISSING from pulse-web-otel/package.json
"author": "DreamHorizon <https://dreamhorizon.org>",
"license": "Apache-2.0",
"keywords": [
  "opentelemetry", "otel", "rum", "observability", "monitoring",
  "web-vitals", "error-tracking", "tracing", "react", "nextjs",
  "browser", "performance"
],
"homepage": "https://github.com/dream-horizon-org/pulse/tree/main/pulse-web-otel#readme",
"repository": {
  "type": "git",
  "url": "git+https://github.com/dream-horizon-org/pulse.git",
  "directory": "pulse-web-otel"
},
"bugs": {
  "url": "https://github.com/dream-horizon-org/pulse/issues"
},
"publishConfig": {
  "access": "public",
  "registry": "https://registry.npmjs.org/",
  "provenance": true
}
```

`publishConfig.access: "public"` is required for any scoped package — without it the first `npm publish` fails with `E402 You must sign up for private packages` (or charges if you do have private access).

The repo root has `LICENSE` (Apache-2.0) but `pulse-web-otel/` does not. Copy the root `LICENSE` to `pulse-web-otel/LICENSE` so npm's tarball includes it; otherwise the npm page shows "no license" even with the `license` field set. The sister RN SDK declares `"license": "MIT"` (`pulse-react-native-otel/package.json:70`), which conflicts with the repo-root Apache-2.0 — the web SDK should follow the **repo root** until the org formally relicenses anything.

#### 1.3.2 Entry points and exports — **mostly correct, one types-map bug**

Today (`pulse-web-otel/package.json:5-30`):

```json
"type": "module",
"main": "dist/index.cjs",
"module": "dist/index.js",
"types": "dist/index.d.ts",
"exports": {
  ".":            { "types": "./dist/index.d.ts",       "import": "./dist/index.js",       "require": "./dist/index.cjs" },
  "./react":      { "types": "./dist/react.d.ts",       "import": "./dist/react.js",       "require": "./dist/react.cjs" },
  "./next":       { "types": "./dist/next.d.ts",        "import": "./dist/next.js",        "require": "./dist/next.cjs" },
  "./next-config":{ "types": "./dist/next-config.d.ts", "import": "./dist/next-config.js", "require": "./dist/next-config.cjs" }
}
```

`tsup` already emits `.d.cts` alongside `.d.ts` (verified in `pulse-web-otel/dist/`: `index.d.cts`, `index.d.ts`, `react.d.cts`, `react.d.ts`, `next.d.cts`, `next.d.ts`). The exports map points the `require` condition's **types** at the `.d.ts` file — that is the classic "dual-package types hazard" `@arethetypeswrong/cli` flags. Required fix:

```json
"exports": {
  "./package.json": "./package.json",
  ".": {
    "import": { "types": "./dist/index.d.ts",  "default": "./dist/index.js" },
    "require":{ "types": "./dist/index.d.cts", "default": "./dist/index.cjs" }
  },
  "./react": {
    "import": { "types": "./dist/react.d.ts",  "default": "./dist/react.js" },
    "require":{ "types": "./dist/react.d.cts", "default": "./dist/react.cjs" }
  },
  "./next": {
    "import": { "types": "./dist/next.d.ts",   "default": "./dist/next.js" },
    "require":{ "types": "./dist/next.d.cts",  "default": "./dist/next.cjs" }
  },
  "./next-config": {
    "import": { "types": "./dist/next-config.d.ts",  "default": "./dist/next-config.js" },
    "require":{ "types": "./dist/next-config.d.cts", "default": "./dist/next-config.cjs" }
  }
}
```

Why each piece matters:

- `"./package.json": "./package.json"` lets bundlers and tools (`vite`, `next`, `webpack-bundle-analyzer`, our own `size-limit`) resolve the manifest. Sentry sets this on every package (`@sentry/react`, `@sentry/browser`).
- `import.types` and `require.types` distinguished — fixes the dual-package types hazard `attw` will report. Sentry uses the same shape for `@sentry/react`.
- Per-condition `default` (instead of a top-level `import`/`require` shorthand) prevents Node from skipping the conditions in subpath subkeys.

Side-effects:

```json
"sideEffects": false
```

The library has no top-level side effects — all subscriptions happen inside `Pulse.init()`. Marking it `false` is what unlocks tree-shaking when consumers `import { trackEvent }` and never touch the React subpath. Both `@sentry/react` and `@sentry/browser` set `sideEffects: false`.

#### 1.3.3 `files` and tarball contents — **ok, but verify**

```json
"files": ["dist"]
```

This is correct in principle, but:

- `README.md`, `LICENSE`, `CHANGELOG.md`, `package.json` are **always** included by npm regardless of `files`.
- Authoring trees such as `docs/` (except files explicitly listed in `files`), `examples/`, `node_modules/`, `src/`, `tsup.config.ts`, `vitest.config.ts`, `.size-limit.json` are **excluded** because they are not under `dist/` and not in `files`. Good.
- `LICENSE` does not yet exist in `pulse-web-otel/`. Add it before publishing (see [§1.3.1](#131-identity--discovery--gaps)).

Run `npm pack --dry-run` to confirm the file list before publishing — see [§3](#3-first-publish-manual--exact-commands).

#### 1.3.4 Peer dependencies — **mostly correct, missing `@types/react`**

Today (`pulse-web-otel/package.json:92-107`):

```json
"peerDependencies": {
  "next": ">=14.0.0",
  "react": ">=18.0.0",
  "react-router-dom": ">=6.0.0"
},
"peerDependenciesMeta": {
  "next": { "optional": true },
  "react": { "optional": true },
  "react-router-dom": { "optional": true }
}
```

Verified consumer matrix from `pulse-web-otel/examples/`:


| Example          | React     | Next      | react-router-dom |
| ---------------- | --------- | --------- | ---------------- |
| `web-sdk-docs`   | —         | —         | —                |
| `ecommerce-demo` | `^18.3.0` | —         | `^6.26.0`        |
| `nextjs-demo`    | `^18.3.0` | `^15.3.1` | —                |
| `lottery-demo`   | `19.2.3`  | `^15.3.1` | —                |


`react: >=18` matches all React app usages but **excludes React 19** which the `lottery-demo` already uses. Required fix:

```json
"peerDependencies": {
  "next": ">=14.0.0",
  "react": ">=18.0.0",
  "react-router-dom": ">=6.0.0",
  "@types/react": ">=18.0.0"
},
"peerDependenciesMeta": {
  "next":              { "optional": true },
  "react":             { "optional": true },
  "react-router-dom":  { "optional": true },
  "@types/react":      { "optional": true }
}
```

The SDK code itself does not import `react-dom`, so there is no `react-dom` peerDep — match Sentry's `@sentry/react` which only declares `react`.

The `react: >=18.0.0` range already accepts React 19 by SemVer rules — no change needed for `lottery-demo` compatibility.

#### 1.3.5 Runtime dependencies — **clean**

Today's dependencies (`pulse-web-otel/package.json:53-69`) are entirely OTel + `web-vitals`. Nothing to remove. Pin the major (`^1`, `^0.53`) and let users dedupe via their own resolutions.

#### 1.3.6 `engines`

Today: `"engines": { "node": ">=18.13.0" }` — keep. CI Node version (`actions/setup-node` step in [§4](#4-automated-release-pipeline)) must satisfy this.

For trusted publishing (OIDC), npm requires CLI ≥ 11.5.1 and Node ≥ 22.14.0 ([npm trusted publishing GA](https://github.blog/changelog/2025-07-31-npm-trusted-publishing-with-oidc-is-generally-available/)). The `engines` field constrains *consumers*, not CI; CI will run on Node 22.

#### 1.3.7 Build outputs — **already correct**

`tsup.config.ts` produces ESM + CJS + `.d.ts` + `.d.cts` with sourcemaps for all four entry points (verified by reading `dist/` listing). No change required, except [§1.3.2](#132-entry-points-and-exports--mostly-correct-one-types-map-bug) above.

Verify after every build:

```bash
cd pulse-web-otel && yarn build
ls -1 dist | sort
# expected (subset):
#   index.cjs, index.cjs.map, index.d.cts, index.d.ts, index.js, index.js.map
#   react.cjs, react.cjs.map, react.d.cts, react.d.ts, react.js, react.js.map
#   next.cjs,  next.cjs.map,  next.d.cts,  next.d.ts,  next.js,  next.js.map
#   next-config.cjs, next-config.js, next-config.d.cts, next-config.d.ts
```

#### 1.3.8 README badges, CHANGELOG

`pulse-web-otel/README.md` already exists. Add (in this order, near the top):

```markdown
[![npm version](https://img.shields.io/npm/v/@dreamhorizonorg/pulse-web?logo=npm)](https://www.npmjs.com/package/@dreamhorizonorg/pulse-web)
[![bundle size](https://img.shields.io/bundlephobia/minzip/@dreamhorizonorg/pulse-web)](https://bundlephobia.com/package/@dreamhorizonorg/pulse-web)
[![license](https://img.shields.io/npm/l/@dreamhorizonorg/pulse-web)](../../LICENSE)
```

`CHANGELOG.md` already follows a "version, breaking changes, other changes" structure — keep it. Adopt **Changesets** for the first non-alpha release (see [§4](#4-automated-release-pipeline)). Do **not** retrofit Changesets into the alpha history; start fresh at `0.1.0-beta.0` or `1.0.0-rc.0`.

---

## 2. One-time setup

### 2.1 npm org and access

1. Create or claim the npm org: [https://www.npmjs.com/org/create](https://www.npmjs.com/org/create). Confirm the org name matches the package scope (`dreamhorizon`).
2. Add maintainers as **Owners** of the package (after the first publish). Limit publish rights to a small group; readers can be the whole team.
3. Enable **2FA for the whole org**: [https://docs.npmjs.com/configuring-two-factor-authentication](https://docs.npmjs.com/configuring-two-factor-authentication). Use "Authorization and writes" mode so even a stolen session cannot publish.
4. For each maintainer, generate an **automation token** *only* if you cannot use OIDC trusted publishing. Trusted publishing is now the default recommendation; see [§2.3](#23-npm-trusted-publishing-oidc).

### 2.2 Local credentials

```bash
npm login
# uses browser-based login; no plaintext token in ~/.npmrc
npm whoami
# expected: your username, e.g. dh-bot
```

This is only needed for the very first manual publish ([§3](#3-first-publish-manual--exact-commands)) and for emergency manual republishes; CI uses OIDC.

### 2.3 npm trusted publishing (OIDC)

Per the [July 2025 GA announcement](https://github.blog/changelog/2025-07-31-npm-trusted-publishing-with-oidc-is-generally-available/), GitHub Actions can publish to npm without any long-lived token.

One-time configuration on npmjs.com:

1. Publish the package once manually with `--access public --tag alpha` (see [§3](#3-first-publish-manual--exact-commands)).
2. Visit [https://www.npmjs.com/package/@dreamhorizonorg/pulse-web/access](https://www.npmjs.com/package/@dreamhorizonorg/pulse-web/access).
3. Under **Trusted Publishing**, "Add a trusted publisher" → **GitHub Actions**.
4. Fill in:
  - Organization: `dream-horizon-org`
  - Repository: `pulse`
  - Workflow filename: `release-web-sdk.yml`
  - Environment (optional but recommended): `npm-publish`
5. Save.

After this, the workflow in [§4](#4-automated-release-pipeline) publishes via OIDC. No `NPM_TOKEN` GitHub secret is needed for the web SDK once trusted publishing is set up.

If you must fall back to a token (e.g. trusted publishing is blocked on your npm plan):

- Create an **automation** token (not "publish") on [https://www.npmjs.com/settings/dh-bot/tokens](https://www.npmjs.com/settings/dh-bot/tokens).
- Add it as a repository secret `NPM_TOKEN` under [https://github.com/dream-horizon-org/pulse/settings/secrets/actions](https://github.com/dream-horizon-org/pulse/settings/secrets/actions).

The sister RN SDK uses the `NPM_TOKEN` fallback (`.github/workflows/publish-react-native.yml:67`); migrate both to OIDC when convenient.

---

## 3. First publish (manual) — exact commands

Run from the repo root. Yarn workspaces in this repo are scoped to `pulse-web-otel/examples/`* (`pulse-web-otel/package.json:34-36`); the SDK package itself is **not** part of a higher-level workspace, so no `--workspaces` flag is needed.

```bash
cd pulse-web-otel
corepack enable                                  # ensures yarn 4.3.1 from packageManager field
yarn install --immutable                          # equivalent of --frozen-lockfile in yarn 4
yarn lint                                         # tsc --noEmit
yarn test:run                                     # vitest run
yarn build                                        # tsup → dist/
yarn size-limit                                   # bundle budget gate
```

Inspect what will go to npm:

```bash
npm pack --dry-run
# verify: only dist/, README.md, LICENSE, CHANGELOG.md, package.json
# bytes printed should be roughly the gzipped size of dist/ + metadata
```

Validate the package layout against published-package linters:

```bash
npx --yes publint
npx --yes @arethetypeswrong/cli --pack .
```

`publint` flags missing exports conditions, wrong `main`/`module` shape, missing `types`. `attw` simulates resolution under `node10`, `node16`, and `bundler` and reports the dual-package types hazard explicitly. Both should print zero errors after the [§1.3.2](#132-entry-points-and-exports--mostly-correct-one-types-map-bug) fix.

Publish the alpha:

```bash
npm publish --access public --tag alpha
# expected: + @dreamhorizonorg/pulse-web@0.1.0-alpha.2
```

Verify on npm:

```bash
npm view @dreamhorizonorg/pulse-web versions --json
npm view @dreamhorizonorg/pulse-web dist-tags
# expected dist-tags: { alpha: '0.1.0-alpha.2' }
# 'latest' is intentionally absent until the first non-alpha
```

Promote a later non-alpha cut to `latest`:

```bash
# after publishing 1.0.0:
npm publish --access public            # implicitly tags as 'latest'
# or, explicit promotion of an existing version:
npm dist-tag add @dreamhorizonorg/pulse-web@1.0.0 latest
```

Reference: [npm dist-tag docs](https://docs.npmjs.com/cli/v10/commands/npm-dist-tag).

---

## 4. Automated release pipeline

### 4.1 Workflow file

Create `.github/workflows/release-web-sdk.yml`. This is a Changesets-driven workflow modelled on PostHog's release flow ([RELEASING.md](https://github.com/PostHog/posthog-js/blob/main/RELEASING.md)) with Sentry-style path filtering ([Sentry JavaScript monorepo](https://github.com/getsentry/sentry-javascript)) and npm trusted publishing for provenance.

```yaml
name: Web SDK — Release

on:
  push:
    branches: [main]
    paths:
      - 'pulse-web-otel/**'
      - '.github/workflows/release-web-sdk.yml'
  workflow_dispatch:

permissions:
  contents: read

concurrency:
  group: release-web-sdk
  cancel-in-progress: false

jobs:
  release:
    name: Publish @dreamhorizonorg/pulse-web
    runs-on: ubuntu-latest
    environment: npm-publish
    permissions:
      contents: write       # changesets opens a PR / pushes version bump
      pull-requests: write  # changesets opens the version PR
      id-token: write       # npm trusted publishing (OIDC)

    steps:
      - name: Checkout
        uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: Setup Node
        uses: actions/setup-node@v4
        with:
          node-version: '22.14.0'
          registry-url: 'https://registry.npmjs.org'

      - name: Enable Corepack (yarn 4.3.1)
        run: corepack enable

      - name: Install
        working-directory: pulse-web-otel
        run: yarn install --immutable

      - name: Lint + test
        working-directory: pulse-web-otel
        run: |
          yarn lint
          yarn test:run

      - name: Build
        working-directory: pulse-web-otel
        run: yarn build

      - name: Size limit
        working-directory: pulse-web-otel
        run: yarn size-limit

      - name: Validate package layout
        working-directory: pulse-web-otel
        run: |
          npx --yes publint
          npx --yes @arethetypeswrong/cli --pack .

      - name: Create release PR or publish
        id: changesets
        uses: changesets/action@v1
        with:
          cwd: pulse-web-otel
          publish: yarn changeset publish
          version: yarn changeset version
          createGithubReleases: true
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          NPM_CONFIG_PROVENANCE: 'true'
```

How it behaves:

- Push to `main` with no Changeset entries → no-op.
- Push to `main` with one or more `pulse-web-otel/.changeset/*.md` files → opens a "Version Packages" PR that bumps `pulse-web-otel/package.json` and writes the changelog.
- Merge that PR → workflow runs `yarn changeset publish` which calls `npm publish` with the dist-tag derived from the current version (`alpha`/`beta`/`next`/`latest`) and OIDC-signed provenance.

### 4.2 One-time Changesets bootstrap

```bash
cd pulse-web-otel
yarn add -D @changesets/cli
yarn changeset init
# edits .changeset/config.json
```

In `.changeset/config.json` set:

```json
{
  "$schema": "https://unpkg.com/@changesets/config@3/schema.json",
  "changelog": "@changesets/changelog-github",
  "commit": false,
  "fixed": [],
  "linked": [],
  "access": "public",
  "baseBranch": "main",
  "updateInternalDependencies": "patch",
  "ignore": ["ecommerce-demo", "nextjs-demo", "lottery-demo", "web-sdk-docs"]
}
```

`ignore` keeps the example workspaces out of the version bump (they are `private: true` so they cannot be published anyway, but Changesets warns).

For each non-trivial PR contributors run:

```bash
cd pulse-web-otel
yarn changeset
# pick: patch / minor / major; write a one-line changelog entry
git add .changeset && git commit -m "chore(web-sdk): add changeset"
```

### 4.3 Conditional publish

Changesets handles "publish only when version changes" automatically — `yarn changeset publish` no-ops on versions already on the registry. If you do not adopt Changesets, the simpler tag-triggered alternative is:

```yaml
on:
  push:
    tags: ['web-sdk-v*']
```

…and add a manual `package.json` version bump + `git tag web-sdk-v0.1.0-alpha.3 && git push --tags`. This mirrors the existing iOS / Android workflows but is more error-prone for a fast-moving SDK; Changesets is recommended.

### 4.4 Provenance verification

After a successful publish:

```bash
npm view @dreamhorizonorg/pulse-web --json | jq '.dist'
# expected: dist.attestations.provenance present
```

Or visit `https://www.npmjs.com/package/@dreamhorizonorg/pulse-web/v/1.0.0` — the page shows a green "Published with provenance" badge and links the workflow run.

---

## 5. Consumer install snippets

```bash
npm install @dreamhorizonorg/pulse-web
yarn add @dreamhorizonorg/pulse-web
pnpm add @dreamhorizonorg/pulse-web
bun add  @dreamhorizonorg/pulse-web
```

For a pre-release tag:

```bash
npm install @dreamhorizonorg/pulse-web@alpha
yarn add  @dreamhorizonorg/pulse-web@beta
pnpm add  @dreamhorizonorg/pulse-web@next
```

Imports (verified against `pulse-web-otel/src/integrations/react/index.ts:1-22` and `pulse-web-otel/src/integrations/next/index.ts:9-41`):

```ts
import { Pulse, PulseDataCollectionConsent } from '@dreamhorizonorg/pulse-web';

import {
  PulseProvider,
  usePulse,
  PulseRouterEvents,
  PulseErrorBoundary,
  useRouterTracking,
} from '@dreamhorizonorg/pulse-web/react';

import {
  PulseProvider,
  PulseRouterEvents,
  useNextAppRouterTracking,
  useNextPagesRouterTracking,
  createPulseInstrumentationHandler,
} from '@dreamhorizonorg/pulse-web/next';

// next.config.js (CommonJS)
const { withPulseConfig } = require('@dreamhorizonorg/pulse-web/next-config');
```

Full integration recipes live in [`../instrumentations/integration/SPEC.md`](../instrumentations/integration/SPEC.md); do not duplicate them here.

---

<a id="6-subpath-exports-vs-sibling-packages-the-big-question"></a>

## 6. Subpath exports vs sibling packages - the big question

### 6.1 Reference points


| SDK               | Package layout                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | Why                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| ----------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **PostHog** today | `posthog-js` (browser core) + sibling `@posthog/react` (separate package). They explicitly migrated **away** from `posthog-js/react` subpath to `@posthog/react`. ([PostHog/posthog#54643](https://github.com/PostHog/posthog/pull/54643))                                                                                                                                                                                                                                                         | Wanted independent versioning of the React layer (`@posthog/react@1.9.0` vs `posthog-js@1.372.x`), independent peerDeps (`@posthog/react` declares `react ^16.8.0` peer + `posthog-js >=1.257.2` peer), and a clean React-package bundle without dragging the browser core's transitive deps into React-only consumers.                                                                                                                                                                                                                             |
| **Sentry** today  | `@sentry/core` + `@sentry/browser` + `@sentry/react` + `@sentry/nextjs` + `@sentry/node` + 30+ more, all in a Lerna→Nx monorepo. ([Sentry JavaScript monorepo](https://github.com/getsentry/sentry-javascript/tree/develop/packages), [@sentry/react package.json](https://github.com/getsentry/sentry-javascript/blob/develop/packages/react/package.json), [@sentry/nextjs package.json](https://github.com/getsentry/sentry-javascript/blob/develop/packages/nextjs/package.json)) | Each framework SDK has its own peerDep range (`@sentry/react` peers `react: ^16.14.0 || 17.x || 18.x || 19.x`; `@sentry/nextjs` peers `next: ^13.2.0 || ^14.0 || ^15.0.0-rc.0 || ^16.0.0-0`), its own conditional exports (Next.js needs `edge`, `edge-light`, `worker`, `workerd`, `browser`, `node` conditions — see `@sentry/nextjs` exports map), and its own runtime deps (Next.js pulls in `@rollup/plugin-commonjs`, `@sentry/webpack-plugin`, `@sentry/bundler-plugin-core` — none of which a plain `@sentry/browser` user should pay for). |


### 6.2 Decision criteria


| Criterion                                                      | Subpath export wins                                                                                      | Sibling package wins                                                  |
| -------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------- |
| Tree-shaking                                                   | ✅ `sideEffects: false` + ESM = consumer never pays for `/react` if not imported                          | tie                                                                   |
| peerDeps                                                       | ❌ all peers must be declared on the single package; `optional: true` mitigates but adds install warnings | ✅ each sibling declares only its own peers                            |
| Framework-specific transitive runtime deps                     | ❌ all consumers download them (e.g. a Next.js webpack plugin)                                            | ✅ siloed                                                              |
| SSR / framework-specific build conditions (RSC, edge, workerd) | ❌ tsup single config can't easily express `"edge"` + `"node"` + `"browser"` per subpath                  | ✅ each sibling has its own bundle config and conditions               |
| Independent versioning                                         | ❌ React layer + core ship together                                                                       | ✅ React layer can hotfix without rebuilding core                      |
| Discovery / docs SEO                                           | tie (`@dreamhorizonorg/pulse-web/react` is searchable)                                                   | ✅ npm has a dedicated page per package                                |
| Bundle bloat from framework adapters                           | acceptable today (`react` chunk ≈ small)                                                                 | wins once `next` chunk pulls Next-specific runtime deps               |
| Maintenance overhead                                           | ✅ one CHANGELOG, one CI job, one publish                                                                 | ❌ N CHANGELOGs, N CI jobs, monorepo tooling (Changesets in mono mode) |


### 6.3 Recommendation for Pulse Web SDK

**Today: keep the single-package + subpath-exports layout (`@dreamhorizonorg/pulse-web` with `./react`, `./next`, `./next-config`).**

Reasons grounded in the current repo state:

1. **Zero framework-only transitive deps.** `tsup.config.ts` already lists `react`, `react-dom`, `react-router-dom`, `next`, and `webpack` as `external` — none ship in the bundle, only in peerDeps. There is no Next-only runtime dep like `@sentry/webpack-plugin` to silo off.
2. **All four entry points share `@opentelemetry/`* runtime.** Splitting would force `@dreamhorizonorg/pulse-web-react` to peerDep `@dreamhorizonorg/pulse-web` and dedupe issues become a real maintenance cost.
3. **Single SemVer pace.** The signal contract and the React/Next adapters move together right now (see `CHANGELOG.md` `0.1.0-alpha.2` — `Pulse.start` rename forced `PulseRouterEvents` rename in lockstep).
4. **Single integration SPEC** (`docs/instrumentations/integration/SPEC.md`). Splitting docs across packages is friction we don't need pre-1.0.
5. **Bundle size is healthy.** `.size-limit.json` budgets are: `dist/index.js` 65 kB, `dist/index.cjs` 115 kB, `dist/next.js` 10 kB. The `/next` and `/react` chunks are small because of the `external` list.

**Graduate to sibling packages when any of these become true:**


| Trigger                                                                                                                               | Likely package                        |
| ------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------- |
| Next.js needs RSC / edge / workerd `exports` conditions like `@sentry/nextjs`                                                         | `@dreamhorizonorg/pulse-web-next`     |
| A Vue / Solid / Svelte adapter ships with framework-specific peerDeps                                                                 | `@dreamhorizonorg/pulse-web-vue` etc. |
| A framework adapter pulls a non-trivial runtime dep (e.g. a webpack plugin) that we don't want forced on every consumer               | split that adapter                    |
| Consumers ask for independent versioning of the React/Next layer (e.g. a security fix in core that should not bump the React package) | split                                 |


**Migration path (no consumer break) when the time comes:**

1. Cut the new package, e.g. `@dreamhorizonorg/pulse-web-react@1.0.0`, with the same exports as today's `/react` subpath.
2. In the next minor of `@dreamhorizonorg/pulse-web` (say `1.4.0`), make `./react` a **re-export shim**:
  ```ts
   // pulse-web-otel/src/integrations/react/index.ts (becomes)
   export * from '@dreamhorizonorg/pulse-web-react';
  ```
3. Add `@dreamhorizonorg/pulse-web-react` as a `dependencies` entry of `@dreamhorizonorg/pulse-web` (so `import …/react` keeps working with no consumer change).
4. Add a deprecation notice in the `/react` subpath README pointing consumers at the new package over the next major.
5. In the **next major** (`2.0.0`), remove the `./react` subpath. Consumers who upgraded their import path see no break; the rest get a clear error from the exports map and a one-liner codemod.

PostHog used exactly this pattern when migrating from `posthog-js/react` to `@posthog/react` ([PR #54643](https://github.com/PostHog/posthog/pull/54643)).

---

## 7. Versioning + deprecation

### 7.1 SemVer rules (binding)

- **Major** — any of: removed/renamed exported symbol; removed `pulse.type` value or attribute; changed exports map to remove a subpath; changed `engines.node` minimum; changed peer ranges in a way that excludes a previously-supported framework version; changed default config behaviour in a way that emits or omits signals from before.
- **Minor** — new exported symbol, new optional config, new `pulse.type` value, new subpath export, new instrumentation, lifted peer range upper bound.
- **Patch** — bug fix, perf, internal refactor, doc update.

The signal contract is the **public API**. A change in `pulse.type`, a span attribute name, or a log body schema is a major. Reference: `pulse-web-otel/src/instrumentations/*` plus [`../instrumentations/sdk-core/SPEC.md`](../instrumentations/sdk-core/SPEC.md) (lifecycle + contract) and the per-instrumentation SPECs under [`../instrumentations/`](../instrumentations/).

### 7.2 Deprecating a bad release

If a published version is broken but installed by anyone:

```bash
npm deprecate @dreamhorizonorg/pulse-web@1.2.3 \
  "Critical bug in network instrumentation; upgrade to 1.2.4."
```

`npm deprecate` does **not** remove the version — it adds a warning when consumers install. Always combine with a patch release:

```bash
# fix on a branch, then:
npm version patch                       # 1.2.3 → 1.2.4
npm publish --access public
npm dist-tag add @dreamhorizonorg/pulse-web@1.2.4 latest
npm deprecate @dreamhorizonorg/pulse-web@1.2.3 "..."
```

### 7.3 Yanking vs patching (`npm unpublish`)

`npm unpublish` is allowed only within **72 hours** of publish and only when no other public package depends on the version. It is a last resort:

- Use case: you accidentally published a `dist/` that contains a credential or PII.
- Use:
  ```bash
  npm unpublish @dreamhorizonorg/pulse-web@1.2.3
  ```
- Then publish a clean replacement with a *new* version (`1.2.4`) — npm will not let you re-use `1.2.3`.

For anything outside the 72 h window or that has dependents, **patch + deprecate** is the only path. Reference: [npm unpublish policy](https://docs.npmjs.com/policies/unpublish).

---

## 8. Post-publish verification

### 8.1 Install in a fresh `examples/ecommerce-demo` against the published version

The in-repo demos use `"@dreamhorizonorg/pulse-web": "workspace:*"` (`pulse-web-otel/examples/ecommerce-demo/package.json:22`). To verify the published artifact end-to-end, do the install in a temp dir:

```bash
cd /tmp
mkdir pulse-web-smoke && cd pulse-web-smoke
yarn init -y
yarn add @dreamhorizonorg/pulse-web@<version> react@18 react-dom@18 react-router-dom@6
node -e "console.log(Object.keys(require('@dreamhorizonorg/pulse-web')))"
node -e "console.log(Object.keys(require('@dreamhorizonorg/pulse-web/react')))"
node -e "console.log(Object.keys(require('@dreamhorizonorg/pulse-web/next-config')))"
# expected: arrays containing 'Pulse', 'PulseProvider', 'withPulseConfig' respectively
```

For a real browser smoke test, create a one-off Vite app from the `examples/ecommerce-demo` template, but replace the workspace dep with the registry version:

```bash
cp -r pulse-web-otel/examples/ecommerce-demo /tmp/ecommerce-smoke
cd /tmp/ecommerce-smoke
sed -i '' 's/"workspace:\*"/"<version>"/' package.json
yarn install
yarn dev
# open http://localhost:3002, click around, watch DevTools Network for /v1/traces, /v1/logs, /v1/metrics
```

### 8.2 Vanilla docs smoke test

```bash
cp -r pulse-web-otel/examples/web-sdk-docs /tmp/web-sdk-docs-smoke
cd /tmp/web-sdk-docs-smoke
sed -i '' 's/"workspace:\*"/"<version>"/' package.json
yarn install && yarn dev
# verify /v1/traces beacon fires within ~30s of page load
```

### 8.3 npm page checks

Visit [https://www.npmjs.com/package/@dreamhorizonorg/pulse-web](https://www.npmjs.com/package/@dreamhorizonorg/pulse-web):

- **Code** tab — file tree should include `dist/`, `LICENSE`, `README.md`, `CHANGELOG.md`, `package.json` and **nothing else**.
- **Versions** tab — confirm dist-tag distribution (`latest`, `alpha`, `beta`, …).
- **Dependencies** tab — runtime deps only (no devDeps leaked).
- **Provenance** badge visible on the version page (after [§4](#4-automated-release-pipeline) is wired).

Verify exports map renders correctly under the version's "Repository" → "package.json" view; the `./react`, `./next`, `./next-config` keys must be present.

---

## 9. Troubleshooting

### 9.1 `npm publish` returns 403


| Cause                                                                                                   | Fix                                                                                                                                                                                  |
| ------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Scope mismatch (publishing `@dreamhorizonorg/pulse-web` but logged in as a user without org membership) | `npm whoami`, then `npm org ls dreamhorizon` to confirm membership. Add yourself or change scope.                                                                                    |
| Missing `publishConfig.access: "public"` on a scoped package                                            | Add it (see [§1.3.1](#131-identity--discovery--gaps)) and republish.                                                                                                                 |
| 2FA token missing (`--otp <code>` required)                                                             | Re-run with `npm publish --otp 123456 --access public --tag alpha`.                                                                                                                  |
| Trusted publishing not configured but workflow has no `NPM_TOKEN`                                       | Either configure trusted publisher on npmjs.com or add `NODE_AUTH_TOKEN: ${{ secrets.NPM_TOKEN }}` env to the publish step (mirror `.github/workflows/publish-react-native.yml:67`). |


### 9.2 `publint` errors


| Error                                               | Meaning                                                                           | Fix                                                                                                                                                |
| --------------------------------------------------- | --------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| `pkg.exports["."]` is missing the `types` condition | TS users get `Cannot find module` on the ESM path                                 | Add `"types"` inside the `import` and `require` condition objects (see [§1.3.2](#132-entry-points-and-exports--mostly-correct-one-types-map-bug)). |
| `pkg.main` does not match exports `.`               | Legacy resolvers and bundler resolvers see different files                        | Make `main` point at the same `.cjs` as `exports["."].require.default`.                                                                            |
| Dual-package hazard                                 | Two copies of the singleton (`Pulse`) loaded from `.cjs` and `.js` simultaneously | Mark `sideEffects: false`; ensure the singleton lives in a single shared chunk.                                                                    |


### 9.3 `attw` errors


| Error                                         | Fix                                                                                                               |
| --------------------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| `Masquerading as ESM` / `Masquerading as CJS` | Distinguish `.d.ts` and `.d.cts` (see [§1.3.2](#132-entry-points-and-exports--mostly-correct-one-types-map-bug)). |
| `Resolution Modes: node10` failing            | Acceptable to ignore; we don't support legacy Node resolution. Document in this file.                             |
| `False ESM`                                   | Your `.d.ts` re-exports from a `.js` file with no top-level `await`. Fixed by the `tsup` `dts: true` output.      |


### 9.4 React peer-dep warnings

If a consumer sees `npm WARN ERESOLVE` for `@types/react`, `react-router-dom`, or `next`:

- Confirm the relevant peer is declared **optional** in `peerDependenciesMeta` (see [§1.3.4](#134-peer-dependencies--mostly-correct-missing-typesreact)).
- The consumer's lockfile may be pinned to an out-of-range version; respond with the supported range and let them widen.

### 9.5 ESM/CJS interop pitfalls

- **Next.js `require('@dreamhorizonorg/pulse-web/next-config')`** in `next.config.js` (CJS) — supported via `exports["./next-config"].require`. If a consumer sees `Error [ERR_REQUIRE_ESM]`, their resolver is bypassing exports map (e.g. `webpack@4`); document that we require Next ≥ 14 in `engines` of consumer apps.
- **TypeScript `moduleResolution: node10`** consumers will not see the subpath exports at all. The fix is on their side: `moduleResolution: "bundler"` or `"node16"`.
- `**type: module` mismatch.** Our `package.json` has `"type": "module"`. Anyone consuming via plain Node (`node -e`) must use the `.cjs` build path or top-level `await import()`.

---

## 10. Sources

- npm dist-tag — [https://docs.npmjs.com/cli/v10/commands/npm-dist-tag](https://docs.npmjs.com/cli/v10/commands/npm-dist-tag)
- npm trusted publishing GA — [https://github.blog/changelog/2025-07-31-npm-trusted-publishing-with-oidc-is-generally-available/](https://github.blog/changelog/2025-07-31-npm-trusted-publishing-with-oidc-is-generally-available/)
- npm trusted publishing docs — [https://docs.npmjs.com/trusted-publishers](https://docs.npmjs.com/trusted-publishers)
- `publint` — [https://publint.dev/](https://publint.dev/)
- `@arethetypeswrong/cli` — [https://github.com/arethetypeswrong/arethetypeswrong.github.io/blob/main/packages/cli/README.md](https://github.com/arethetypeswrong/arethetypeswrong.github.io/blob/main/packages/cli/README.md)
- Changesets action — [https://github.com/changesets/action](https://github.com/changesets/action)
- PostHog `posthog-js/react` → `@posthog/react` migration — [https://github.com/PostHog/posthog/pull/54643](https://github.com/PostHog/posthog/pull/54643)
- PostHog releasing — [https://github.com/PostHog/posthog-js/blob/main/RELEASING.md](https://github.com/PostHog/posthog-js/blob/main/RELEASING.md)
- `posthog-js` package.json (browser core) — [https://github.com/PostHog/posthog-js/blob/main/packages/browser/package.json](https://github.com/PostHog/posthog-js/blob/main/packages/browser/package.json)
- `@posthog/react` package.json — [https://github.com/PostHog/posthog-js/blob/main/packages/react/package.json](https://github.com/PostHog/posthog-js/blob/main/packages/react/package.json)
- Sentry monorepo overview — [https://github.com/getsentry/sentry-javascript/tree/develop/packages](https://github.com/getsentry/sentry-javascript/tree/develop/packages)
- Sentry publishing a release — [https://github.com/getsentry/sentry-javascript/blob/develop/docs/publishing-a-release.md](https://github.com/getsentry/sentry-javascript/blob/develop/docs/publishing-a-release.md)
- `@sentry/react` package.json — [https://github.com/getsentry/sentry-javascript/blob/develop/packages/react/package.json](https://github.com/getsentry/sentry-javascript/blob/develop/packages/react/package.json)
- `@sentry/browser` package.json — [https://github.com/getsentry/sentry-javascript/blob/develop/packages/browser/package.json](https://github.com/getsentry/sentry-javascript/blob/develop/packages/browser/package.json)
- `@sentry/nextjs` package.json — [https://github.com/getsentry/sentry-javascript/blob/develop/packages/nextjs/package.json](https://github.com/getsentry/sentry-javascript/blob/develop/packages/nextjs/package.json)

