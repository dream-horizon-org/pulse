/**
 * History API SPA router — Pulse.setScreenName after each navigation.
 */

import { Pulse } from "@dreamhorizonorg/pulse-web";

function normalizePath(path) {
  if (!path || path === "") return "/";
  let p = path.startsWith("/") ? path : `/${path}`;
  if (p.length > 1 && p.endsWith("/")) p = p.slice(0, -1);
  return p;
}

/**
 * @param {{
 *   routes: Record<string, (el: HTMLElement) => void>;
 *   outlet: HTMLElement;
 *   navRoot: HTMLElement;
 *   routeLabels: Record<string, string>;
 * }} opts
 */
export function createRouter(opts) {
  const { routes, outlet, navRoot, routeLabels } = opts;
  const knownSet = new Set(Object.keys(routeLabels));

  function pulseSetScreen(name) {
    if (Pulse.isInitialized()) Pulse.setScreenName(name);
  }

  function syncNav(activePath) {
    navRoot.innerHTML = Object.entries(routeLabels)
      .map(([path, label]) => {
        const active = path === activePath;
        return `<a class="nav-link${active ? " is-active" : ""}" href="${path}" data-spa-link="true" ${active ? 'aria-current="page"' : ""}>${label}</a>`;
      })
      .join("");
  }

  /** @param {string} pathname */
  function renderContent(pathname) {
    const n = normalizePath(pathname);
    const isKnown = knownSet.has(n);
    const render = routes[isKnown ? n : "/404"];
    outlet.replaceChildren();
    if (render) render(outlet);
    pulseSetScreen(n);
    syncNav(isKnown ? n : "");
  }

  document.addEventListener("click", (e) => {
    const a = e.target.closest("a[data-spa-link]");
    if (!a) return;
    if (e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
    const href = a.getAttribute("href");
    if (!href?.startsWith("/")) return;
    e.preventDefault();
    const next = normalizePath(href);
    try {
      history.pushState({}, "", next);
    } catch {
      /* ignore */
    }
    renderContent(next);
  });

  window.addEventListener("popstate", () => {
    renderContent(window.location.pathname);
  });

  return {
    /** Sync outlet + Pulse screen from current URL (no history mutation). */
    start() {
      renderContent(window.location.pathname);
    },
  };
}
