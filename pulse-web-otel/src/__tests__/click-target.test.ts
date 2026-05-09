import { describe, expect, it } from "vitest";

import {
  buildClickContextLabel,
  isInteractiveTarget,
  resolveInteractiveElement,
  widgetIdFromElement,
  widgetNameFromElement,
} from "../instrumentations/click-target";

describe("click-target", () => {
  it("resolveInteractiveElement finds button in path", () => {
    document.body.innerHTML = '<button type="button" id="go">Go</button>';
    const btn = document.getElementById("go")!;
    const path = [btn, document.body, document.documentElement];
    expect(resolveInteractiveElement(path)).toBe(btn);
  });

  it("resolveInteractiveElement returns null when only structural nodes", () => {
    const path = [document.body, document.documentElement];
    expect(resolveInteractiveElement(path)).toBeNull();
  });

  it("isInteractiveTarget is true for anchor with href", () => {
    document.body.innerHTML = '<a href="/x">x</a>';
    const a = document.querySelector("a")!;
    expect(isInteractiveTarget(a)).toBe(true);
  });

  it("isInteractiveTarget is false for anchor without href", () => {
    document.body.innerHTML = '<a name="n">x</a>';
    const a = document.querySelector("a")!;
    expect(isInteractiveTarget(a)).toBe(false);
  });

  it("widgetNameFromElement uppercases HTML tag", () => {
    document.body.innerHTML = "<button></button>";
    const b = document.querySelector("button")!;
    expect(widgetNameFromElement(b)).toBe("BUTTON");
  });

  it("widgetIdFromElement prefers id over data-testid", () => {
    document.body.innerHTML = '<button id="a" data-testid="b">x</button>';
    const b = document.querySelector("button")!;
    expect(widgetIdFromElement(b)).toBe("a");
  });

  it("widgetIdFromElement uses data-testid when id absent", () => {
    document.body.innerHTML = '<button data-testid="cta">x</button>';
    const b = document.querySelector("button")!;
    expect(widgetIdFromElement(b)).toBe("cta");
  });

  it("buildClickContextLabel uses aria-label when captureContext true", () => {
    document.body.innerHTML = '<button aria-label="Add to cart"></button>';
    const b = document.querySelector("button")!;
    expect(buildClickContextLabel(b, true)).toBe("label=Add to cart");
  });

  it("buildClickContextLabel returns undefined when captureContext false", () => {
    document.body.innerHTML = "<button>Hi</button>";
    const b = document.querySelector("button")!;
    expect(buildClickContextLabel(b, false)).toBeUndefined();
  });

  it("buildClickContextLabel skips password input", () => {
    document.body.innerHTML = '<input type="password" value="x" />';
    const el = document.querySelector("input")!;
    expect(buildClickContextLabel(el, true)).toBeUndefined();
  });
});
