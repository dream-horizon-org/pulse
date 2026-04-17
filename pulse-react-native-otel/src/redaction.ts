export function redactUrl(url: string): string {
  try {
    const parsed = new URL(url);
    const redacted = `${parsed.protocol}//${parsed.host}${parsed.pathname}`;
    return parsed.search ? `${redacted}?***` : redacted;
  } catch {
    return '[invalid-url]';
  }
}

export function classifyError(error: unknown): string {
  if (error instanceof TypeError) {
    return 'type_error';
  }
  if (error instanceof Error) {
    const msg = error.message.toLowerCase();
    if (msg.includes('timeout')) return 'timeout';
    if (msg.includes('network')) return 'network_error';
    if (msg.includes('abort')) return 'aborted';
    return 'error';
  }
  return 'unknown';
}
