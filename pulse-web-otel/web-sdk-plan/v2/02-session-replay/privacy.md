# 04.2 — Session Replay Privacy

**Goal:** Define and implement the complete privacy control layer for session replay — masking sensitive input fields, blocking private DOM subtrees, and ensuring PII never leaves the browser.

**File:** `src/replay/privacy.ts` + rrweb config options
**Android equivalent:** None — web-exclusive

---

## Privacy Threat Model

Session replay records DOM structure and user interactions. Without controls, it can capture:
- Passwords typed into `<input type="password">`
- Credit card numbers, CVV, SSN in form fields
- Private chat messages or support tickets
- Names and email addresses in profile pages
- Content inside private document viewers

The goal is to give developers a simple, opt-out model where **sensitive data is masked by default**.

---

## Privacy Controls

### 1. Input Masking (Default: All Inputs Masked)

By default, **all input values are replaced with `*` characters** of the same length, preserving form layout without leaking values.

```typescript
// rrweb config in recorder (04.1)
maskAllInputs: true,
maskInputOptions: {
  password: true,     // always
  email: true,        // default: true
  tel: true,          // default: true
  text: false,        // default: false (allow text if not sensitive)
  number: false,
  search: false,
}
```

Developers can loosen this per-input:
```html
<!-- Explicitly allow recording -->
<input type="text" data-pulse-unmask />
```

### 2. CSS Class Masking — `pulse-mask`

Any element with the class `pulse-mask` has its **text content replaced with ` █ ` characters** and its children's values blocked.

```html
<span class="pulse-mask">John Smith</span>
<!-- Recorded as: ████████████ -->

<div class="pulse-mask">
  <p>Private message content here</p>
  <!-- Entire subtree is masked -->
</div>
```

### 3. CSS Class Blocking — `pulse-block`

Any element with the class `pulse-block` is **completely excluded from recording** — replaced with a placeholder div of the same size. Neither the element nor its children are recorded.

```html
<div class="pulse-block">
  <!-- Credit card form — never recorded -->
  <input type="text" placeholder="Card number" />
  <input type="text" placeholder="CVV" />
</div>
```

### 4. URL Scrubbing

Network request URLs captured by other instrumentations (02.2) strip query params. Session replay itself records `window.location.href` on navigation events — the query string is stripped:

```typescript
// In recorder config
maskURLs: true,  // strips query string from URL in events
```

---

## Configuration Interface

```typescript
export interface ReplayPrivacyConfig {
  /** Mask all input values (default: true) */
  maskAllInputs?: boolean;

  /** Fine-grained control by input type */
  maskInputOptions?: {
    password?: boolean;   // always true
    email?: boolean;      // default: true
    tel?: boolean;        // default: true
    text?: boolean;       // default: false
    number?: boolean;     // default: false
    search?: boolean;     // default: false
    url?: boolean;        // default: false
    color?: boolean;      // default: false
    date?: boolean;       // default: false
    range?: boolean;      // default: false
    textarea?: boolean;   // default: false
  };

  /** CSS class to mask element text (default: 'pulse-mask') */
  maskTextClass?: string;

  /** CSS class to block element entirely (default: 'pulse-block') */
  blockClass?: string;

  /** CSS selectors to always block, regardless of class (advanced) */
  blockSelector?: string;  // e.g. '[data-private]'

  /** Strip query params from recorded URLs (default: true) */
  maskURLs?: boolean;
}
```

### Merge with Defaults

```typescript
function buildPrivacyConfig(userConfig: ReplayPrivacyConfig = {}): Required<ReplayPrivacyConfig> {
  return {
    maskAllInputs: userConfig.maskAllInputs ?? true,
    maskInputOptions: {
      password: true,   // always — never configurable
      email:    true,
      tel:      true,
      text:     false,
      number:   false,
      search:   false,
      url:      false,
      color:    false,
      date:     false,
      range:    false,
      textarea: false,
      ...userConfig.maskInputOptions,
      password: true,   // enforce after spread
    },
    maskTextClass:  userConfig.maskTextClass  ?? 'pulse-mask',
    blockClass:     userConfig.blockClass     ?? 'pulse-block',
    blockSelector:  userConfig.blockSelector  ?? '',
    maskURLs:       userConfig.maskURLs       ?? true,
  };
}
```

---

## Default Safe State

```
Privacy posture when no config is provided:

✅ All input values       → replaced with **** 
✅ Passwords              → always masked
✅ Emails/phones          → masked by default
✅ .pulse-mask elements   → text replaced with blocks
✅ .pulse-block elements  → completely excluded
✅ Query params in URLs   → stripped
```

---

## Edge Cases

| Case | Handling |
|---|---|
| `<input type="password">` — developer sets `maskAllInputs: false` | `password: true` in `maskInputOptions` is enforced regardless |
| Shadow DOM elements with sensitive content | rrweb v2 supports shadow DOM serialisation; `blockClass` applies within shadow roots |
| `<iframe>` content | Same-origin iframes can be recorded with `recordIframe: true`; cross-origin always blocked |
| React controlled inputs | rrweb hooks `addEventListener` — works with React synthetic events |
| Dynamic content added after recording starts | `MutationObserver` in rrweb captures new nodes; privacy rules applied at serialisation time |
| SVG text content | Treated as regular text; `pulse-mask` applies |
| `contenteditable` elements | Treated as inputs; `maskAllInputs` applies |

---

## Developer Integration Example

```typescript
PulseSDK.init({
  projectId: 'proj_abc123',
  replay: {
    enabled: true,
    privacy: {
      maskAllInputs: false,       // Allow non-sensitive inputs to be recorded
      maskInputOptions: {
        password: true,           // Always mask passwords
        email: true,              // Always mask emails
        text: false,              // Allow text inputs
      },
      blockSelector: '[data-private]',  // Block any element with data-private attr
    },
  },
});
```

And in the app's HTML:

```html
<!-- Sensitive section — never recorded -->
<div data-private>
  <p>Patient diagnosis: ...</p>
</div>

<!-- Normal content — recorded -->
<div>
  <p>Product description: Great shoes for hiking</p>
</div>

<!-- Name shown but masked in replay -->
<span class="pulse-mask">{{ user.fullName }}</span>
```

---

## Testing

### Unit Tests (Vitest + JSDOM)

```typescript
it('enforces password masking even when maskAllInputs is false', () => {
  const config = buildPrivacyConfig({ maskAllInputs: false });
  expect(config.maskInputOptions.password).toBe(true);
});

it('applies defaults when no config provided', () => {
  const config = buildPrivacyConfig();
  expect(config.maskAllInputs).toBe(true);
  expect(config.maskTextClass).toBe('pulse-mask');
  expect(config.blockClass).toBe('pulse-block');
  expect(config.maskURLs).toBe(true);
});

it('merges user maskInputOptions with defaults', () => {
  const config = buildPrivacyConfig({
    maskInputOptions: { text: true }
  });
  expect(config.maskInputOptions.text).toBe(true);
  expect(config.maskInputOptions.email).toBe(true); // still default
  expect(config.maskInputOptions.password).toBe(true); // enforced
});
```

### Integration Tests (Vitest + rrweb)

```typescript
it('does not record value of masked input', async () => {
  const events: eventWithTime[] = [];
  const stopFn = record({
    emit: e => events.push(e),
    maskAllInputs: true,
  });

  const input = document.createElement('input');
  document.body.appendChild(input);
  input.value = 'secret-password';
  input.dispatchEvent(new Event('input'));

  stopFn?.();

  const inputEvents = events.filter(e =>
    e.type === EventType.IncrementalSnapshot &&
    (e.data as any).source === 5 // IncrementalSource.Input
  );
  expect(inputEvents.every(e => !(e.data as any).text.includes('secret'))).toBe(true);
});
```

---

## Done Criteria

- [ ] All inputs masked by default (`maskAllInputs: true`)
- [ ] `password` input type always masked, never configurable off
- [ ] `pulse-mask` CSS class masks text content
- [ ] `pulse-block` CSS class excludes element from recording entirely
- [ ] `blockSelector` config blocks arbitrary CSS selectors
- [ ] Query params stripped from recorded URLs
- [ ] Developer can opt specific inputs in/out via `maskInputOptions`
- [ ] `buildPrivacyConfig()` correctly merges with defaults
- [ ] All unit tests passing
