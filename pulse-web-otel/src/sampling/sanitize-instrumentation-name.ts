// Mirrors Android PulseOtelUtils.sanitizeInstrumentationName for SdkMeter instrument names.

/**
 * OTel instrument names: letters, digits, `_`, `.`, `-`, `/`; must start with a letter
 * (prefix `m` if not); max 255 chars.
 */
export function sanitizeInstrumentationName(
  name: string,
  fallbackChar = "_",
): string {
  let sanitized = "";
  for (const char of name) {
    if (
      (char >= "a" && char <= "z") ||
      (char >= "A" && char <= "Z") ||
      (char >= "0" && char <= "9") ||
      char === "_" ||
      char === "." ||
      char === "-" ||
      char === "/"
    ) {
      sanitized += char;
    } else {
      sanitized += fallbackChar;
    }
  }
  const first = sanitized.charAt(0);
  const startsWithLetter =
    first !== "" &&
    ((first >= "a" && first <= "z") || (first >= "A" && first <= "Z"));
  const withLetterStart = startsWithLetter ? sanitized : `m${sanitized}`;
  return withLetterStart.length <= 255
    ? withLetterStart
    : withLetterStart.slice(0, 255);
}
