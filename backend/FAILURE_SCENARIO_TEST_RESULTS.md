# Failure Scenario Test Results

**Date:** March 9, 2026
**Services Under Test:** `session-capture-service` (Rust/Axum) · `session-replay-ingestion` (Node.js)

## Pipeline Architecture

```
Client SDK → Capture Service (:3400) → Kafka → Ingestion Service → S3 (compressed)
                                                                  ↘ Kafka (metadata) → ClickHouse
```

## Results

All 8 scenarios passed.

---

### 1. Ingestion Crash + Kafka Replay

| | |
|---|---|
| **Failure** | Ingestion container killed mid-batch (before 10s flush) |
| **Observed** | Offsets not committed; data absent from S3 and ClickHouse |
| **Recovery** | Container restarted → Kafka replayed from last committed offset → data written to S3 + ClickHouse |
| **Guarantee** | At-least-once delivery confirmed |

---

### 2. Capture Service Down

| | |
|---|---|
| **Failure** | `pulse-session-capture` stopped |
| **Observed** | TCP connection refused — client receives no HTTP response (network-level error, e.g. `ECONNREFUSED`) |
| **Recovery** | Container restarted → immediate HTTP 200 `{"status":"Ok"}` on next request |
| **Guarantee** | Client SDKs should retry on connection errors; service recovers instantly on restart with no warm-up delay |

---

### 3. Kafka Down — Capture Service Behavior

| | |
|---|---|
| **Failure** | `pulse-kafka` stopped |
| **Observed** | HTTP 503 `{"error":"Service temporarily unavailable","status":0}` · `/_liveness` reports `healthy: false, rdkafka: Stalled` |
| **Recovery** | Kafka restarted → capture auto-reconnects → HTTP 200 `{"status":"Ok"}` · liveness returns `healthy: true` |
| **Guarantee** | No silent data loss — the 503 tells the client the event was NOT accepted, so the SDK can retry; load balancers can use `/_liveness` to stop routing traffic until recovery |

---

### 4. Kafka Down — Ingestion Service Behavior

| | |
|---|---|
| **Failure** | `pulse-kafka` stopped while ingestion is running |
| **Observed** | librdkafka logs `Broker transport failure` errors; `consume()` returns empty batches; process stays alive (0 restarts) |
| **Recovery** | Kafka restarted → librdkafka auto-reconnects → consumer resumes processing; all pending messages consumed and flushed |
| **Guarantee** | More resilient than crash-and-restart; zero downtime on recovery |

---

### 5. S3/MinIO Down — Flush Failure

| | |
|---|---|
| **Failure** | `pulse-minio` stopped before ingestion flush |
| **Observed** | S3 health check fails on startup → `process.exit(1)` · Docker restarts container (`Restarting (1)`) |
| **Recovery** | MinIO restarted → Docker restarts ingestion → Kafka replays from uncommitted offset → data written to S3 + ClickHouse |
| **Guarantee** | No data loss; Kafka retains messages until offsets are committed after successful S3 write |

---

### 6. ClickHouse Down — S3 Unaffected

| | |
|---|---|
| **Failure** | `pulse-clickhouse` stopped |
| **Observed** | S3 writes succeed normally (file count increased); metadata published to Kafka topic (not directly to ClickHouse) |
| **Recovery** | ClickHouse restarted → its Kafka engine table auto-consumes pending metadata → rows appear in ClickHouse |
| **Guarantee** | S3 and ClickHouse are decoupled via Kafka; ClickHouse outage has zero impact on data ingestion |

---

### 7. Oversized Payload (413)

| | |
|---|---|
| **Failure** | 24.8 MB payload sent (body limit: 25 MB compressed) |
| **Observed** | HTTP 413 `{"error":"Event too large for sink"}` |
| **Recovery** | N/A — service remains healthy, no crash |
| **Guarantee** | Clean rejection; no resource exhaustion |

---

### 8. Malformed Payload (400)

| | |
|---|---|
| **Test Case** | **Response** |
| Invalid JSON | 400 `{"error":"Invalid payload"}` |
| Missing `project_id` | 400 `{"error":"Missing project_id"}` |
| Missing `session_id` | 400 `{"error":"Missing session_id"}` |
| Missing `snapshot_data` | 400 `{"error":"Missing snapshot_data"}` |

Service remained healthy after all requests.

---

## Key Takeaways

1. **Kafka is the durability backbone.** Offsets are committed only after successful S3 writes, ensuring at-least-once delivery across crashes.
2. **ClickHouse is fully decoupled.** It consumes metadata from a Kafka topic asynchronously — its outage never blocks ingestion.
3. **librdkafka handles broker reconnection automatically.** The ingestion service survives Kafka outages without crashing.
4. **Capture service degrades gracefully.** Returns 503 with a retryable error when Kafka is down; liveness endpoint reflects actual health.
5. **Input validation is strict.** Oversized and malformed payloads are rejected at the edge with appropriate HTTP status codes.
