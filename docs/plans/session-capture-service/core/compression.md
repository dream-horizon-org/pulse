# compression

Parent: [session-capture-service](../index.md) ·
Brief: [component](../../../components/session-capture-service.md)

## 1. Purpose

Decompress gzip-encoded request bodies into a bounded-size UTF-8 string
ready for JSON parsing, without allocating unbounded memory.

## 2. Source

- `src/payload/decompression.rs`
- `src/payload/mod.rs` — `handle_recording_payload(body, limit, ...)`.
- `src/payload/recordings.rs` — JSON → event vector.

## 3. Detection + decode

- gzip magic: `[0x1f, 0x8b, 0x08]` (`GZIP_MAGIC_NUMBERS`).
- `flate2::read::GzDecoder` over the raw `Bytes`.
- Size guard: caller passes `limit` (the route uses
  `RECORDING_BODY_SIZE = 25 MiB`).
- Function: `decompress_payload(bytes: Bytes, limit: usize) -> Result<String, CaptureError>`.

## 4. Metrics

- `metrics::histogram!("capture_raw_payload_size")` — raw bytes received.
- Tracing span `instrument(skip_all, fields(payload_len = bytes.len()))`.

## 5. Failure modes

- Not gzip → currently returns the bytes as-is (callers must handle).
- Decompressed > `limit` → `CaptureError`.
- Invalid UTF-8 → `CaptureError`.

## 6. Streaming considerations

Current impl reads the body into memory then decompresses. For larger
recordings, consider chunked streaming via `body_chunk_read_timeout`
and `body_read_chunk_size_kb` (already wired in `router::State`).

## 7. Tests

`cargo test` — add table-driven cases: empty, undersized, exactly at
limit, over-limit, corrupt gzip, non-gzip JSON.

## 8. Cross-links

- [http-ingest](./websocket-server.md)
- [kafka-producer](./kafka-producer.md)
- Credit: PostHog capture pipeline (see file header).

## 9. Open items

- Add `Content-Encoding: br` (brotli) support if SDK adopts it.
- Emit decompression ratio metric for capacity planning.
