# lottery-demo — Enriched Sample App Plan

> **Status: DRAFT — awaiting go-ahead before any implementation.**

---

## 1. What & Why

A new sample app under `pulse-web-otel/examples/lottery-demo/` that:

- Mirrors the real DH Lottery app stack exactly (Next.js + Tailwind + Capacitor)
- Runs fully on mocked data — no backend required
- Integrates `@dreamhorizonorg/pulse-web` and exercises **every signal type, both positive and negative**
- Replicates realistic lottery user journeys so signals match what we'd see in production
- Capacitor wrapper validates the SDK in the real native WebView environment

---

## 2. Stack

| Concern | Choice |
|---|---|
| Framework | Next.js 15 (App Router) |
| React | React 19 |
| Styling | Tailwind CSS v4 |
| Language | TypeScript strict |
| Data fetching | TanStack Query v5 |
| HTTP layer | native `fetch` → Next.js `/api/*` route handlers (real network spans) |
| Forms | react-hook-form |
| Mobile wrapper | Capacitor 8 |
| Package manager | yarn (workspace:*) |
| Data | Fully mocked — no real backend |

---

## 3. Screens & Routes

| Route | Screen | What happens there |
|---|---|---|
| `/` | Home | Lottery cards, banner carousel, winners ticker |
| `/lottery/[id]` | Lottery Detail | Prize table, series picker, buy CTA |
| `/lottery/[id]/choose` | Ticket Selection | Browse/search 10 000 tickets, qty selector |
| `/orders` | My Orders | Order history, ticket download |
| `/login` | Login | Mocked OTP flow — valid + invalid + expired + rate-limited |
| `/sdk-lab` | SDK Lab | Explicit trigger panel for every signal type |

---

## 4. Mock API Layer (why route handlers, not static JSON)

The SDK's network instrumentation patches `window.fetch`. If data is served from static imports, there are zero network spans. Using Next.js `/api/*` route handlers means every data load is a **real `fetch` call** → real `http` spans in Pulse.

Every handler adds `await sleep(randomBetween(100, 400))` for realistic latency.

```
app/api/
├── lotteries/route.ts            GET  → list (200)
├── lottery/[id]/route.ts         GET  → detail (200 | 404 | 410 gone/expired)
├── buy/route.ts                  POST → confirm (200 | 402 insufficient | 422 sale-closed)
├── otp/send/route.ts             POST → ok (200 | 429 rate-limited)
├── otp/verify/route.ts           POST → ok (200 | 400 wrong-OTP | 400 expired)
├── orders/route.ts               GET  → list (200)
├── banners/route.ts              GET  → list (200)
├── slow/route.ts                 GET  → 200 after 4 s delay (web vitals stress)
└── chaos/route.ts                GET  → 500 / timeout / abort (SDK Lab)
```

Controlled via query params so one handler covers positive + negative:
- `/api/lottery/demo-live` → 200 normal
- `/api/lottery/demo-expired` → 410 Gone (sale closed)
- `/api/lottery/demo-missing` → 404
- `/api/buy?scenario=ok` → 200
- `/api/buy?scenario=insufficient_balance` → 402
- `/api/buy?scenario=sale_closed` → 422

---

## 5. Signal Coverage — Complete Map

### 5.1 Network (`http`) — Positive Cases

| Trigger | Endpoint | Expected span |
|---|---|---|
| Home loads | GET `/api/lotteries` | 200, fast |
| Banner load | GET `/api/banners` | 200 |
| Open lottery card | GET `/api/lottery/demo-live` | 200 |
| Browse orders | GET `/api/orders` | 200 |
| Successful OTP send | POST `/api/otp/send` | 200 |
| Correct OTP verify | POST `/api/otp/verify` | 200 |
| Buy ticket happy path | POST `/api/buy?scenario=ok` | 200 |

### 5.2 Network (`http`) — Negative Cases (realistic lottery scenarios)

| Scenario | Endpoint | Status | What it mirrors in prod |
|---|---|---|---|
| Expired lottery detail | GET `/api/lottery/demo-expired` | 410 | Draw already happened |
| Missing lottery | GET `/api/lottery/demo-missing` | 404 | Bad deep link |
| Insufficient wallet balance | POST `/api/buy?scenario=insufficient_balance` | 402 | User hasn't topped up |
| Sale window closed at checkout | POST `/api/buy?scenario=sale_closed` | 422 | Race condition — sale ended while user was selecting |
| Wrong OTP entered | POST `/api/otp/verify?scenario=wrong_otp` | 400 | Typo in OTP |
| OTP expired | POST `/api/otp/verify?scenario=expired` | 400 | User waited too long |
| OTP rate limit (3 attempts) | POST `/api/otp/send?scenario=rate_limited` | 429 | Brute-force guard |
| Server error on lottery list | GET `/api/lotteries?scenario=server_error` | 500 | BE outage |
| Slow response (poor LCP) | GET `/api/slow` | 200 after 4 s | Poor network / congested CDN |
| Aborted request | fetch aborted via AbortController (SDK Lab) | — | User leaves page mid-load |
| Network offline simulation | navigator.onLine = false (SDK Lab) | — | Phone loses signal |

### 5.3 Errors — `device.crash`

| Trigger | How | Where |
|---|---|---|
| React render bomb | `throw new Error()` inside render | SDK Lab button → `PulseErrorBoundary` catches → `reportDeviceCrash` |
| Unhandled synchronous throw | `setTimeout(() => { throw new Error() }, 0)` | SDK Lab → `window.onerror` |
| Failed dynamic import | `import('/nonexistent-module')` | SDK Lab → `window.onerror` |
| Error boundary on route | Route-level `error.tsx` receives error, calls `reportDeviceCrash` | SDK Lab → navigate to crash route |
| JSON parse failure | `JSON.parse('not-json')` in a useEffect | SDK Lab |

### 5.4 Errors — `non_fatal`

| Trigger | How | Where |
|---|---|---|
| Unhandled promise rejection | `Promise.reject(new Error())` | SDK Lab → `window.unhandledrejection` |
| API 4xx manually reported | `reportException(error)` in fetch error handler | Buy flow (402, 422), OTP flow (400, 429) |
| API 5xx manually reported | `reportException(error)` | Lottery list 500 error state |
| Ticket already sold | UI catches "ticket taken" response, reports non_fatal | Choose screen, ticket already reserved |
| localStorage quota exceeded | Fill localStorage to limit, then write | SDK Lab |
| Manual `trackNonFatal` | `Pulse.trackNonFatal('form_validation_failed')` | OTP form validation |
| Manual `reportException` | `Pulse.reportException(err, { context: 'checkout' })` | SDK Lab |

### 5.5 Web Vitals — All 6, Good + Poor

Web Vitals fire automatically from the SDK. The demo deliberately creates both good and bad conditions.

| Vital | Good threshold | Poor threshold | How we trigger poor |
|---|---|---|---|
| **LCP** Largest Contentful Paint | < 2.5 s | > 4 s | Home → show a hero image loaded from `/api/slow` (4 s delay) |
| **CLS** Cumulative Layout Shift | < 0.1 | > 0.25 | "Winners ticker" banner injected above content 500 ms after load (no height reservation) |
| **INP** Interaction to Next Paint | < 200 ms | > 500 ms | SDK Lab "Block main thread" button runs a 600 ms sync loop then updates DOM |
| **FCP** First Contentful Paint | < 1.8 s | > 3 s | Naturally fast on dev; slow on `/api/slow` route |
| **FID** First Input Delay | < 100 ms | > 300 ms | Same main-thread block as INP (FID is FID of first input only) |
| **TTFB** Time to First Byte | < 800 ms | > 1.8 s | `/api/slow` route sleeps before responding |

Good conditions fire naturally during normal navigation. Poor conditions are triggered by SDK Lab controls and the `/api/slow` endpoint.

### 5.6 Sessions

| Signal | When it fires | How we trigger in app |
|---|---|---|
| `session.start` (new install) | First ever app open | Clear localStorage → reload |
| `session.start` (normal) | Every app open | Normal page load |
| `session.start` (after timeout) | Idle > 30 min | SDK Lab "Advance session clock" button (sets session start time back 31 min in localStorage) |
| `session.start` (after login) | Login completes, user identity attached | Complete mock OTP flow |
| `session.end` (tab close) | `pagehide` fires | Close tab |
| `session.end` (timeout) | 30 min idle | Triggered via clock manipulation in SDK Lab |
| `pulse.app.installation.start` | First ever install only | Clear installation ID from localStorage → reload |

### 5.7 Custom Events (realistic lottery events)

| Event name | Fired on | Attributes |
|---|---|---|
| `home_screen_loaded` | Home mount | `user_id`, `is_new_user` |
| `lottery_card_clicked` | Tap lottery card | `lottery_id`, `lottery_name`, `prize_amount` |
| `ticket_pick_random` | Random pick button | `lottery_id`, `series` |
| `ticket_pick_manual` | Manual ticket chosen | `lottery_id`, `ticket_number` |
| `cart_checkout_click` | "Buy Now" tapped | `ticket_count`, `total_amount` |
| `otp_sent` | OTP send success | `masked_mobile` |
| `otp_verified` | OTP verify success | `is_new_user` |
| `ticket_purchased` | Buy success | `lottery_id`, `ticket_count`, `amount` |
| `purchase_failed` | Buy 4xx/5xx | `reason`, `error_code` |
| `order_viewed` | Orders screen | `order_count` |
| `session_rotate_manual` | SDK Lab button | — |

### 5.8 Clicks & Interactions (auto-captured)

Every button and link in the app is data-testid-labelled so the `app.click` auto-instrumentation produces named signals. Key interactions:

- "Pick random ticket" button (lottery detail)
- Ticket number cells in 10k grid (choose screen) — captures scroll depth + tap position
- "Buy Now" CTA
- Bottom nav tabs
- Filter/search on ticket grid → many rapid interactions

Rage-click target: the "Buy Now" button when it's disabled (sale closed state) — users typically tap it repeatedly when frustrated. This mirrors real prod behaviour.

### 5.9 Screen Tracking / Navigation

`useRouterTracking` is only wired for `react-router-dom`. Next.js App Router uses `usePathname` from `next/navigation`. We create a thin wrapper:

```tsx
// app/components/PulseRouterTracker.tsx ('use client')
'use client'
import { usePathname } from 'next/navigation'
import { useEffect } from 'react'
import { Pulse } from '@dreamhorizonorg/pulse-web'

const ROUTE_NAMES: Record<string, string> = {
  '/':                    'Home',
  '/lottery':             'LotteryList',
  '/orders':              'Orders',
  '/login':               'Login',
  '/sdk-lab':             'SdkLab',
}
function resolveScreenName(pathname: string): string {
  if (pathname.endsWith('/choose')) return 'TicketSelection'
  if (pathname.startsWith('/lottery/')) return 'LotteryDetail'
  return ROUTE_NAMES[pathname] ?? pathname
}
export function PulseRouterTracker() {
  const pathname = usePathname()
  useEffect(() => {
    Pulse.setScreenName(resolveScreenName(pathname))
  }, [pathname])
  return null
}
```

---

## 6. Realistic User Journeys (end-to-end signal chains)

These journeys are walkable in the running app — not just SDK Lab buttons. Each step produces a traceable signal.

### Journey 1 — Happy Path: New User Buys Ticket
```
1. First open → installation.start + session.start(reason=new_install)
2. Home loads → screen_load(Home) + LCP + http(GET /api/lotteries, 200)
3. Tap "Lohri Special" card → app.click + lottery_card_clicked + screen_load(LotteryDetail)
4. Lottery detail loads → http(GET /api/lottery/demo-live, 200)
5. Tap "Pick random" → app.click + ticket_pick_random
6. Tap "Buy Now" → app.click + cart_checkout_click + screen_load(Checkout)
7. Checkout → http(POST /api/buy?scenario=ok, 200) + ticket_purchased
8. View orders → screen_load(Orders) + http(GET /api/orders, 200)
9. Close tab → session.end(reason=pagehide)
```

### Journey 2 — OTP Errors: Wrong → Expired → Rate Limited
```
1. Open login → session.start + screen_load(Login)
2. Enter mobile → app.click(send-otp) + otp_send → http(POST /api/otp/send, 200) + otp_sent
3. Enter wrong OTP → http(POST /api/otp/verify?scenario=wrong_otp, 400)
   → reportException(WrongOtpError) → non_fatal
4. Enter expired OTP (wait too long) → http(POST /api/otp/verify?scenario=expired, 400)
   → reportException(OtpExpiredError) → non_fatal + trackNonFatal('otp_expired')
5. Hit "Resend" 3 times fast → http(POST /api/otp/send?scenario=rate_limited, 429)
   → reportException(RateLimitError) → non_fatal
```

### Journey 3 — Sale Race Condition
```
1. Open lottery (sale active) → http(GET /api/lottery/demo-live, 200)
2. Spend 5 min selecting tickets (sale closes during selection)
3. Tap "Buy Now" → http(POST /api/buy?scenario=sale_closed, 422)
   → reportException(SaleClosedError) → non_fatal + purchase_failed(reason=sale_closed)
4. UI shows "Sale has ended" banner → CLS spike (banner injected above content)
```

### Journey 4 — Server Degradation
```
1. Home loads → http(GET /api/lotteries?scenario=server_error, 500)
   → reportException(ServerError) → non_fatal
2. Error state shown, user taps "Retry" → rage-click if retry is slow
3. Retry succeeds → http(GET /api/lotteries, 200)
```

### Journey 5 — Poor Network (slow 4G simulation)
```
1. Home → http(GET /api/slow, 200 after 4 s) → poor LCP (>4 s) + poor TTFB
2. Session active, user waits → INP measured on first tap
3. Buy → http(POST /api/buy, 200 after 3 s) → slow network span
```

### Journey 6 — App Crash Recovery
```
1. SDK Lab → "Throw React render error"
   → PulseErrorBoundary.componentDidCatch → reportDeviceCrash
   → Error boundary UI shown with reset button
2. User taps "Try again" → boundary resets → session continues (not a new session)
3. SDK Lab → "Throw uncaught error"
   → window.onerror → device.crash
4. Page reloads → session.start(reason=error_recovery)
   → IndexedDB drain flushes buffered signals from previous session
```

---

## 7. SDK Lab Screen — Complete Trigger Panel

Sections and controls:

### Errors
| Button | Signal | Notes |
|---|---|---|
| Throw uncaught error | `device.crash` | `setTimeout(() => throw)` |
| Unhandled rejection | `non_fatal` | `Promise.reject()` |
| React render bomb | `device.crash` | Throws in render → `PulseErrorBoundary` |
| Navigate to crash route | `device.crash` | Hits `error.tsx` boundary |
| reportDeviceCrash() | `device.crash` | Manual call |
| reportException() | `non_fatal` | Manual call with custom attrs |
| trackNonFatal() | `non_fatal` | Named non-fatal |
| JSON parse error | `non_fatal` | `JSON.parse('bad')` in useEffect |
| localStorage quota | `non_fatal` | Fill storage then write |

### Network
| Button | Signal | Notes |
|---|---|---|
| Trigger 404 | `http` span, status 404 | GET `/api/chaos?status=404` |
| Trigger 429 | `http` span, status 429 | GET `/api/chaos?status=429` |
| Trigger 500 | `http` span, status 500 | GET `/api/chaos?status=500` |
| Trigger timeout | `http` span, no response | Fetch with 1 s AbortController timeout |
| Trigger abort | `http` span, aborted | AbortController.abort() immediately |
| Slow request (4 s) | `http` span, slow | GET `/api/slow` |

### Web Vitals
| Button | Signal | Notes |
|---|---|---|
| Trigger poor INP | `web_vital` INP > 500 ms | Sync loop blocks main thread 600 ms then DOM update |
| Trigger CLS | `web_vital` CLS > 0.25 | Inject tall banner above existing content |
| Load slow hero image | `web_vital` LCP > 4 s | Replace hero with image from `/api/slow` |

### Session
| Button | Signal | Notes |
|---|---|---|
| Rotate session | `session.end` + `session.start` | Advance session timestamp 31 min |
| Simulate new install | `installation.start` + `session.start` | Clear installation ID + reload |
| Force session flush | — | `Pulse.loggerProvider.forceFlush()` |

### Custom Events
| Button | Signal |
|---|---|
| Fire custom event | `custom_event` with arbitrary attrs |
| Batch stress (600 events) | 600× `custom_event` — tests batch overflow |

---

## 8. Pulse SDK Init (SSR-safe, Next.js)

`PulseProvider` from `@dreamhorizonorg/pulse-web/react` is already marked `'use client'` and SSR-safe. Drop into `layout.tsx`:

```tsx
// app/providers/PulseProvider.tsx
'use client'
import { PulseProvider as SDKPulseProvider } from '@dreamhorizonorg/pulse-web/react'
import { PulseDataCollectionConsent } from '@dreamhorizonorg/pulse-web'

export function PulseProvider({ children }) {
  return (
    <SDKPulseProvider
      shutdownOnUnmount={false}
      config={{
        apiKey: process.env.NEXT_PUBLIC_PULSE_API_KEY ?? 'default-project_devkey01',
        serviceName: 'lottery-demo',
        serviceVersion: '1.0.0',
        dataCollectionState: PulseDataCollectionConsent.ALLOWED,
        instrumentations: {
          errors:    { enabled: true },
          network:   { enabled: true },
          clicks:    { enabled: true },
          webVitals: { enabled: true },
          navigation:{ enabled: true },
          session:   { enabled: true },
        },
        beforeSendData: {
          // Scrub auth headers before export
          onBeforeSendNetworkRequest: (req) => {
            const sanitised = { ...req }
            delete sanitised.requestHeaders?.['authorization']
            return sanitised
          }
        }
      }}
    >
      {children}
    </SDKPulseProvider>
  )
}
```

`PulseProvider` wraps `PulseErrorBoundary` internally — React render errors auto-report as `device.crash` without any extra wiring.

`PulseRouterTracker` mounts once in `layout.tsx` to call `setScreenName` on every Next.js App Router navigation.

---

## 9. error.tsx / global-error.tsx Wiring

Next.js route-level and root error boundaries require explicit `reportDeviceCrash` calls:

```tsx
// app/error.tsx and app/global-error.tsx
'use client'
import { useEffect } from 'react'
import { Pulse } from '@dreamhorizonorg/pulse-web'

export default function Error({ error }: { error: Error }) {
  useEffect(() => {
    Pulse.reportDeviceCrash(error, { boundary: 'route_error_boundary' })
  }, [error])
  // ... error UI
}
```

---

## 10. Capacitor Setup

```ts
// capacitor.config.ts
import type { CapacitorConfig } from '@capacitor/cli'

const config: CapacitorConfig = {
  appId: 'com.dreamhorizon.lotterydemo',
  appName: 'LotteryDemo',
  webDir: 'out',          // next export output
  server: {
    // Dev: point at local Next.js dev server
    // Comment out for production native build
    url: 'http://YOUR_IP:3001',
    cleartext: true,
  },
}
export default config
```

Native projects (`android/`, `ios/`) committed to repo. Build artifacts excluded:
- `android/.gitignore`: excludes `build/`, `.gradle/`, `local.properties`
- `ios/.gitignore`: excludes `Pods/`, `DerivedData/`, `*.xcarchive`

Native build:
```bash
npm run build          # next build + next export → /out
npx cap sync android   # copy web assets + update plugins
npx cap open android   # open in Android Studio → run on device

npm run build
npm run sync:ios       # mirrors lottery-frontend pattern
npx cap open ios       # open in Xcode → run on device/simulator
```

---

## 11. File Structure

```
lottery-demo/
├── package.json
├── next.config.ts          (output: 'export', basePath: none)
├── tailwind.config.ts
├── postcss.config.mjs
├── capacitor.config.ts
├── tsconfig.json
├── .env.example            (NEXT_PUBLIC_PULSE_API_KEY)
├── android/
├── ios/
├── public/
│   └── mock-images/
├── app/
│   ├── layout.tsx          (QueryProvider > UserProvider > PulseProvider > PulseRouterTracker)
│   ├── page.tsx            (Home)
│   ├── error.tsx           (calls reportDeviceCrash)
│   ├── global-error.tsx    (calls reportDeviceCrash)
│   ├── globals.css         (Tailwind + lottery gold/sapphire palette)
│   ├── providers/
│   │   ├── PulseProvider.tsx
│   │   └── QueryProvider.tsx
│   ├── components/
│   │   ├── PulseRouterTracker.tsx
│   │   ├── NavBar.tsx
│   │   ├── BottomNav.tsx
│   │   ├── LotteryCard.tsx
│   │   ├── BannerCarousel.tsx
│   │   ├── PrizeBreakupTable.tsx
│   │   ├── TicketGrid.tsx          (10 000 ticket grid with search + filter)
│   │   ├── CartFooter.tsx
│   │   └── PulseDebugPanel.tsx     (ported from ecommerce-demo)
│   ├── context/
│   │   └── UserContext.tsx         (mock user / login state)
│   ├── data/
│   │   └── mock/
│   │       ├── lotteries.json
│   │       ├── lottery-live.json
│   │       ├── lottery-expired.json
│   │       └── orders.json
│   ├── hooks/
│   │   ├── useLotteries.ts
│   │   ├── useLottery.ts
│   │   └── useOrders.ts
│   ├── lib/
│   │   └── api.ts                  (fetch wrapper, reports non_fatal on 4xx/5xx)
│   ├── api/
│   │   ├── lotteries/route.ts
│   │   ├── lottery/[id]/route.ts
│   │   ├── buy/route.ts
│   │   ├── otp/
│   │   │   ├── send/route.ts
│   │   │   └── verify/route.ts
│   │   ├── orders/route.ts
│   │   ├── banners/route.ts
│   │   ├── slow/route.ts
│   │   └── chaos/route.ts
│   ├── login/page.tsx
│   ├── lottery/
│   │   └── [id]/
│   │       ├── page.tsx
│   │       ├── error.tsx
│   │       └── choose/page.tsx
│   ├── orders/page.tsx
│   └── sdk-lab/page.tsx
```

---

## 12. Run Commands

```bash
# Dev (web browser)
cd pulse-web-otel
yarn install
yarn workspace lottery-demo dev          # → http://localhost:3001

# Build SDK + demo together
yarn build && yarn workspace lottery-demo build

# Native Android
cd examples/lottery-demo
npm run build          # generates /out via next export
npx cap sync android
npx cap open android   # run on device/emulator in Android Studio

# Native iOS
npm run build
npm run sync:ios       # cap sync ios
npx cap open ios       # run on device/simulator in Xcode
```

Root `package.json` additions:
```json
"lottery-demo":         "yarn workspace lottery-demo dev",
"lottery-demo:build":   "yarn build && yarn workspace lottery-demo build"
```

---

## 13. Signals Checklist (total coverage)

| Signal | Auto | Manual | + case | - case |
|---|---|---|---|---|
| `session.start` | ✅ | — | app open | timeout / post-crash |
| `session.end` | ✅ | — | tab close | pagehide in WebView |
| `pulse.app.installation.start` | ✅ | — | first install | — |
| `http` network | ✅ | — | 200 lotteries / buy | 4xx / 5xx / timeout / abort |
| `app.click` | ✅ | — | every button | rage-click (disabled Buy Now) |
| `web_vital` LCP | ✅ | — | fast home | slow hero image |
| `web_vital` CLS | ✅ | — | stable layout | injected banner |
| `web_vital` INP | ✅ | — | normal click | main-thread block |
| `web_vital` FCP | ✅ | — | fast load | slow route |
| `web_vital` TTFB | ✅ | — | fast API | `/api/slow` |
| `device.crash` | ✅ (boundary) | ✅ (SDK Lab) | — | render bomb / uncaught / dynamic import fail |
| `non_fatal` | ✅ (unhandled rejection) | ✅ (api errors) | — | 400/402/422/429/500 + quota exceeded |
| `custom_event` | — | ✅ | purchase / otp flows | purchase_failed |
| `screen_load` (navigation) | ✅ | — | every route | — |
| `rum.sdk.init.*` | ✅ | — | SDK start | — |

---

## 14. Decisions Locked In

| Decision | Value |
|---|---|
| Name | `lottery-demo` |
| Port | `3001` |
| SDK Lab | Always visible in nav |
| Capacitor native projects | Committed to repo, build artifacts gitignored |
| PulseDebugPanel | Ported from ecommerce-demo |
