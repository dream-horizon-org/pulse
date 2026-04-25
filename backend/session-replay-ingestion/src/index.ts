import { CompressionCodecs, CompressionTypes } from 'kafkajs'
import { decompress as zstdDecompress } from 'fzstd'

// Register ZSTD codec — the topic uses ZSTD-compressed messages; KafkaJS
// doesn't include a ZSTD decoder by default so we register one at startup.
CompressionCodecs[CompressionTypes.ZSTD] = () => ({
    compress: async (_encoder: unknown): Promise<Buffer> => {
        throw new Error('[Codec] ZSTD compression not supported for producing')
    },
    decompress: async (buffer: Buffer): Promise<Buffer> => {
        return Buffer.from(zstdDecompress(new Uint8Array(buffer)))
    },
})

import { loadConfig } from './config'
import { SessionReplayConsumer } from './consumer'

async function main(): Promise<void> {
    console.log('=== Pulse Session Replay Ingestion Consumer ===')

    const config = loadConfig()
    const consumer = new SessionReplayConsumer(config)

    // Graceful shutdown on SIGTERM/SIGINT
    const shutdown = async (signal: string) => {
        console.log(`\nReceived ${signal}, shutting down gracefully...`)
        await consumer.stop()
        process.exit(0)
    }

    process.on('SIGTERM', () => shutdown('SIGTERM'))
    process.on('SIGINT', () => shutdown('SIGINT'))

    try {
        await consumer.start()
    } catch (error) {
        console.error('Fatal error starting consumer:', error)
        process.exit(1)
    }
}

main()
