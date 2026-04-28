// Replaced at build time by tsup's `define` option with the npm package version.
// Do NOT import process.env here — this runs in the browser.
declare const __SDK_VERSION__: string;
export const SDK_VERSION: string =
  typeof __SDK_VERSION__ !== 'undefined' ? __SDK_VERSION__ : '0.0.0-dev';
