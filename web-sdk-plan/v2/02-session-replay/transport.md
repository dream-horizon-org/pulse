# 04.3 — Session Replay Transport

**Goal:** Compress rrweb event batches with gzip (via pako), encode as base64, wrap in an OTLP log record, and deliver to the Pulse backend — with `sendBeacon` for page-unload reliability and chunked delivery for large payloads.

**File:** `src/replay/transport.ts`
**Android equivalent:** None — web-exclusive; conceptually similar to the Android disk buffer + background flush mechanism

---

## Delivery Pipeline

```
rrweb events (JSON array)
        │
        ▼
  pako.gzip()          → binary compressed payload
        │
        ▼
  base64 encode        → string safe for OTLP attribute
        │
        ▼
  OTLP Log Record      → wrapped in standard Pulse log format
        │
        ├── normal:    fetch() POST to /v1/logs
        └── unload:    navigator.sendBeacon() to /v1/logs
```

---

## OTLP Log Record Format

Session replay is delivered as a standard OTLP log record with `pulse.type: session_replay`:

```typescript
interface ReplayLogRecord {
  'pulse.type':           'session_replay';
  'replay.session_id':    string;     // session.id from global attributes
  'replay.chunk_index':   number;     // 0-based chunk sequence number
  'replay.is_last_chunk': boolean;    // true on final chunk of a flush
  'replay.event_count':   number;     // number of rrweb events in this chunk
  'replay.start_time':    number;     // timestamp of first event in chunk (Unix ms)
  'replay.end_time':      number;     // timestamp of last event in chunk (Unix ms)
  'replay.payload':       string;     // base64(gzip(JSON.stringify(events)))
  'replay.payload_size':  number;     // compressed size in bytes
}
```

### Why This Format?

- **Standard OTLP:** No backend changes needed — uses existing `/v1/logs` endpoint
- **Compressed:** gzip + base64 keeps payload size manageable (typical 10:1 compression for DOM JSON)
- **Chunked:** Splits large batches to respect `sendBeacon` 64KB limit
- **Session-linked:** `replay.session_id` links replay to all other telemetry from the same session

---

## Implementation

```typescript
// src/replay/transport.ts
import { gzip } from 'pako';
import type { eventWithTime } from '@rrweb/types';

const CHUNK_SIZE_BYTES = 50 * 1024;  // 50KB compressed per chunk (headroom under sendBeacon limit)

export class ReplayTransport {
  private chunkIndex = 0;

  constructor(
    private readonly logger: Logger,     // OTEL Logger instance
    private readonly getSessionId: () => string,
  ) {}

  async send(events: eventWithTime[], options: { isUnload?: boolean } = {}): Promise<void> {
    if (events.length === 0) return;

    const chunks = this.chunkEvents(events);

    for (let i = 0; i < chunks.length; i++) {
      const chunk = chunks[i];
      const isLastChunk = i === chunks.length - 1;
      await this.sendChunk(chunk, isLastChunk, options.isUnload ?? false);
    }
  }

  // ─── Private ──────────────────────────────────────────────────────────────

  private chunkEvents(events: eventWithTime[]): eventWithTime[][] {
    const chunks: eventWithTime[][] = [];
    let currentChunk: eventWithTime[] = [];
    let currentSize = 0;

    for (const event of events) {
      const eventJson = JSON.stringify(event);
      const compressed = gzip(eventJson);

      if (currentSize + compressed.length > CHUNK_SIZE_BYTES && currentChunk.length > 0) {
        chunks.push(currentChunk);
        currentChunk = [];
        currentSize = 0;
      }

      currentChunk.push(event);
      currentSize += compressed.length;
    }

    if (currentChunk.length > 0) chunks.push(currentChunk);
    return chunks;
  }

  private async sendChunk(
    events: eventWithTime[],
    isLastChunk: boolean,
    isUnload: boolean,
  ): Promise<void> {
    const json = JSON.stringify(events);
    const compressed = gzip(json);
    const payload = uint8ArrayToBase64(compressed);

    const startTime = events[0]?.timestamp ?? Date.now();
    const endTime = events[events.length - 1]?.timestamp ?? Date.now();

    const attributes = {
      'pulse.type':           'session_replay',
      'replay.session_id':    this.getSessionId(),
      'replay.chunk_index':   this.chunkIndex++,
      'replay.is_last_chunk': isLastChunk,
      'replay.event_count':   events.length,
      'replay.start_time':    startTime,
      'replay.end_time':      endTime,
      'replay.payload':       payload,
      'replay.payload_size':  compressed.length,
    };

    if (isUnload) {
      // Page is unloading — use sendBeacon for guaranteed delivery
      this.sendViaBeacon(attributes);
    } else {
      // Normal flush — use fetch for reliability and error handling
      await this.sendViaFetch(attributes);
    }
  }

  private sendViaFetch(attributes: Record<string, unknown>): Promise<void> {
    // Delegates to the OTEL logger which sends via OTLP HTTP
    return new Promise((resolve) => {
      this.logger.emit({
        body: 'session_replay',
        attributes,
        severityNumber: SeverityNumber.INFO,
      });
      resolve();
    });
  }

  private sendViaBeacon(attributes: Record<string, unknown>): void {
    // sendBeacon requires a direct POST — bypass the OTEL SDK for this call
    const otlpPayload = buildOtlpLogsPayload([attributes]);
    const blob = new Blob([JSON.stringify(otlpPayload)], {
      type: 'application/json',
    });
    navigator.sendBeacon(this.getLogsEndpoint(), blob);
  }

  private getLogsEndpoint(): string {
    return globalConfig.otlpEndpoint + '/v1/logs';
  }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

function uint8ArrayToBase64(bytes: Uint8Array): string {
  let binary = '';
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}
```

---

## Compression Rationale

rrweb events are verbose JSON — a typical 30-second interaction produces:
- ~500KB uncompressed JSON
- ~50KB after gzip (10:1 ratio typical for repetitive DOM JSON)
- ~67KB after base64 encoding (+33% overhead)

At 50KB per chunk, a 30-second session typically delivers in 1–2 chunks.

| Content | Typical Size |
|---|---|
| Full DOM snapshot (modern SPA) | 80–200KB uncompressed → 8–20KB gzipped |
| 30s of mouse movements (50ms sampling) | ~150KB uncompressed → ~15KB gzipped |
| 60s of form interaction | ~100KB uncompressed → ~10KB gzipped |

---

## `sendBeacon` Constraints

| Constraint | Value | Handling |
|---|---|---|
| Max payload size | ~64KB | Chunks limited to 50KB |
| Method | Always POST | Built in |
| Content-Type | Must be `application/json`, `text/plain`, or `application/x-www-form-urlencoded` | Use `Blob` with `application/json` |
| Async | Fire-and-forget | No response handling |
| Timing | Must be called in `pagehide` handler synchronously | `sendViaBeacon` is synchronous |

---

## Edge Cases

| Case | Handling |
|---|---|
| `pako` unavailable / not loaded | Guard import; if missing, fall back to uncompressed (accept larger payload) |
| `sendBeacon` returns `false` | Browser rejected the request (too large or queue full); log warning, data lost |
| Empty events array | Early return — no chunk sent |
| Session ID changes mid-session | Use `getSessionId()` at send time — captures current session ID |
| Network error on fetch | OTLP SDK handles retry with backoff (configured in 01-foundation) |
| Very large single event (e.g. canvas frame) | May exceed chunk size alone — send as its own chunk even if over limit |
| First chunk index after page restore | `chunkIndex` resets to 0 on BFCache restore — `replay.session_id` distinguishes sessions |

---

## Testing

### Unit Tests (Vitest)

```typescript
it('compresses and base64-encodes events', async () => {
  const transport = new ReplayTransport(mockLogger, () => 'sess_abc');
  const events = [{ type: 3, timestamp: Date.now(), data: { x: 100 } }];

  await transport.send(events);

  const call = mockLogger.emit.mock.calls[0][0];
  expect(call.attributes['pulse.type']).toBe('session_replay');
  expect(typeof call.attributes['replay.payload']).toBe('string');
  expect(call.attributes['replay.payload_size']).toBeGreaterThan(0);
});

it('splits large batches into multiple chunks', async () => {
  const transport = new ReplayTransport(mockLogger, () => 'sess_abc');

  // Create events that will exceed 50KB per chunk
  const events = Array.from({ length: 100 }, (_, i) => ({
    type: 3,
    timestamp: Date.now() + i,
    data: { content: 'x'.repeat(5000) },
  }));

  await transport.send(events);

  expect(mockLogger.emit.mock.calls.length).toBeGreaterThan(1);
});

it('marks last chunk with is_last_chunk: true', async () => {
  const transport = new ReplayTransport(mockLogger, () => 'sess_abc');
  // Force 2 chunks
  const events = generateEvents(100);
  await transport.send(events);

  const calls = mockLogger.emit.mock.calls;
  const lastCall = calls[calls.length - 1][0];
  expect(lastCall.attributes['replay.is_last_chunk']).toBe(true);

  // First chunk should not be last
  if (calls.length > 1) {
    expect(calls[0][0].attributes['replay.is_last_chunk']).toBe(false);
  }
});

it('increments chunk_index across calls', async () => {
  const transport = new ReplayTransport(mockLogger, () => 'sess_abc');
  await transport.send([makeEvent(0)]);
  await transport.send([makeEvent(1)]);

  const firstIndex = mockLogger.emit.mock.calls[0][0].attributes['replay.chunk_index'];
  const secondIndex = mockLogger.emit.mock.calls[1][0].attributes['replay.chunk_index'];
  expect(secondIndex).toBe(firstIndex + 1);
});
```

---

## Done Criteria

- [ ] Events compressed with `pako.gzip` and base64-encoded before sending
- [ ] Large batches split into ≤50KB chunks
- [ ] `replay.chunk_index` increments monotonically across all flushes in a session
- [ ] `replay.is_last_chunk: true` on final chunk of each flush
- [ ] `isUnload: true` triggers `sendBeacon` instead of `fetch`
- [ ] `sendBeacon` payload is a `Blob` with `application/json` content type
- [ ] `replay.session_id` populated from global session ID
- [ ] `replay.event_count`, `replay.start_time`, `replay.end_time` accurate
- [ ] All unit tests passing
