# Cross-cutting: auth, environment, startup

All cases assume stdio MCP unless noted.

---

## TC-AUTH-001 — Successful cold start exchange

**Intent:** Credentials file populated; stderr shows exchange success.

**Steps**

1. Remove or rename `~/.pulse-mcp/credentials.json` (optional clean room).
2. Start MCP with valid `PULSE_BASE_URL`, `PULSE_API_KEY`.

**Expect**

- Stderr contains `Authentication successful.` and `Pulse MCP server running on stdio`.
- `~/.pulse-mcp/credentials.json` exists, mode **0600**, contains `accessToken` + `refreshToken`.

---

## TC-AUTH-002 — Missing `PULSE_BASE_URL`

**Intent:** Fail fast before tools register.

**Setup:** Unset `PULSE_BASE_URL`, set valid key.

**Expect:** Process exits non-zero; error mentions `PULSE_BASE_URL`.

---

## TC-AUTH-003 — Missing `PULSE_API_KEY`

**Expect:** Same pattern for `PULSE_API_KEY`.

---

## TC-AUTH-004 — Invalid or revoked API key

**Setup:** Garbage key or revoked token.

**Expect:** Startup `Failed to exchange API key`, exit **1**. No dangling half-registered server.

---

## TC-AUTH-005 — Wrong base URL (host up, wrong service)

**Setup:** Point to non-pulse HTTP server returning HTML or 404.

**Expect:** Startup exchange fails with clear Axios/network message.

---

## TC-AUTH-006 — **[P]** Token refresh on 401 during tool call

**Intent:** Verify interceptor re-exchanges API key (not silent failure).

**Precondition:** MCP already running with valid cached creds.

**Steps**

1. Call `list_projects`.
2. On server/backing store: invalidate sessions if you have a harness **or** revoke/rotate forcing 401 — **alternative:** temporarily set wrong token in credentials file mid-run only in a disposable env (advanced).

**Minimal practical variant:** revoke key **after** successful start; trigger a tool → expect retry path uses `process.env.PULSE_API_KEY` (still set) and either recovers **or** fails with exchanged error if revoke blocks re-exchange.

**Expect:** Either successful retry **or** single failure with message referencing 401/unauthorized—not endless loop.

**Record:** whether `credentials.json` mtime updates after recovery.

---

## TC-AUTH-007 — **`tools/list` smoke**

**Steps:** Send JSON-RPC `tools/list` or use client UI.

**Expect:** Names match `00-matrix-tool-inventory.md`; no duplicate names; **45** tools (verify via `grep server.tool(` in `pulse-mcp/src` if disputed).

---

## TC-AUTH-008 — MCP tool argument validation (representative)

**Intent:** Ensures invalid types rejected **before** HTTP.

Pick any Zod-backed tool with numeric field, e.g. `get_event_definition`:

- `{ "projectId": "x", "id": "not-a-number" }` → MCP layer should reject / error.

---

## Operational note (who suffers if skipped)

Skipping **TC-AUTH-006** hides “looks healthy until first idle expiry” breakage. Skipping **TC-AUTH-001** file permissions hides world-readable tokens on shared machines.

