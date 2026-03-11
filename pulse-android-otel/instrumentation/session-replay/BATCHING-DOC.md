Session Replay – Batching Strategy

This document describes how replay data is batched, when it is sent, and what you can configure. You can paste it as-is into Google Docs, Confluence, Notion, or any editor; formatting is kept minimal so it travels well.


-------------------------------------------------------------------------------
OVERVIEW
-------------------------------------------------------------------------------

Replay uses two levels of batching:

1. Capture-level: One snapshot run (or one touch gesture) produces one batch — a single List<ReplayEvent> (e.g. Meta + FullSnapshot, or several mouse events). This is passed to the emitter as one unit.

2. Queue-level: The default SDK path persists each batch to a file and maintains a queue. Batches are sent to the backend in groups: either when a timer fires, when the queue size reaches a threshold, or on next app launch (cached events).

Each batch is one envelope (one JSON payload with event, timestamp, properties.session_id, properties.snapshot_data, properties.snapshot_source). The backend receives one network payload per batch; the queue only controls when those payloads are sent, not how many events are inside each payload.


-------------------------------------------------------------------------------
WHAT IS A "BATCH"?
-------------------------------------------------------------------------------

Term          | Meaning
--------------|------------------------------------------------------------------
Batch         | One call to ReplayEventEmitter.emit(sessionId, events). One snapshot run or one touch gesture produces one batch.
Envelope      | The JSON wrapper for one batch: { "event": "snapshot", "timestamp": "...", "properties": { "session_id", "snapshot_data", "snapshot_source" } }.
Queue         | In-memory list of files (one file per batch). Each file holds one encrypted envelope.

So: 1 snapshot run → 1 batch → 1 file → 1 envelope sent (when that file is flushed). The queue batches sends (how many envelopes go out in one flush), not the events inside a single envelope.


-------------------------------------------------------------------------------
HOW BATCHING WORKS
-------------------------------------------------------------------------------

1. Emit (capture → emitter)

   • On each throttled snapshot (or touch down/up), the integration builds a List<ReplayEvent> and calls eventEmitter.emit(sessionId, events).

   • With the default PersistingReplayEmitter, this does not send immediately. It:
     - Builds the envelope JSON from (sessionId, events).
     - Encrypts it and writes one file under the SDK storage directory (e.g. <timestamp>_<uuid>.replay).
     - Appends that file to an in-memory queue (deque).

   So after each capture, the batch is persisted and queued.


2. Flush (queue → backend)

   A flush takes up to maxBatchSize files from the front of the queue:

   1. Read and decrypt each file (do not delete yet).
   2. Combine into one payload and call realSend(payload). realSend returns Result<Unit>.
   3. On success: delete the files. On failure (e.g. API error, no network): re-queue the files at the front of the queue so the next flush or app launch will retry.

   Flush runs in two ways:

   • Timer: Every flushIntervalSeconds seconds, a background task runs flushIfNeeded().
   • Size: Right after adding a file, if deque.size >= flushAt, flushIfNeeded() is run immediately.

   So batches are sent in order, and failed sends are retried without data loss.


3. Send cached events (after app kill)

   On next app launch, before starting replay, the SDK calls sendCachedEvents():

   1. List all .replay files in the SDK storage directory (from the previous process).
   2. Sort by file modification time (ascending).
   3. Read and decrypt all files (do not delete yet), combine into one payload, call realSend(payload).
   4. On success: delete the files. On failure: do not delete; files remain on disk and will be retried on the next app launch.

   So any batches that were written to disk but not yet flushed (e.g. app was killed) are sent on the next run, and API/network failures are retried on subsequent launches.


4. Shutdown

   On SDK shutdown, flush() is called so pending batches in the queue are sent (best-effort; the SDK does not wait for the backend to acknowledge).


-------------------------------------------------------------------------------
FLOW SUMMARY
-------------------------------------------------------------------------------

  [Snapshot or touch] → emit(sessionId, events)
         → build envelope → encrypt → write 1 file → add to queue
         → if queue.size >= flushAt → flush()

  [Timer every flushIntervalSeconds] → flush()

  [Flush] → take files from queue → read → realSend(payload) → on success delete, on failure re-queue

  [App restarted] → sendCachedEvents() → list files → read all → realSend(payload) → on success delete, on failure leave files for next launch


-------------------------------------------------------------------------------
CONFIGURABLE PARAMETERS
-------------------------------------------------------------------------------

All batching-related options are on SessionReplayConfig. The tables below list those that affect batching and persistence; for capture (throttle, screenshot, masking, etc.) see the main Session Replay README.

Batching and persistence
------------------------

Parameter                  | Type                      | Default                    | Description
---------------------------|---------------------------|----------------------------|-------------------------------------------------------------------------------
flushIntervalSeconds       | Int                       | 60                         | Interval (seconds) between periodic flushes. Lower = more frequent sends; higher = fewer network calls, more batches queued.
flushAt                     | Int                       | 10                         | Queue size that triggers an immediate flush. When the number of batches (files) in the queue reaches this value, a flush is triggered. Lower = flush sooner; higher = allow more batches to accumulate.
maxBatchSize               | Int                       | 50                         | Maximum number of batches (files) to process per flush. Each flush takes up to this many files from the front of the queue, sends them in order, then deletes the files. Does not limit how many events are in one batch.

Capture-related (affects how often a batch is created)
------------------------------------------------------

Parameter        | Type  | Default | Description
-----------------|-------|---------|-------------------------------------------------------------------------------
throttleDelayMs  | Long  | 1000    | Minimum delay (ms) between snapshot captures per window. Higher = fewer snapshots (fewer batches); lower = more frequent snapshots (more batches).


-------------------------------------------------------------------------------
TUNING GUIDE
-------------------------------------------------------------------------------

• Fewer network calls, more delay: Increase flushIntervalSeconds, increase flushAt, and/or increase throttleDelayMs.

• Faster delivery, more calls: Decrease flushIntervalSeconds, decrease flushAt, and/or decrease throttleDelayMs.

• Large queues: If the queue often grows large, increase maxBatchSize so each flush sends more batches, or decrease flushIntervalSeconds / flushAt so flushes happen more often.

• App kill / offline: Batches are written to disk as soon as they are emitted. On next launch, sendCachedEvents() sends all leftover files in order. Storage location is fixed by the SDK (not configurable).


-------------------------------------------------------------------------------
WHAT IS NOT CONFIGURABLE
-------------------------------------------------------------------------------

• One envelope per batch: Each emit(sessionId, events) becomes exactly one envelope (one file, one send). The SDK does not merge multiple snapshot runs into a single envelope.

• Order: Batches are always sent in the order they were emitted (FIFO queue and file sort by modification time for cached events).

• Send cached on startup: Always runs once when the SDK initializes session replay; there is no switch to disable it.

• Encryption: Always on (default or custom); there is no option to disable encryption for persistence.


-------------------------------------------------------------------------------
SUMMARY
-------------------------------------------------------------------------------

Concept                    | Behavior
---------------------------|-------------------------------------------------------------------------------
Batch                      | One emit(sessionId, events) = one envelope = one file.
When batches are sent      | On timer (flushIntervalSeconds), when queue size ≥ flushAt, on next launch (cached), and on shutdown (flush).
How many sent per flush    | Up to maxBatchSize batches (files), in order.
Configurable               | flushIntervalSeconds, flushAt, maxBatchSize, and (for batch frequency) throttleDelayMs. Storage directory and encryption are fixed by the SDK.
