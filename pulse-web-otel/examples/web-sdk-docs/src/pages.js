import { Pulse } from "@dreamhorizon/pulse-web";

function el(html) {
  const t = document.createElement("template");
  t.innerHTML = html.trim();
  return t.content;
}

function safeInit(fn) {
  return () => {
    if (!Pulse.isInitialized()) {
      window.alert(
        "Pulse is not initialized (e.g. consent denied). No events will be sent.",
      );
      return;
    }
    fn();
  };
}

export const routeLabels = {
  "/": "Home",
  "/install": "Install",
  "/events": "Events",
  "/errors": "Errors",
  "/user": "User",
};

function renderHome(root) {
  root.appendChild(
    el(`
    <article class="doc">
      <h1>Vanilla Pulse Web SDK</h1>
      <p class="lead">Plain HTML, CSS, and JavaScript — no React. The SDK is the same <code>@dreamhorizon/pulse-web</code> package as the ecommerce demo.</p>
      <p>Use the nav to change routes; each navigation calls <code>Pulse.setScreenName(pathname)</code> so <code>screen.name</code> on OTLP logs matches the current page.</p>
      <p class="muted">See <code>pulse-web-otel/web-sdk-plan/</code> in the repo for full contracts. For a product-style SPA, use <code>examples/ecommerce-demo</code> (port 3002). This app runs on port 3003 by default.</p>
    </article>
  `),
  );
}

function renderInstall(root) {
  root.appendChild(
    el(`
    <article class="doc">
      <h1>Install &amp; start</h1>
      <pre class="code"><code>import { Pulse, PulseDataCollectionConsent } from "@dreamhorizon/pulse-web";

Pulse.init({
  apiKey: import.meta.env.VITE_PULSE_API_KEY,
  serviceName: "web-sdk-docs",
  dataCollectionState: PulseDataCollectionConsent.ALLOWED,
  export: { format: "json" },
});

Pulse.setScreenName("/");
Pulse.trackEvent("docs.page_view", { section: "install" });</code></pre>
      <h2>Environment (Vite)</h2>
      <p>Copy <code>.env.example</code> to <code>.env.local</code>. Key variables:</p>
      <ul>
        <li><code>VITE_PULSE_API_KEY</code> — project key</li>
        <li><code>VITE_PULSE_FORMAT</code> — <code>json</code> (readable) or <code>protobuf</code></li>
        <li><code>VITE_PULSE_MOCK_SDK_CONFIG=true</code> — load <code>public/pulse-sdk-config.mock.json</code> into <code>localStorage</code> (feature gates)</li>
        <li><code>?pulse_consent=denied</code> — consent gate (SDK stays off)</li>
      </ul>
    </article>
  `),
  );
}

function renderEvents(root) {
  root.appendChild(
    el(`
    <article class="doc">
      <h1>Custom events</h1>
      <p><code>Pulse.trackEvent</code> requires the <code>custom_events</code> feature in remote config. This demo uses mock JSON when <code>VITE_PULSE_MOCK_SDK_CONFIG=true</code>.</p>
      <div class="btn-row">
        <button type="button" id="btn-event-a" class="btn">trackEvent("docs.demo.cta")</button>
        <button type="button" id="btn-event-b" class="btn">trackEvent("docs.demo.secondary", attrs)</button>
      </div>
    </article>
  `),
  );

  root.querySelector("#btn-event-a").addEventListener(
    "click",
    safeInit(() => {
      Pulse.trackEvent("docs.demo.cta", { placement: "events_panel" });
    }),
  );
  root.querySelector("#btn-event-b").addEventListener(
    "click",
    safeInit(() => {
      Pulse.trackEvent("docs.demo.secondary", {
        source: "vanilla-demo",
        variant: "b",
      });
    }),
  );
}

function renderErrors(root) {
  root.appendChild(
    el(`
    <article class="doc">
      <h1>Errors (non-fatal)</h1>
      <p>These emit logs for dashboard contracts — they are safe test errors, not real crashes.</p>
      <div class="btn-row">
        <button type="button" id="btn-nf" class="btn">trackNonFatal("docs.demo.breadcrumb")</button>
        <button type="button" id="btn-ex" class="btn">reportException(new Error(...))</button>
      </div>
    </article>
  `),
  );

  root.querySelector("#btn-nf").addEventListener(
    "click",
    safeInit(() => {
      Pulse.trackNonFatal("docs.demo.breadcrumb", {
        step: "checkout_preview",
      });
    }),
  );
  root.querySelector("#btn-ex").addEventListener(
    "click",
    safeInit(() => {
      Pulse.reportException(new Error("docs.demo.handled_exception"), {
        handled: true,
      });
    }),
  );
}

function renderUser(root) {
  root.appendChild(
    el(`
    <article class="doc">
      <h1>User identity</h1>
      <p><code>setUserId</code> persists to localStorage (Android parity). Use demo IDs only.</p>
      <div class="btn-row">
        <button type="button" id="btn-user-set" class="btn">Set demo user</button>
        <button type="button" id="btn-user-clear" class="btn">Clear user</button>
      </div>
    </article>
  `),
  );

  root.querySelector("#btn-user-set").addEventListener(
    "click",
    safeInit(() => {
      Pulse.setUserId("docs-demo-user-1");
      Pulse.setUserProperty("plan", "pro");
    }),
  );
  root.querySelector("#btn-user-clear").addEventListener(
    "click",
    safeInit(() => {
      Pulse.setUserId(null);
    }),
  );
}

function renderNotFound(root) {
  root.appendChild(
    el(`
    <article class="doc">
      <h1>Not found</h1>
      <p>No route for this path. <code>screen.name</code> is still the URL you requested for debugging.</p>
      <p><a href="/" data-spa-link>Back home</a></p>
    </article>
  `),
  );
}

export const routes = {
  "/": renderHome,
  "/install": renderInstall,
  "/events": renderEvents,
  "/errors": renderErrors,
  "/user": renderUser,
  "/404": renderNotFound,
};
