/**
 * Unit tests for errorFilenameFromStack()
 *
 * Covers the four supported frame formats:
 *   - Browser https:// URL  (production bundles)
 *   - Browser http://  URL  (local dev server)
 *   - POSIX absolute path   (Node.js / SSR)
 *   - Windows absolute path
 *   - file:// protocol
 *   - Edge cases: empty, undefined-ish, no recognisable frame
 */

import { describe, it, expect } from "vitest";
import { errorFilenameFromStack } from "../utils/error-stack";

describe("errorFilenameFromStack", () => {
  // ─── Browser https:// frames ──────────────────────────────────────────────

  it("extracts https:// URL from frame with function name", () => {
    const stack = [
      "Error: render crash",
      "    at ComponentA (https://cdn.app.com/main.abc123.js:1:45231)",
      "    at renderWithHooks (https://cdn.app.com/main.abc123.js:1:12034)",
    ].join("\n");
    expect(errorFilenameFromStack(stack)).toBe(
      "https://cdn.app.com/main.abc123.js",
    );
  });

  it("extracts https:// URL from anonymous frame (no function name)", () => {
    const stack = [
      "Error: crash",
      "    at https://cdn.app.com/chunk.8a3f2b.js:2:100",
    ].join("\n");
    expect(errorFilenameFromStack(stack)).toBe(
      "https://cdn.app.com/chunk.8a3f2b.js",
    );
  });

  it("extracts https:// URL with deep path and query-free bundle name", () => {
    const stack = [
      "TypeError: cannot read property",
      "    at Object.<anonymous> (https://app.example.com/static/js/vendors~main.chunk.js:1:9999)",
    ].join("\n");
    expect(errorFilenameFromStack(stack)).toBe(
      "https://app.example.com/static/js/vendors~main.chunk.js",
    );
  });

  // ─── Local dev http:// frames ─────────────────────────────────────────────

  it("extracts http:// URL (local dev server)", () => {
    const stack = [
      "Error: dev crash",
      "    at fn (http://localhost:3000/src/App.tsx:42:7)",
    ].join("\n");
    expect(errorFilenameFromStack(stack)).toBe(
      "http://localhost:3000/src/App.tsx",
    );
  });

  // ─── POSIX absolute path (Node.js / SSR) ─────────────────────────────────

  it("extracts POSIX absolute path", () => {
    const stack = [
      "Error: server error",
      "    at fn (/app/src/server/index.js:10:5)",
    ].join("\n");
    expect(errorFilenameFromStack(stack)).toBe("/app/src/server/index.js");
  });

  // ─── Windows absolute path ────────────────────────────────────────────────

  it("extracts Windows backslash path", () => {
    const stack = [
      "Error: win crash",
      "    at fn (C:\\app\\src\\index.js:10:5)",
    ].join("\n");
    expect(errorFilenameFromStack(stack)).toBe("C:\\app\\src\\index.js");
  });

  it("extracts Windows forward-slash path", () => {
    const stack = [
      "Error: win crash",
      "    at fn (C:/app/src/index.js:10:5)",
    ].join("\n");
    expect(errorFilenameFromStack(stack)).toBe("C:/app/src/index.js");
  });

  // ─── file:// protocol ─────────────────────────────────────────────────────

  it("extracts file:// path", () => {
    const stack = [
      "Error: file crash",
      "    at fn (file:///Users/dev/project/app.js:1:1)",
    ].join("\n");
    expect(errorFilenameFromStack(stack)).toBe("file:///Users/dev/project/app.js");
  });

  // ─── Edge cases ───────────────────────────────────────────────────────────

  it("returns 'unknown' for empty string", () => {
    expect(errorFilenameFromStack("")).toBe("unknown");
  });

  it("returns 'unknown' for a message-only stack (no frames)", () => {
    expect(errorFilenameFromStack("Error: something went wrong")).toBe(
      "unknown",
    );
  });

  it("returns 'unknown' for cross-origin stub (no stack info)", () => {
    // This is what the browser provides for cross-origin script errors
    expect(errorFilenameFromStack("Script error.")).toBe("unknown");
  });

  it("picks the first matching frame from a multi-frame stack", () => {
    const stack = [
      "Error: crash",
      "    at innerFn (https://cdn.app.com/main.js:1:100)",
      "    at outerFn (https://cdn.app.com/vendor.js:2:200)",
    ].join("\n");
    // Should return the FIRST frame's filename
    expect(errorFilenameFromStack(stack)).toBe("https://cdn.app.com/main.js");
  });
});
