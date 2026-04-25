"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.SnappySessionRecorder = void 0;
const luxon_1 = require("luxon");
const snappy_1 = require("snappy");
/**
 * Records events for a single session recording using Snappy compression.
 *
 * Buffers events and provides them as a snappy-compressed session recording block that can be
 * stored in a session batch file. The session recording block can be read as an independent unit.
 *
 * ```
 * Session Batch File
 * ├── Snappy Session Recording Block 1 <── One SnappySessionRecorder corresponds to one block
 * │   └── JSONL Session Recording Block
 * │       ├── {"timestamp":..., "type":..., "data":{...}}
 * │       ├── {"timestamp":..., "type":..., "data":{...}}
 * │       └── ...
 * ├── Snappy Session Recording Block 2
 * │   └── ...
 * └── ...
 * ```
 *
 * The session block format (after decompression) is a sequence of newline-delimited JSON records.
 */
class SnappySessionRecorder {
    constructor(sessionId, projectId, batchId) {
        this.sessionId = sessionId;
        this.projectId = projectId;
        this.batchId = batchId;
        this.uncompressedChunks = [];
        this.eventCount = 0;
        this.ended = false;
        this.startDateTime = null;
        this.endDateTime = null;
        this._userId = null;
        this.snapshotSource = null;
    }
    /**
     * Records a message containing events for this session.
     * Events are buffered until end() is called.
     *
     * @param message - Parsed message from Kafka
     * @returns Number of raw bytes written (before compression)
     * @throws If called after end()
     */
    recordMessage(message) {
        if (this.ended) {
            throw new Error("Cannot record message after end() has been called");
        }
        if (!this._userId) {
            this._userId = message.user_id;
        }
        if (!this.snapshotSource) {
            this.snapshotSource = message.snapshot_source ?? "mobile";
        }
        if (!this.startDateTime || message.eventsRange.start < this.startDateTime) {
            this.startDateTime = message.eventsRange.start;
        }
        if (!this.endDateTime || message.eventsRange.end > this.endDateTime) {
            this.endDateTime = message.eventsRange.end;
        }
        let rawBytesWritten = 0;
        for (const event of message.events) {
            const line = JSON.stringify(event) + "\n";
            const chunk = Buffer.from(line);
            this.uncompressedChunks.push(chunk);
            this.eventCount++;
            rawBytesWritten += chunk.length;
        }
        return rawBytesWritten;
    }
    /**
     * The user_id associated with this session recording.
     */
    get userId() {
        if (!this._userId) {
            throw new Error("No user_id set. No messages recorded yet.");
        }
        return this._userId;
    }
    get isEmpty() {
        return this.eventCount === 0;
    }
    /**
     * Finalizes the session recording and returns the compressed buffer with metadata.
     * Uses Snappy compression (native, no WASM memory limits).
     *
     * @returns The compressed session recording block with metadata
     * @throws If called more than once
     */
    async end() {
        if (this.ended) {
            throw new Error("end() has already been called");
        }
        this.ended = true;
        const uncompressedBuffer = Buffer.concat(this.uncompressedChunks);
        // Snappy compression uses native bindings (no WASM limits) - can handle large buffers
        const compressed = await (0, snappy_1.compress)(uncompressedBuffer);
        return {
            buffer: compressed,
            eventCount: this.eventCount,
            startDateTime: this.startDateTime ?? luxon_1.DateTime.fromMillis(0),
            endDateTime: this.endDateTime ?? luxon_1.DateTime.fromMillis(0),
            snapshotSource: this.snapshotSource,
            batchId: this.batchId,
        };
    }
}
exports.SnappySessionRecorder = SnappySessionRecorder;
//# sourceMappingURL=snappy-session-recorder.js.map