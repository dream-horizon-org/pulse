# M4 — Framework Completion + Build Pipeline + Publish

## Context
Completes V1 by adding Next.js and CDN integrations, finalising the tsup build config for all entry points, adding GitHub Actions CI/CD, enforcing the 30 KB bundle budget, and triggering the first production npm publish. After M4 any web project (React, Next.js, or plain HTML) can install and use the SDK.

## Prerequisites
- M2 complete: React integration done, `@dreamhorizon/pulse-web@0.1.0-alpha.1` alpha published
- M3 complete: all 6 signal types verified in ClickHouse
- `pulse-web-otel/web-sdk-plan/v1/MILESTONES.md` M2 + M3 checkboxes all `[x]`

## Spec Docs to Read First
1. `pulse-web-otel/web-sdk-plan/v1/04-frameworks/nextjs.md` — App Router + Pages Router integration
2. `pulse-web-otel/web-sdk-plan/v1/04-frameworks/cdn-vanilla.md` — async snippet + queue drain
3. `pulse-web-otel/web-sdk-plan/v1/05-build-distribution/index.md` — tsup config, exports map, size-limit, CI/CD

## Files to Create

| File | Spec doc |
|---|---|
| `src/integrations/nextjs/index.ts` | `nextjs.md` — barrel export |
| `src/integrations/nextjs/PulseNextProvider.tsx` | `nextjs.md` |
| `src/integrations/cdn/snippet.js` | `cdn-vanilla.md` |
| `src/version.ts` | `index.md` — `__SDK_VERSION__` |
| `.size-limit.json` | `index.md` |
| `.github/workflows/ci.yml` | `index.md` |
| `.github/workflows/publish.yml` | `index.md` |
| `examples/nextjs-app/` | `nextjs.md` — working Next.js example |
| `examples/cdn-vanilla/` | `cdn-vanilla.md` — plain HTML example |

## Files to Update
| File | Change |
|---|---|
| `tsup.config.ts` | Full multi-entry config (see below) |
| `package.json` | Add `./nextjs` and `./cdn` exports entries; add `size-limit` script |
| `src/index.ts` | Replace `__SDK_VERSION__` stub with import from `version.ts` |

---

## Key Implementation Notes

### `PulseNextProvider` — App Router (`src/integrations/nextjs/PulseNextProvider.tsx`)
```typescript
'use client'; // Required for App Router
import { usePathname } from 'next/navigation';
export function PulseNextProvider({ config, children }) {
  useEffect(() => { PulseWeb.start(config) }, []);  // once
  const pathname = usePathname();
  useEffect(() => { /* emit screen_session span */ }, [pathname]);
  return <>{children}</>;
}
```
- SSR guard: `if (typeof window === 'undefined') return children` (also handled by `'use client'`)

### `PulseNextProvider` — Pages Router
```typescript
import { useRouter } from 'next/router';
export function PulseNextProvider({ config, children }) {
  useEffect(() => { PulseWeb.start(config) }, []);
  const router = useRouter();
  useEffect(() => {
    const handler = (url) => { /* emit screen_session span */ };
    router.events.on('routeChangeComplete', handler);
    return () => router.events.off('routeChangeComplete', handler);
  }, [router]);
  return <>{children}</>;
}
```

### CDN Async Snippet (`src/integrations/cdn/snippet.js`)
```javascript
(function(w,d,s,n){
  w[n] = w[n] || { _q: [], start: function(c){ this._q.push(['start',c]); } };
  var script = d.createElement(s);
  script.async = true;
  script.src = 'https://cdn.pulse.io/web/v1/pulse-web.umd.js';
  script.onload = function() {
    var q = w[n]._q; w[n] = PulseWeb;
    q.forEach(function(call){ w[n][call[0]].apply(w[n], call.slice(1)); });
  };
  d.head.appendChild(script);
})(window, document, 'script', 'PulseWeb');
```

### `tsup.config.ts` — Full Multi-Entry
```typescript
import { defineConfig } from 'tsup';
export default defineConfig([
  {
    entry: { index: 'src/index.ts' },
    format: ['esm', 'cjs'],
    dts: true, clean: true, sourcemap: true,
    define: { __SDK_VERSION__: JSON.stringify(process.env.npm_package_version) },
    external: ['react', 'react-dom', 'react-router-dom', 'next'],
  },
  {
    entry: { react: 'src/integrations/react/index.ts' },
    format: ['esm', 'cjs'], dts: true,
    external: ['react', 'react-dom', 'react-router-dom', '@dreamhorizon/pulse-web'],
  },
  {
    entry: { nextjs: 'src/integrations/nextjs/index.ts' },
    format: ['esm', 'cjs'], dts: true,
    external: ['react', 'next', '@dreamhorizon/pulse-web'],
  },
  {
    entry: { 'index.umd': 'src/index.ts' },
    format: ['iife'], globalName: 'PulseWeb',
    minify: true, sourcemap: true,
    define: { __SDK_VERSION__: JSON.stringify(process.env.npm_package_version) },
  },
]);
```

### `.size-limit.json`
```json
[
  { "path": "dist/index.js",      "limit": "30 kB" },
  { "path": "dist/react.js",      "limit": "2 kB",  "import": "{ PulseProvider }" },
  { "path": "dist/index.umd.js",  "limit": "80 kB" }
]
```

### `ci.yml` — PR checks
```yaml
on: [pull_request]
jobs:
  ci:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: 20 }
      - run: yarn install --frozen-lockfile
      - run: yarn tsc --noEmit
      - run: yarn lint
      - run: yarn test --run
      - run: yarn build
      - run: yarn size-limit
```

### `publish.yml` — Release pipeline (trigger: `pulse-web@*` tag)
```yaml
on:
  push:
    tags: ['pulse-web@*']
jobs:
  publish:
    steps:
      - yarn install --frozen-lockfile
      - yarn test --run
      - yarn build
      - yarn size-limit
      - npm publish --access public
      - aws s3 sync dist/ s3://pulse-cdn/web/${TAG}/
      - aws cloudfront create-invalidation ...
      - gh release create ${TAG} --generate-notes
```

### Example Apps
- `examples/nextjs-app/`: `app/layout.tsx` with `<PulseNextProvider>` (App Router); `.env.local.example`
- `examples/cdn-vanilla/`: `index.html` with the async snippet inline; no bundler needed

## Done Criteria
- [ ] Next.js App Router: `<PulseNextProvider>` in `app/layout.tsx` — no SSR errors, route changes tracked
- [ ] Next.js Pages Router: `<PulseNextProvider>` in `_app.tsx` — same
- [ ] CDN snippet: `window.PulseWeb.start()` called before bundle loads → drains queue correctly
- [ ] `yarn build` produces all 5 dist files: `index.js`, `index.cjs`, `react.js`, `nextjs.js`, `index.umd.js`
- [ ] All entry points have `.d.ts` type declarations
- [ ] `node -e "require('@dreamhorizon/pulse-web')"` — no error (CJS works)
- [ ] `import { PulseWeb } from '@dreamhorizon/pulse-web'` tree-shakes in Vite build
- [ ] `rum.sdk.version` in emitted spans matches `package.json` version
- [ ] `yarn size-limit` passes: core < 30 KB, CDN UMD < 80 KB
- [ ] CI pipeline green on a test PR
- [ ] Release tag `pulse-web@0.1.0-alpha` triggers publish pipeline
- [ ] CDN URL returns 200 with `Content-Encoding: gzip`
- [ ] `examples/nextjs-app` and `examples/cdn-vanilla` both work end-to-end

## Verification
```bash
# Build check
cd pulse-web-otel
yarn build
ls dist/   # expect: index.js, index.cjs, index.d.ts, react.js, nextjs.js, index.umd.js
yarn size-limit

# CJS check
node -e "const { PulseWeb } = require('./dist/index.cjs'); console.log(typeof PulseWeb.start)"

# Next.js example
cd examples/nextjs-app && yarn install && yarn dev
# No "localStorage is not defined" in terminal output

# CDN example
cd examples/cdn-vanilla && npx serve .
# Open browser → check Network tab for OTLP POST
```
Update `pulse-web-otel/web-sdk-plan/v1/MILESTONES.md` M4 checkboxes when all pass.
