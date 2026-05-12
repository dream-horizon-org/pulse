# Publishing — canonical operational spec

**Package (npm):** `@dreamhorizonorg/pulse-web`  
**Repo directory:** `pulse-web-otel/` (not “pulse-web-sdk”; use this folder name in tooling and CI paths.)

This SPEC is the **index of truth** for ship gates, tarball shape, and doc hierarchy. Procedural detail (first publish, OIDC, Changesets YAML, troubleshooting tables) stays in [`./PUBLISHING.md`](./PUBLISHING.md). The one-page checklist is [`./QUICKSTART.md`](./QUICKSTART.md).

---

## 1. Goal

Give maintainers and agents a single place to answer: what ships to npm, how version/dist-tags work, which gates run before publish, and which docs apply to consumers vs maintainers — without stale paths to removed planning trees.

---

## 2. Assumptions / Research / Parity

- **Naming:** Public package name is `@dreamhorizonorg/pulse-web`. Repository layout uses `pulse-web-otel/` (matches `package.json` `repository.directory`).
- **Consumers** install from npm; integration wiring is documented in [`../instrumentations/integration/SPEC.md`](../instrumentations/integration/SPEC.md), not in publishing docs.
- **Signal contract** and instrumentation behaviour live under [`../instrumentations/`](../instrumentations/) (per-instrumentation SPECs + `sdk-core`).
- **SemVer + dist-tags** (`alpha` / `beta` / `latest`): aligned with [`./PUBLISHING.md`](./PUBLISHING.md) §1.
- **npm trusted publishing (OIDC)** is the target for CI; first manual publish may still be required to register the package (see long guide §2–3).

---

## 3. Requirements

| ID | Requirement |
|----|----------------|
| R1 | Every release candidate passes the same gates: `yarn lint`, `yarn test:run`, `yarn build`, `yarn size-limit`, `yarn publint`, `yarn attw`, and `npm pack --dry-run` review. |
| R2 | Scoped package publishes with `publishConfig.access: public` and correct `exports` map (including separate `import`/`require` **types** where dual emit exists). |
| R3 | Pre-`1.0` prereleases use `--tag alpha` (or `beta` per policy); do not assign `latest` until policy says so. |
| R4 | Maintainer docs must not reference deleted paths (`web-sdk-plan/`, legacy `INTEGRATION.md` only); integration content is [`../instrumentations/integration/SPEC.md`](../instrumentations/integration/SPEC.md). |
| R5 | Tarball contents match [`package.json`](../../package.json) `files` plus npm defaults (`package.json`, `README.md`, `LICENSE`, `CHANGELOG.md`). |

---

## 4. Architectural Design

- **Single npm package** with subpath exports (`./react`, `./react/router`, `./next`, `./next-config`) until triggers in [`./PUBLISHING.md`](./PUBLISHING.md) §6 justify sibling packages.
- **Documentation layering:**
  - **This SPEC** — scope, requirements, pointers, redundancy rules.
  - **`docs/publishing/PUBLISHING.md`** — full runbooks (manual first publish, OIDC setup, Changesets workflow sketch, deprecate/unpublish, peer comparisons).
  - **`docs/publishing/QUICKSTART.md`** — copy-paste commands and minimal tables.
  - **`CHANGELOG.md`** — user-visible release notes.
  - **`README.md`** — consumer-facing install and quick start (not maintainer publishing).

---

## 5. LLD (operational)

### 5.1 Package identity

- **Name:** `@dreamhorizonorg/pulse-web` ([`package.json`](../../package.json) `name`).
- **`files` field:** Ships `dist/`, `README.md`, `CHANGELOG.md`, `LICENSE`, `docs/publishing/SPEC.md`, `docs/publishing/PUBLISHING.md`, `docs/publishing/QUICKSTART.md` (see [`package.json`](../../package.json) `files`). Maintainer publishing docs live only under `docs/publishing/` — not at the package root.

### 5.2 Pre-publish commands (canonical order)

From repo root / package dir:

```bash
cd pulse-web-otel
yarn install --immutable   # or yarn install in dev
yarn lint
yarn test:run
yarn build
yarn size-limit
yarn publint
yarn attw
npm pack --dry-run
```

Quickstart variant: [`./QUICKSTART.md`](./QUICKSTART.md).

### 5.3 Publish command (alpha example)

```bash
npm publish --access public --tag alpha
```

### 5.4 Consumer integration

Do not duplicate integration recipes here. Point to [`../instrumentations/integration/SPEC.md`](../instrumentations/integration/SPEC.md) and framework SPECs (`react-integration`, `nextjs-integration`).

### 5.5 CI automation

[`./PUBLISHING.md`](./PUBLISHING.md) §4 describes a `release-web-sdk.yml` workflow and Changesets; **wire-up is tracked as rollout work** (workflow may not exist in-repo until implemented).

---

## 6. Test Coverage

| Layer | What |
|--------|------|
| Local gates | Scripts above; `attw` red on `node10` for subpaths may be acceptable per quickstart — red on `node16`/`bundler` is not. |
| Tarball | `npm pack --dry-run` matches expected file list. |
| Post-publish | Optional smoke: temp project `npm install @dreamhorizonorg/pulse-web@<tag>` and `require`/`import` entrypoints (see long guide §8). |

---

## 7. Known Bugs & Gaps

- **P0:** None tracked in this SPEC — publishing **workflow file** may be absent until DevOps adds it; until then releases are manual/semi-manual per [`./PUBLISHING.md`](./PUBLISHING.md).
- **Doc drift:** Long guide may cite line numbers or snippets that rot; prefer `package.json` and this tree as source of truth when they disagree.
- **`provenance`:** If `publishConfig.provenance` is added, align CI Node/npm with npm OIDC requirements (see [`./PUBLISHING.md`](./PUBLISHING.md) §2.3).

---

## 8. Redundancy & Cleanup Notes

| Doc | Role |
|-----|------|
| `docs/publishing/SPEC.md` (this file) | Canonical scope + pointers; keep updated when `files`, gates, or integration links change. |
| `docs/publishing/PUBLISHING.md` | Extended procedures; trim only obsolete path references, not the whole narrative. |
| `docs/publishing/QUICKSTART.md` | Short sheet; must stay consistent with §5.2 gates and tarball description. |

**Absorbed / obsolete paths:** Any reference to `web-sdk-plan/INTEGRATION.md` or versioned `web-sdk-plan/v*/04-contract-parity.md` should be replaced by `docs/instrumentations/integration/SPEC.md` and `docs/instrumentations/sdk-core/SPEC.md` respectively.

---

## 9. Open Questions

- Exact timeline for **Changesets** bootstrap and **GitHub Actions** publish workflow vs manual publishes.
- npm org handle consistency (`dreamhorizonorg` vs `dreamhorizon`) in runbooks — confirm against actual org membership commands (`npm org ls`).
