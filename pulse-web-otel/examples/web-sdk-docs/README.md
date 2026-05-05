# web-sdk-docs — vanilla Pulse Web SDK demo

Plain **HTML + CSS + JavaScript** (no React). Uses the same `@dreamhorizon/pulse-web` workspace package as `examples/ecommerce-demo`.

## What it demonstrates

- **Client-side routes** (`/`, `/install`, `/events`, `/errors`, `/user`) via the History API.
- **`PulseWeb.setScreenName(pathname)`** on every in-app navigation so `screen.name` follows the URL.
- **`PulseWeb.trackEvent`**, **`trackNonFatal`**, **`reportException`**, **`setUserId`** on dedicated pages.

## Run

From repo root `pulse-web-otel/`:

```bash
yarn install
yarn build
yarn demo:docs
```

Opens **http://localhost:3003** (see `vite.config.ts`).

Or from this folder:

```bash
yarn --cwd ../.. build
yarn dev
```

## Configuration

- Copy **`.env.example`** to **`.env.local`**.
- Set **`VITE_PULSE_MOCK_SDK_CONFIG=true`** to seed **`public/pulse-sdk-config.mock.json`** into `localStorage` before init (feature gates including `custom_events`), same pattern as the ecommerce demo.
- Consent: append **`?pulse_consent=denied`** to verify the SDK does not initialize.

## Compared to ecommerce-demo

| | ecommerce-demo | web-sdk-docs |
|---|----------------|--------------|
| UI | React + React Router | Vanilla JS |
| Port | 3002 | 3003 |
| E2E | Playwright web-sdk-gates | Manual only (v1) |

Full SDK contracts and plans live under `pulse-web-otel/web-sdk-plan/`.
