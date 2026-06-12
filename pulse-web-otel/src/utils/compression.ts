// M1: Feature-detects CompressionStream('gzip') and compresses OTLP payloads.
// Falls back to uncompressed if CompressionStream is unavailable (older browsers).

declare const CompressionStream: {
  new (format: string): {
    writable: WritableStream;
    readable: ReadableStream;
  };
} | undefined;

export function isCompressionSupported(): boolean {
  return typeof CompressionStream !== 'undefined';
}

export async function gzipString(input: string): Promise<ArrayBuffer> {
  if (!isCompressionSupported() || typeof CompressionStream === 'undefined') {
    // Fall back to uncompressed UTF-8 bytes
    const encoder = new TextEncoder();
    const bytes = encoder.encode(input);
    return bytes.buffer as ArrayBuffer;
  }

  const encoder = new TextEncoder();
  const bytes = encoder.encode(input);

  const cs = new CompressionStream('gzip');
  const writer = cs.writable.getWriter();
  writer.write(bytes);
  writer.close();

  const reader = cs.readable.getReader();
  const chunks: Uint8Array[] = [];

  while (true) {
    const { done, value } = await reader.read() as { done: boolean; value: Uint8Array | undefined };
    if (done) break;
    if (value) chunks.push(value);
  }

  const totalLength = chunks.reduce((acc, chunk) => acc + chunk.length, 0);
  const result = new Uint8Array(totalLength);
  let offset = 0;
  for (const chunk of chunks) {
    result.set(chunk, offset);
    offset += chunk.length;
  }

  return result.buffer as ArrayBuffer;
}
