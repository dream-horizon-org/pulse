import * as snappy from 'snappy'
import { DateTime } from 'luxon'
import { SnapshotEvent } from '../kafka/types'

/**
 * Result of finalizing a session recording block.
 */
export interface EndResult {
    /** Snappy-compressed JSONL buffer */
    buffer: Buffer
    /** Compressed size in bytes */
    compressedSize: number
    /** Uncompressed size in bytes */
    uncompressedSize: number
    /** Earliest event timestamp */
    startDateTime: DateTime
    /** Latest event timestamp */
    endDateTime: DateTime
}

/**
 * Records events for a single session, buffering them as JSONL lines
 * and compressing the final output with Snappy.
 *
 * JSONL format (one event per line):
 *   {"timestamp":1700000000000,"type":2,"data":{...}}
 *   {"timestamp":1700000001000,"type":3,"data":{...}}
 */
export class SnappySessionRecorder {
    private uncompressedChunks: Buffer[] = []
    private _uncompressedSize: number = 0
    private _eventCount: number = 0
    private startDateTime: DateTime | null = null
    private endDateTime: DateTime | null = null

    get uncompressedSize(): number {
        return this._uncompressedSize
    }

    get isEmpty(): boolean {
        return this._eventCount === 0
    }

    /**
     * Record a batch of events from a single Kafka message for this session.
     * Events are serialized as JSONL lines and buffered.
     *
     * @returns The uncompressed size of the added data.
     */
    public recordEvents(
        events: SnapshotEvent[],
        eventsRange: { start: DateTime; end: DateTime }
    ): number {
        let addedSize = 0

        for (const event of events) {
            const line = JSON.stringify(event) + '\n'
            const buf = Buffer.from(line)
            this.uncompressedChunks.push(buf)
            addedSize += buf.length
            this._eventCount++
        }

        this._uncompressedSize += addedSize

        // Track time range
        if (this.startDateTime === null || eventsRange.start < this.startDateTime) {
            this.startDateTime = eventsRange.start
        }
        if (this.endDateTime === null || eventsRange.end > this.endDateTime) {
            this.endDateTime = eventsRange.end
        }

        return addedSize
    }

    /**
     * Finalize: concatenate all JSONL lines and compress with Snappy.
     */
    public async end(): Promise<EndResult> {
        const uncompressedData = Buffer.concat(this.uncompressedChunks)
        const buffer = await snappy.compress(uncompressedData)

        return {
            buffer: Buffer.from(buffer),
            compressedSize: buffer.length,
            uncompressedSize: uncompressedData.length,
            startDateTime: this.startDateTime ?? DateTime.utc(),
            endDateTime: this.endDateTime ?? DateTime.utc(),
        }
    }
}
