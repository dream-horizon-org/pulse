/**
 * Formats a numeric percentage for display.
 *
 * - Rounds to at most 1 decimal place (e.g., `85.71428...` -> `"85.7%"`).
 * - Drops a trailing `.0` for whole numbers (e.g., `100` -> `"100%"`, `0` -> `"0%"`).
 * - Returns `"0%"` for non-finite inputs (NaN / Infinity / null-ish coerced).
 *
 * @param value - The percentage value (0-100 expected, not a 0-1 ratio).
 * @returns The formatted percentage string with a trailing `%`.
 *
 * @example
 * formatPercent(85.71428571428571) // "85.7%"
 * formatPercent(14.285714285714286) // "14.3%"
 * formatPercent(0) // "0%"
 * formatPercent(100) // "100%"
 */
export function formatPercent(value: number): string {
  if (!Number.isFinite(value)) {
    return "0%";
  }
  const rounded = Math.round(value * 10) / 10;
  const display = Number.isInteger(rounded)
    ? rounded.toString()
    : rounded.toFixed(1);
  return `${display}%`;
}
