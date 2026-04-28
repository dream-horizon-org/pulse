export function errorFilenameFromStack(stack: string): string {
  const m = stack.match(
    /(?:\(|at\s+)(?:[^\s()]+\s+)?(\/[^\s)]+|file:\/[^\s)]+|[a-zA-Z]:[\\/][^\s)]+)/,
  );
  return m?.[1] ?? "unknown";
}
