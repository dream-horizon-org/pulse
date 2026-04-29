/**
 * Browser gzip for OTLP bodies via CompressionStream (Chrome 80+, Firefox 113+, Safari 16.4+).
 */

export function isGzipSupported(): boolean {
  return typeof CompressionStream !== "undefined";
}

export async function gzipUint8Array(input: Uint8Array): Promise<Uint8Array> {
  const stream = new CompressionStream("gzip");
  const writer = stream.writable.getWriter();
  void writer.write(input as BufferSource);
  void writer.close();
  const buf = await new Response(stream.readable).arrayBuffer();
  return new Uint8Array(buf);
}
