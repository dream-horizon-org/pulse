# ADR: XHR Request Header Capture via WeakMap Monkey-Patch (Option B)

**Status:** Accepted  
**Date:** 2026-05-15  
**Scope:** `pulse-web-otel/src/instrumentations/network.ts`

---

## Problem

`capturedRequestHeaders` works correctly on Fetch spans because the `Request`
object exposes `.headers.get()` at callback time. On XHR spans it silently
produces no `http.request.header.*` attributes.

Root cause: browsers hide sent headers after `xhr.send()`. There is no
`xhr.getRequestHeader()` API. By the time OTel's `applyCustomAttributesOnSpan`
fires (at `readyState === DONE`), all request header information is
inaccessible on the XHR object.

No warning was emitted anywhere — the config option appeared to work but
produced empty results.

---

## Decision: Option B — WeakMap capture at `setRequestHeader`

Monkey-patch `XMLHttpRequest.prototype.setRequestHeader` at
`NetworkInstrumentation.install()` time to intercept and store headers in a
module-scoped `WeakMap<XMLHttpRequest, Record<string, string>>` before
`send()` is called. In `applyCustomAttributesOnSpan`, read from the WeakMap
instead of the (unavailable) native API.

```
Host app calls                     Our patch runs
xhr.setRequestHeader(name, value)  →  xhrHeaderStore.set(xhr, {...})
                                       origSetRequestHeader.call(xhr, name, value)

Later, at readyState DONE:
applyCustomAttributesOnSpan(span, xhr)
  →  storedHeaders = xhrHeaderStore.get(xhr)
  →  xhrHeaderStore.delete(xhr)   // cleanup
```

This is the same pattern used by Sentry and PostHog for the same problem.

---

## Why not the alternatives

| Option | Verdict |
|--------|---------|
| A — `XMLHttpRequestInstrumentation` open/send intercept (re-implement OTel) | Duplicates OTel's own patching; fragile across OTel upgrades; rejected. |
| B — WeakMap patch at `setRequestHeader` | Clean interception point before send; pairs naturally with OTel's existing applyCustomAttributesOnSpan hook. **Chosen.** |
| C — `PerformanceResourceTiming` headers | `serverTiming` only; cannot reconstruct arbitrary request headers. Rejected. |

---

## Implementation details

- **Guard:** patch is only installed when `capturedRequestHeaders` is a
  non-empty array. Zero monkey-patch surface when the feature is unused.
- **Idempotency:** `installXhrHeaderPatch()` is a no-op if already patched
  (guards on `_origSetRequestHeader !== undefined`).
- **Call-through:** the patched function always calls through to
  `_origSetRequestHeader` — the browser still receives every header.
- **Cleanup:** `applyCustomAttributesOnSpan` calls `xhrHeaderStore.delete(xhr)`
  after reading headers. WeakMap semantics further prevent reference leaks if
  cleanup is somehow skipped.
- **Teardown:** `uninstall()` calls `uninstallXhrHeaderPatch()` which restores
  `XMLHttpRequest.prototype.setRequestHeader` to the original and clears
  `_origSetRequestHeader`. Safe to call multiple times.
- **SSR guard:** `typeof XMLHttpRequest !== "undefined"` checked before
  installing the patch.

---

## Consequences

### Positive

- `capturedRequestHeaders` now works for XHR spans with no API-surface change.
- Zero overhead when the feature is not configured.
- Fully reversible on `uninstall()`.

### Risks / mitigations

- **Second monkey-patch:** OTel's `XMLHttpRequestInstrumentation` already
  patches XHR prototype methods (`open`, `send`). Our patch targets
  `setRequestHeader` which OTel does not patch, so there is no conflict.
- **Order sensitivity:** `installXhrHeaderPatch()` is called inside `install()`
  after the `capturedRequestHeaders` check, before OTel's `enable()`. Order
  is deterministic.
- **Test isolation:** `_origSetRequestHeader` is module-scoped; tests must
  call `instr.uninstall()` in `afterEach` to restore the prototype. The test
  suite does this.

---

## References

- Sentry Browser SDK: `xhr-instrumentation.ts` — same WeakMap pattern
- PostHog: `xhr.ts` — captures headers at `setRequestHeader`
- OTel JS `instrumentation-xml-http-request`: does not patch `setRequestHeader`
