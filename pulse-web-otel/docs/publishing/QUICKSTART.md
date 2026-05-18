# Publishing Quickstart — `@dreamhorizonorg/pulse-web`

> One-page cheat sheet. Full reference: [`PUBLISHING.md`](./PUBLISHING.md).

## TL;DR

```bash
cd pulse-web-otel
yarn install --immutable
yarn build
yarn publint && yarn attw            # gates
npm publish --access public --tag alpha
```

That's it. Everything below is context, options, and troubleshooting.

---

## What gets published

The npm tarball ships exactly these (verified via `npm pack --dry-run`):

```text
dist/                  # ESM + CJS + .d.ts + .d.cts for index, react, next, next-config
README.md
LICENSE
CHANGELOG.md
docs/publishing/SPEC.md
docs/publishing/PUBLISHING.md
docs/publishing/QUICKSTART.md
package.json
```

Everything else (`src/`, `examples/`, authoring `docs/` outside the `files` allowlist, configs) is excluded (see `package.json` `files`).

## Versioning

| Phase | Version | Tag |
|---|---|---|
| Internal preview today | `0.1.0-alpha.x` | `alpha` |
| External preview | `0.1.0-beta.x` | `beta` |
| Production | `1.0.0` | `latest` |

`npm publish --tag alpha` keeps `latest` empty until the first non-alpha cut. Do not promote to `latest` accidentally.

## Sharing a pre-publish build with colleagues

Build a tarball locally and share the file — no npm publish needed:

```bash
cd pulse-web-otel
yarn pack:tarball
# → dreamhorizonorg-pulse-web-0.1.0-alpha.1.tgz in current dir
```

Colleague installs it from the file:

```bash
npm install /path/to/dreamhorizonorg-pulse-web-0.1.0-alpha.1.tgz
# or
yarn add /path/to/dreamhorizonorg-pulse-web-0.1.0-alpha.1.tgz
```

The `.tgz` is byte-identical to what `npm publish` would upload. Use this for internal QA before going to npm.

## Pre-publish gates (run on every release)

```bash
yarn lint              # tsc --noEmit
yarn test:run          # vitest run
yarn build             # tsup → dist/
yarn size-limit        # bundle budget
yarn publint           # npm package layout
yarn attw              # types resolution under node10/node16/bundler
npm pack --dry-run     # inspect tarball file list
```

All must pass before `npm publish`. Expected `attw` result: `node10` shows red for subpath imports — that's intentional and acceptable (we don't support pre-TS-4.7 module resolution).

## First-time npm setup

1. Create org `dreamhorizonorg` on npmjs.com (or confirm membership).
2. Enable 2FA on the org: <https://docs.npmjs.com/configuring-two-factor-authentication>.
3. `npm login` locally for the first manual publish.
4. After first publish, configure [trusted publishing (OIDC)](./PUBLISHING.md#23-npm-trusted-publishing-oidc) so CI never needs `NPM_TOKEN`.

## Consumer install

```bash
npm install @dreamhorizonorg/pulse-web
yarn add @dreamhorizonorg/pulse-web
pnpm add @dreamhorizonorg/pulse-web
```

Pre-release tag:

```bash
npm install @dreamhorizonorg/pulse-web@alpha
```

Imports:

```ts
import { Pulse } from "@dreamhorizonorg/pulse-web";
import { PulseProvider } from "@dreamhorizonorg/pulse-web/react";
import { PulseProvider, useNextAppRouterTracking } from "@dreamhorizonorg/pulse-web/next";
const { withPulseConfig } = require("@dreamhorizonorg/pulse-web/next-config");
```

## Subpath exports vs sibling packages

Today: **single package** `@dreamhorizonorg/pulse-web` with `./react`, `./next`, `./next-config` subpaths.

Reasons (from [PUBLISHING.md §6.3](./PUBLISHING.md#63-recommendation-for-pulse-web-sdk)):

1. Zero framework-only transitive deps — `react`, `next`, `webpack` are all `external` in `tsup.config.ts`, only ship as peerDeps.
2. All entries share the `@opentelemetry/*` runtime — splitting forces a peerDep dance.
3. Single SemVer pace — signal contract + adapters move in lockstep.
4. Single integration SPEC (`docs/instrumentations/integration/SPEC.md`).
5. Bundle size is healthy (`/next` chunk ≈ 10 kB).

Graduate to `@dreamhorizonorg/pulse-web-next`, `…-vue` only when a framework needs RSC/edge/workerd export conditions, or independent versioning, or framework-specific runtime deps. Migration path is in [PUBLISHING.md §6.3](./PUBLISHING.md#63-recommendation-for-pulse-web-sdk).

## When something goes wrong

| Symptom | Fix |
|---|---|
| `403 Forbidden` on publish | `npm whoami` + `npm org ls dreamhorizonorg`; confirm membership; check `publishConfig.access: public` is set |
| `publint` errors | See [PUBLISHING.md §9.2](./PUBLISHING.md#92-publint-errors) |
| `attw` red for `node16` or `bundler` | Real bug — fix exports map; do not publish |
| `attw` red only for `node10` | Acceptable — we don't support legacy resolution |
| `Cannot find module '@dreamhorizonorg/pulse-web'` after rename/install | Re-run `yarn install` from `pulse-web-otel/` to regenerate workspace symlinks |
| `bigint` ReactNode error in a workspace demo | Monorepo-only artefact of dual `@types/react` resolution; `resolutions` field in `pulse-web-otel/package.json` pins the version. Does not affect external consumers |

For everything else: [`PUBLISHING.md`](./PUBLISHING.md) has the long answers.
