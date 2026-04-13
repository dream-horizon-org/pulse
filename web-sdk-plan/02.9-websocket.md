# 02.9 — WebSocket Instrumentation

**Goal:** Track WebSocket connection lifecycle (open, close, error, message counts) as spans — web-exclusive signal capturing real-time data transport that native HTTP instrumentation misses.

**File:** `src/instrumentations/websocket.ts`
**Android equivalent:** None (web-only; OkHttp3 instrumentation doesn't cover WebSocket frames)

---

## Signals Produced

### `pulse.type: websocket` — one span per WebSocket connection lifetime

| Attribute | Type | Source | Notes |
|---|---|---|---|
| `pulse.type` | string | `"websocket"` | |
| `websocket.url` | string | Sanitised `ws.url` | |
| `websocket.status` | string | `"open"` \| `"closed"` \| `"error"` | Final state of the connection |
| `websocket.close_code` | long | `CloseEvent.code` | [RFC 6455 close codes](https://www.iana.org/assignments/websocket/websocket.xhtml) |
| `websocket.close_reason` | string | `CloseEvent.reason` (max 128 chars) | Server-provided close reason |
| `websocket.messages_sent` | long | Count of `ws.send()` calls | |
| `websocket.messages_received` | long | Count of `message` events | |
| `websocket.bytes_sent` | long | Sum of payload sizes from `send()` | Best-effort — `string.length` for text, `byteLength` for binary |
| `websocket.bytes_received` | long | Sum of `MessageEvent.data` sizes | Best-effort |
| `websocket.duration` | long | Time from open to close (ms) | |
| `url.path` | string | `window.location.pathname` at open time | Page the WS was created on |

---

## Implementation

```typescript
// src/instrumentations/websocket.ts

const _WebSocket = window.WebSocket;

export class WebSocketInstrumentation {
  install(): void {
    if (!('WebSocket' in window)) return;

    const instrumentation = this;

    window.WebSocket = class InstrumentedWebSocket extends _WebSocket {
      private _openTime = 0;
      private _msgSent = 0;
      private _msgRecv = 0;
      private _bytesSent = 0;
      private _bytesRecv = 0;
      private _urlPath = window.location.pathname;

      constructor(url: string | URL, protocols?: string | string[]) {
        super(url, protocols);

        this.addEventListener('open', () => {
          this._openTime = Date.now();
        });

        this.addEventListener('message', (e: MessageEvent) => {
          this._msgRecv++;
          this._bytesRecv += getMessageSize(e.data);
        });

        this.addEventListener('close', (e: CloseEvent) => {
          instrumentation.emitSpan(this, {
            status: 'closed',
            closeCode: e.code,
            closeReason: e.reason,
          });
        });

        this.addEventListener('error', () => {
          instrumentation.emitSpan(this, {
            status: 'error',
            closeCode: 0,
            closeReason: '',
          });
        });
      }

      send(data: string | ArrayBufferLike | Blob | ArrayBufferView): void {
        this._msgSent++;
        this._bytesSent += getSendSize(data);
        super.send(data as any);
      }

      // Expose private fields for emitSpan
      get _stats() {
        return {
          openTime: this._openTime,
          msgSent: this._msgSent,
          msgRecv: this._msgRecv,
          bytesSent: this._bytesSent,
          bytesRecv: this._bytesRecv,
          urlPath: this._urlPath,
        };
      }
    } as any;
  }

  uninstall(): void {
    window.WebSocket = _WebSocket;
  }

  private emitSpan(
    ws: any,
    opts: { status: string; closeCode: number; closeReason: string }
  ): void {
    const stats = ws._stats;
    const duration = stats.openTime > 0 ? Date.now() - stats.openTime : 0;

    emitLogRecord({
      'pulse.type':                  'websocket',
      'websocket.url':               sanitizeWsUrl(ws.url),
      'websocket.status':            opts.status,
      'websocket.close_code':        opts.closeCode,
      'websocket.close_reason':      opts.closeReason.slice(0, 128),
      'websocket.messages_sent':     stats.msgSent,
      'websocket.messages_received': stats.msgRecv,
      'websocket.bytes_sent':        stats.bytesSent,
      'websocket.bytes_received':    stats.bytesRecv,
      'websocket.duration':          Math.round(duration),
      'url.path':                    stats.urlPath,
    });
  }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

function getMessageSize(data: MessageEvent['data']): number {
  if (typeof data === 'string') return data.length;
  if (data instanceof ArrayBuffer) return data.byteLength;
  if (ArrayBuffer.isView(data)) return data.byteLength;
  return 0; // Blob — size not synchronously available
}

function getSendSize(data: Parameters<WebSocket['send']>[0]): number {
  if (typeof data === 'string') return data.length;
  if (data instanceof ArrayBuffer) return data.byteLength;
  if (ArrayBuffer.isView(data)) return data.byteLength;
  if (data instanceof Blob) return data.size;
  return 0;
}

function sanitizeWsUrl(url: string): string {
  try {
    const u = new URL(url);
    u.search = '';  // strip query params (may contain auth tokens)
    return u.toString();
  } catch {
    return url;
  }
}
```

---

## WebSocket Close Codes Reference

| Code | Meaning | When Seen |
|---|---|---|
| `1000` | Normal closure | Clean app-level close |
| `1001` | Going away | Page navigating away |
| `1006` | Abnormal closure | Network drop (no close frame received) |
| `1011` | Server error | Server-side exception |
| `1015` | TLS failure | Certificate error |

`close_code: 0` is used when the connection never opened (error before handshake).

---

## Edge Cases

| Case | Handling |
|---|---|
| `new WebSocket()` throws (invalid URL) | Error propagates normally before listener attachment; no span emitted |
| Connection never opens (server refuses) | `error` event fires, then `close` — span emitted with `status: 'error'` |
| `send()` called before `open` | Counted but queued by browser; bytes_sent still tracked |
| Blob messages received | `bytesRecv` won't count Blob size (async `blob.size`); counts as `0` — acceptable |
| Multiple protocol upgrades | One span per `WebSocket` instance |
| Worker-owned WebSockets | `window.WebSocket` patch doesn't affect Workers — out of scope |
| ws:// vs wss:// | URL stored as-is; sanitisation only strips query string |

---

## Testing

### Unit Tests (Vitest + JSDOM)

```typescript
it('emits websocket span on close', async () => {
  const records = captureLogRecords();
  const inst = new WebSocketInstrumentation();
  inst.install();

  const ws = new WebSocket('wss://realtime.example.com/events');
  // Simulate open
  ws.dispatchEvent(new Event('open'));
  // Simulate close
  ws.dispatchEvent(new CloseEvent('close', { code: 1000, reason: 'done' }));

  expect(records[0]['pulse.type']).toBe('websocket');
  expect(records[0]['websocket.status']).toBe('closed');
  expect(records[0]['websocket.close_code']).toBe(1000);
});

it('counts messages sent and received', () => {
  const records = captureLogRecords();
  const inst = new WebSocketInstrumentation();
  inst.install();

  const ws = new WebSocket('wss://realtime.example.com/events');
  ws.dispatchEvent(new Event('open'));
  ws.send('ping');
  ws.send('hello');
  ws.dispatchEvent(new MessageEvent('message', { data: 'pong' }));
  ws.dispatchEvent(new CloseEvent('close', { code: 1000 }));

  expect(records[0]['websocket.messages_sent']).toBe(2);
  expect(records[0]['websocket.messages_received']).toBe(1);
  expect(records[0]['websocket.bytes_sent']).toBe(9); // 'ping'.length + 'hello'.length
});

it('emits with status error on connection failure', () => {
  const records = captureLogRecords();
  const inst = new WebSocketInstrumentation();
  inst.install();

  const ws = new WebSocket('wss://realtime.example.com/events');
  ws.dispatchEvent(new Event('error'));
  ws.dispatchEvent(new CloseEvent('close', { code: 1006 }));

  expect(records[0]['websocket.status']).toBe('error');
});

it('strips query params from WebSocket URL', () => {
  const records = captureLogRecords();
  const inst = new WebSocketInstrumentation();
  inst.install();

  const ws = new WebSocket('wss://realtime.example.com/events?token=secret');
  ws.dispatchEvent(new Event('open'));
  ws.dispatchEvent(new CloseEvent('close', { code: 1000 }));

  expect(records[0]['websocket.url']).toBe('wss://realtime.example.com/events');
});
```

---

## Done Criteria

- [ ] WebSocket connection lifecycle emits `websocket` log record on close
- [ ] `websocket.status` correctly reports `"closed"` vs `"error"`
- [ ] `websocket.close_code` and `websocket.close_reason` populated from `CloseEvent`
- [ ] `websocket.messages_sent` and `websocket.messages_received` accurate
- [ ] `websocket.bytes_sent` and `websocket.bytes_received` populated for string/ArrayBuffer data
- [ ] Query params stripped from `websocket.url`
- [ ] `uninstall()` restores original `window.WebSocket`
- [ ] All unit tests passing
