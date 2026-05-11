# Pulse Product — Web Panel

Standalone reader for the `product/` folder. Reads every `.md` file in `frameworks/`, `prds/`, and the product root, then serves a searchable, navigable web interface for browsing and presenting them.

## Run

```bash
cd product/web-panel
npm install
npm run dev
```

Opens automatically at <http://localhost:5174>.

## Build for static hosting

```bash
npm run build
```

Output goes to `dist/`. Drop the folder onto any static host (GitHub Pages, Netlify, Vercel, S3 + CloudFront, internal nginx, …).

## How it works

- All `.md` files in `../frameworks/`, `../prds/`, and `../*.md` are bundled at build time via Vite's `import.meta.glob`. Nothing is fetched at runtime — the content ships as part of the JS bundle.
- **Adding a new doc** = drop a new `.md` file in the right folder. The panel picks it up on the next dev reload or rebuild. No registry to update.
- **Filenames become URL slugs.** Use kebab-case: `funnel-diagnose-agent.md` → URL hash `#/prds/funnel-diagnose-agent`.
- **Filenames starting with `_`** (like `_template.md`) are sorted to the bottom of their group so they don't visually clutter the active doc list, but they remain visible.

## Features

- **Sidebar navigation** grouped by folder (Frameworks, PRDs, Overview).
- **Fuzzy search** across titles, filenames, and content using Fuse.js. Snippets show the matched context.
- **On-page TOC** auto-generated from `H2`/`H3` headings.
- **Hash-based routing** so links to specific docs are shareable.
- **Mobile-friendly** layout — sidebar collapses behind a menu button on narrow screens.
- **Print-friendly markdown styling** for tables, blockquotes, code blocks.

## Stack

React 18 · TypeScript · Vite 5 · react-markdown · remark-gfm · rehype-slug · Fuse.js · github-slugger

No CSS framework — plain CSS modules. Total dependency footprint is intentionally small so this stays cheap to maintain.

## Customising

- **Group order / labels** → `src/components/Sidebar.tsx` (`GROUP_LABELS` constant) and `src/lib/docs.ts` (`GROUP_ORDER`).
- **Markdown styling** → `src/components/DocViewer.module.css`.
- **Search behaviour** → `src/lib/search.ts` (Fuse threshold, weights).

## Notes

- The web panel is a presentation layer only. Source of truth lives in the `.md` files. Don't introduce features that depend on rendered state — every doc must remain readable and copy-pasteable on its own.
