# 05.3 — Vue Integration

**Goal:** Provide a `PulseVuePlugin` for Vue 3 that initializes the SDK, registers global error handling, and integrates with Vue Router for automatic route change tracking.

**File:** `src/integrations/vue/index.ts`
**Package:** `@pulse-sdk/vue`

---

## Vue Plugin API

```typescript
import { createApp } from 'vue';
import { createRouter, createWebHistory } from 'vue-router';
import { PulseVuePlugin } from '@pulse-sdk/vue';

const router = createRouter({
  history: createWebHistory(),
  routes: [...],
});

const app = createApp(App);

app.use(router);
app.use(PulseVuePlugin, {
  projectId: 'proj_abc123',
  otlpEndpoint: 'https://ingest.pulse.io',
  replay: { enabled: true },
  router,  // Optional: pass router for route tracking
});

app.mount('#app');
```

---

## Implementation

```typescript
// src/integrations/vue/index.ts
import type { App, Plugin } from 'vue';
import type { Router } from 'vue-router';
import { PulseSDK } from '../../sdk';

export interface PulseVueOptions {
  projectId: string;
  otlpEndpoint?: string;
  replay?: { enabled?: boolean };
  router?: Router;
}

export const PulseVuePlugin: Plugin<PulseVueOptions> = {
  install(app: App, options: PulseVueOptions): void {
    if (typeof window === 'undefined') return; // SSR guard

    // Initialize SDK
    const sdk = new PulseSDK({
      projectId: options.projectId,
      otlpEndpoint: options.otlpEndpoint,
      replay: options.replay,
    });
    sdk.init();

    // Make SDK available globally via inject/provide
    app.provide('pulse', sdk);

    // Global Vue error handler
    app.config.errorHandler = (err, instance, info) => {
      sdk.reportException(err instanceof Error ? err : new Error(String(err)), {
        isFatal: false,
        attributes: {
          'vue.component_name': instance?.$options.name ?? instance?.$options.__name ?? '',
          'vue.lifecycle_hook': info ?? '',
        },
      });
      // Re-throw so Vue's default console error still fires
      console.error('[Vue Error]', err);
    };

    // Global Vue warning handler (development mode)
    if (process.env.NODE_ENV !== 'production') {
      app.config.warnHandler = (msg, instance, trace) => {
        // Warnings are non-fatal; only log — don't send to Pulse
        console.warn('[Vue Warning]', msg, trace);
      };
    }

    // Vue Router integration
    if (options.router) {
      setupRouterTracking(options.router, sdk);
    }
  },
};
```

### Router Tracking

```typescript
function setupRouterTracking(router: Router, sdk: PulseSDK): void {
  router.afterEach((to, from) => {
    if (from.path === to.path) return; // Same route — skip (query/hash change only)

    sdk.navigationInstrumentation?.onRouteChange(to.path);
  });
}
```

### `usePulse()` Composable

```typescript
// src/integrations/vue/composables.ts
import { inject } from 'vue';
import type { PulseSDK } from '../../sdk';

export function usePulse(): PulseSDK {
  const sdk = inject<PulseSDK>('pulse');
  if (!sdk) {
    throw new Error('[Pulse] usePulse() must be called inside a component that has PulseVuePlugin installed.');
  }
  return sdk;
}
```

Usage in a Vue component:

```vue
<script setup>
import { usePulse } from '@pulse-sdk/vue';

const pulse = usePulse();

function onCheckout() {
  pulse.trackEvent('checkout_started', { cart_value: 49.99 });
}
</script>
```

---

## Vue Error Attributes

| Attribute | Source | Notes |
|---|---|---|
| `vue.component_name` | `instance.$options.name` or `__name` | `__name` is the filename-based name in `<script setup>` |
| `vue.lifecycle_hook` | `info` param from `errorHandler` | e.g. `"created hook"`, `"v-on handler"`, `"render function"` |

---

## Nuxt.js Support

For Nuxt 3, the plugin is loaded via a Nuxt plugin file:

```typescript
// plugins/pulse.client.ts  (note: .client.ts — client-side only)
import { PulseVuePlugin } from '@pulse-sdk/vue';

export default defineNuxtPlugin((nuxtApp) => {
  const router = useRouter();
  nuxtApp.vueApp.use(PulseVuePlugin, {
    projectId: useRuntimeConfig().public.pulseProjectId,
    router,
  });
});
```

The `.client.ts` suffix ensures the plugin only runs in the browser (Nuxt's SSR guard mechanism).

---

## Package Exports

```typescript
// src/integrations/vue/index.ts
export { PulseVuePlugin } from './plugin';
export { usePulse } from './composables';
export type { PulseVueOptions } from './plugin';
```

---

## Edge Cases

| Case | Handling |
|---|---|
| `app.config.errorHandler` already set by the user | Compose: call existing handler before Pulse's handler |
| Router not provided | Only global error handling active; navigation tracking skipped |
| Vue Router navigation guard throws | Caught by `errorHandler`; reported as non-fatal |
| Same-route navigation (`/about?tab=2` → `/about?tab=3`) | `from.path === to.path` — skipped to avoid session noise |
| Nuxt SSR | `.client.ts` plugin suffix ensures server-side skip |
| `inject()` called outside component scope | Error thrown with clear message |
| Vue 2 | Not supported — Vue 2 uses different plugin API (`Vue.use()`) |

---

## Testing

### Unit Tests (Vitest + Vue Test Utils)

```typescript
import { mount } from '@vue/test-utils';

it('provides SDK to child components', () => {
  const TestComp = defineComponent({
    setup() {
      const sdk = usePulse();
      return { hasSdk: !!sdk };
    },
    template: '<div>{{ hasSdk }}</div>',
  });

  const wrapper = mount(TestComp, {
    global: {
      plugins: [[PulseVuePlugin, { projectId: 'proj_test' }]],
    },
  });

  expect(wrapper.text()).toBe('true');
});

it('calls reportException on Vue component error', () => {
  const reportSpy = vi.fn();
  const ThrowingComp = defineComponent({
    setup() { throw new Error('render error'); },
    template: '<div />',
  });

  const app = createApp({ template: '<ThrowingComp />', components: { ThrowingComp } });
  app.use(PulseVuePlugin, { projectId: 'proj_test' });
  // Replace SDK instance's reportException
  // ... mount and expect reportSpy called
});

it('tracks route change on afterEach', () => {
  const onRouteChange = vi.fn();
  const router = createRouter({ history: createWebHistory(), routes: mockRoutes });

  const app = createApp(App);
  app.use(PulseVuePlugin, { projectId: 'proj_test', router });
  // Mock sdk.navigationInstrumentation
  // Navigate and assert onRouteChange called
});
```

### E2E (Playwright)

```typescript
test('Vue Router navigation creates screen_session span', async ({ page }) => {
  await page.goto('/vue-app');
  await page.click('[data-testid="nav-about"]');

  const span = await waitForSpan(receiver, 'screen_session');
  expect(span['screen.name']).toBe('/vue-app');
});

test('Vue component error reported as non_fatal', async ({ page }) => {
  await page.goto('/vue-app/error-page');
  const log = await waitForLog(receiver, 'non_fatal');
  expect(log['vue.lifecycle_hook']).toBeTruthy();
});
```

---

## Done Criteria

- [ ] `PulseVuePlugin` installs SDK and provides context via `provide/inject`
- [ ] `app.config.errorHandler` captures Vue render and lifecycle errors
- [ ] `vue.component_name` and `vue.lifecycle_hook` attributes populated
- [ ] `router.afterEach` tracks route changes when router is provided
- [ ] Same-route navigations (query/hash changes) not tracked as new sessions
- [ ] `usePulse()` composable returns SDK or throws clear error
- [ ] Nuxt 3 `.client.ts` plugin pattern documented and working
- [ ] SSR guard active — no code runs outside browser
- [ ] All unit tests passing
