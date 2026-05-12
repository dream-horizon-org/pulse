/**
 * DOM hit-testing for click instrumentation — interactive element detection and label extraction.
 * Mirrors iOS/Android semantics: first qualifying element along `composedPath()` wins.
 */

const INTERACTIVE_ROLES = new Set([
  "button",
  "link",
  "menuitem",
  "menuitemcheckbox",
  "menuitemradio",
  "option",
  "radio",
  "switch",
  "tab",
  "checkbox",
  "searchbox",
  "spinbutton",
  "slider",
  "textbox",
  "combobox",
  "listbox",
]);

const LABEL_MAX_LEN = 200;

/** Build `composedPath()` fallback when unavailable (older browsers). */
export function eventComposedPath(ev: Event): EventTarget[] {
  if (typeof ev.composedPath === "function") {
    const p = ev.composedPath();
    if (p.length > 0) return p.slice();
  }
  const path: EventTarget[] = [];
  let n: Node | null | undefined = ev.target instanceof Node ? ev.target : null;
  while (n) {
    path.push(n);
    n = n.parentNode;
  }
  return path;
}

/**
 * First interactive element in hit path (deepest-first), or `null` for dead clicks.
 * Skips `html` / `body` as sole structural nodes; real targets are still found when nested.
 */
export function resolveInteractiveElement(path: EventTarget[]): Element | null {
  for (const t of path) {
    if (!(t instanceof Element)) continue;
    if (t === document.documentElement || t === document.body) continue;
    if (isInteractiveTarget(t)) return t;
  }
  return null;
}

export function isInteractiveTarget(el: Element): boolean {
  if (el instanceof HTMLElement) {
    if (el.hidden) return false;
    if (el.getAttribute("aria-disabled") === "true") return false;

    if (el instanceof HTMLButtonElement) return !el.disabled;
    if (el instanceof HTMLAnchorElement) return el.hasAttribute("href");
    if (el instanceof HTMLInputElement) {
      const type = el.type.toLowerCase();
      if (type === "hidden") return false;
      return !el.disabled;
    }
    if (el instanceof HTMLTextAreaElement || el instanceof HTMLSelectElement) {
      return !el.disabled;
    }
    if (el instanceof HTMLOptionElement) return !el.disabled;
    if (el instanceof HTMLLabelElement) return true;

    if (el.tagName.toUpperCase() === "SUMMARY") return true;

    const role = el.getAttribute("role");
    if (role && INTERACTIVE_ROLES.has(role.toLowerCase())) return true;
  } else if (el instanceof SVGElement) {
    const role = el.getAttribute("role");
    if (role && INTERACTIVE_ROLES.has(role.toLowerCase())) return true;
    if (el.tagName.toLowerCase() === "a" && el.hasAttribute("href"))
      return true;
  }

  return false;
}

export function widgetNameFromElement(el: Element): string {
  if (el instanceof HTMLElement) return el.tagName.toUpperCase();
  if (el instanceof SVGElement) {
    const local = el.tagName.toLowerCase();
    return `svg:${local}`;
  }
  return "UNKNOWN";
}

export function widgetIdFromElement(el: Element): string | undefined {
  if (el.id) return el.id;
  const testId = el.getAttribute("data-testid");
  if (testId) return testId;
  return undefined;
}

/**
 * `app.click.context` as `label=...` (Android `PulseAttributes.AppClickContext` shape).
 * Returns `undefined` when nothing safe to send.
 */
export function buildClickContextLabel(
  el: Element,
  captureContext: boolean,
): string | undefined {
  if (!captureContext) return undefined;
  if (el instanceof HTMLInputElement && el.type.toLowerCase() === "password") {
    return undefined;
  }

  const fromAttr =
    (el instanceof HTMLElement && el.getAttribute("aria-label")) ||
    (el instanceof HTMLElement && el.getAttribute("title")) ||
    null;
  if (fromAttr) {
    const t = fromAttr.trim();
    if (t) return `label=${t.slice(0, LABEL_MAX_LEN)}`;
  }

  if (el instanceof HTMLElement) {
    const text = el.innerText?.trim() ?? "";
    if (text) return `label=${text.slice(0, LABEL_MAX_LEN)}`;
  }

  return undefined;
}
