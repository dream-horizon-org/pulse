# Manual tests — PulseWeb lifecycle, shutdown, disk buffer

Prereqs: demo running (`yarn demo` from `pulse-web-otel`), consent allowed, `PulseWeb` on `window` (ecommerce-demo `_PulseWebExpose`).

**Note:** Batch delay, compression, and endpoint base URL are not public `PulseWebConfig` fields. **Disk buffering** (IndexedDB failed-export replay) is **on by default** — same default as Android OTel RUM when `PulseSDK.initialize` does not pass a disk lambda (`DiskBufferingConfigurationSpec.isEnabled` defaults to `true`). The ecommerce demo can **opt out** with **`VITE_PULSE_DISK_BUFFER=false`** or **`?pulse_disk=0`**. Max age / cache size use SDK defaults; optional **`VITE_PULSE_DISK_BUFFER_MAX_AGE_MS`** / **`VITE_PULSE_DISK_BUFFER_MAX_SIZE_BYTES`** are read **inside the SDK** when buffering is active (like `VITE_PULSE_BATCH_DELAY_MS`).

---

## 1. `PulseWeb.shutdown()` — `forceFlush` (positive)

**Goal:** Pending batches flush; shutdown resolves without throw.

1. Open app, wait for `session.start`, then emit extra signals (navigate or `PulseWeb.trackEvent("shutdown_probe")`).
2. DevTools console:

   ```js
   await window.PulseWeb.shutdown();
   ```

3. **Expect:** Network shows **/v1/logs** (and/or traces/metrics) as flush runs; console **no** uncaught errors. Full reload for a fresh SDK instance.

---

## 2. Second `start()` — no-op (negative)

**Goal:** **One** `session.start` per successful init (Strict Mode double-invoke must not double-init).

1. Default demo load (`/`).
2. Count **`session.start`** for that page load before reload.
3. **Expect:** **Exactly one** (E2E: `m1.spec.ts` “double PulseWeb.start() is a no-op”).

---

## 3. `diskBuffering` — failed export persists, drain on reload (positive)

**Goal:** Non-retryable OTLP failure stores rows in IndexedDB **`pulse_signal_buffer`**; next load drains via `drainBufferedOtlpExports` (see E2E `@M1 disk buffer replay`).

1. Ensure disk buffering is on (default). If you previously used **`?pulse_disk=0`**, reload without that query.
2. Block **`*/v1/logs`** or go offline so the first log export fails while signals emit.
3. Application tab → IndexedDB → **`pulse_signal_buffer`** → **`signals`** has rows.
4. Unblock / online, reload.
5. **Expect:** Buffered payloads eventually POST; E2E asserts `session.start` after reload.

---

## 4. Disk buffer — prune / max size (edge)

Use tiny **`VITE_PULSE_DISK_BUFFER_MAX_AGE_MS`** / **`VITE_PULSE_DISK_BUFFER_MAX_SIZE_BYTES`** (see `src/persistence/indexed-db.ts`: `pruneExpired`, `enforceMaxSize`).

---

## Quick references

| Topic | Location |
|------|-----------|
| `start()`, `shutdown()`, drain | `pulse-web-otel/src/sdk.ts` |
| Disk defaults + Vite env merge | `pulse-web-otel/src/constants/disk-buffer.ts` |
| `PulseWebConfig.diskBuffering` | `pulse-web-otel/src/config.ts` |
| IDB store | `pulse-web-otel/src/persistence/indexed-db.ts` |
| E2E | `examples/ecommerce-demo/e2e/m1.spec.ts` |
