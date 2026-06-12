/**
 * Extracts the filename / URL from the first parseable frame of a JS stack string.
 *
 * Supported formats (in match-priority order):
 *   https?://host/path.js          — browser production bundle (most common)
 *   /absolute/posix/path.js        — Node.js / server-side
 *   file:///absolute/path.js       — file:// protocol
 *   C:\windows\path.js             — Windows absolute path
 *
 * Returns "unknown" when no recognisable frame is found (e.g. cross-origin
 * script errors where the browser withholds stack info).
 */
export function errorFilenameFromStack(stack: string): string {
  const m = stack.match(
    /(?:\(|at\s+)(?:[^\s()]+\s+)?(https?:\/\/[^\s)]+|\/[^\s)]+|file:\/[^\s)]+|[a-zA-Z]:[\\/][^\s)]+)/,
  );
  if (!m?.[1]) return "unknown";
  // Strip trailing :line:col or :line suffix (e.g. "main.js:10:5" → "main.js")
  return m[1].replace(/:(\d+)(:\d+)?$/, "");
}
