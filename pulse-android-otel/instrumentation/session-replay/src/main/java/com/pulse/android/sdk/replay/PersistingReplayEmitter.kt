package com.pulse.android.sdk.replay

import com.pulse.android.sdk.replay.events.ReplayEvent
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wraps a [ReplayEventEmitter] with file-based persistence and a queue so that replay batches
 * survive app process death. When the app is killed, batches already written to disk are sent
 * on the next launch via [sendCachedEvents].
 *
 * Payloads are always encrypted before write and decrypted on read using [replayStorageEncryption].
 *
 * Behavior (aligned with PostHog Android):
 * - Each [emit] writes the batch (as envelope JSON, encrypted) to a file and adds it to an in-memory queue.
 * - A background timer flushes the queue periodically; flush also runs when queue size reaches [flushAt].
 * - On startup, call [sendCachedEvents] once to send any leftover files from a previous run.
 */
public class PersistingReplayEmitter(
    private val storageDir: File,
    private val buildEnvelope: (sessionId: String, events: List<ReplayEvent>) -> String,
    private val realSend: (envelopeJson: String) -> Unit,
    private val flushIntervalSeconds: Int,
    private val flushAt: Int,
    private val maxBatchSize: Int,
    private val replayStorageEncryption: ReplayStorageEncryption,
    private val logger: (String) -> Unit = {},
) : ReplayEventEmitter {

    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "PulseReplayQueue").apply { isDaemon = true }
    } as ScheduledExecutorService

    private val deque = ArrayDeque<File>()
    private val dequeLock = Any()

    private val isFlushing = AtomicBoolean(false)

    init {
        storageDir.mkdirs()
        scheduleFlush()
    }

    override fun emit(sessionId: String, events: List<ReplayEvent>) {
        if (events.isEmpty()) return
        executor.execute {
            try {
                val envelope = buildEnvelope(sessionId, events)
                val file = File(storageDir, "${System.currentTimeMillis()}_${UUID.randomUUID()}.replay")
                val bytes = envelope.toByteArray(StandardCharsets.UTF_8)
                val toWrite = replayStorageEncryption.encrypt(bytes)
                file.writeBytes(toWrite)
                synchronized(dequeLock) {
                    deque.addLast(file)
                }
                logger("Replay batch persisted: ${file.name} (queue size: ${deque.size})")
                if (deque.size >= flushAt) {
                    flushIfNeeded()
                }
            } catch (e: Throwable) {
                logger("Replay persist failed: $e")
            }
        }
    }

    /**
     * Sends any replay batches that were persisted in a previous run (e.g. before app was killed).
     * Call once after SDK init, before starting session replay. Runs asynchronously on the queue executor.
     */
    public fun sendCachedEvents() {
        executor.execute {
            try {
                val files = storageDir.listFiles()?.filter { it.isFile && it.name.endsWith(".replay") }?.sortedBy { it.lastModified() }
                    ?: emptyList()
                if (files.isEmpty()) return@execute
                logger("Sending ${files.size} cached replay batches from previous run")
                for (file in files) {
                    try {
                        val content = readFileContent(file)
                        if (content != null) {
                            realSend(content)
                        }
                        file.delete()
                    } catch (e: Throwable) {
                        logger("Failed to send cached replay file ${file.name}: $e")
                    }
                }
            } catch (e: Throwable) {
                logger("Send cached replay events failed: $e")
            }
        }
    }

    /**
     * Flushes the in-memory queue: sends up to [maxBatchSize] batches and deletes their files.
     * Called periodically and when queue size >= [flushAt].
     */
    public fun flush() {
        executor.execute { flushIfNeeded() }
    }

    private fun flushIfNeeded() {
        if (!isFlushing.compareAndSet(false, true)) return
        try {
            val toSend = mutableListOf<File>()
            synchronized(dequeLock) {
                val n = minOf(maxBatchSize, deque.size)
                repeat(n) {
                    deque.removeFirstOrNull()?.let { toSend.add(it) }
                }
            }
            for (file in toSend) {
                try {
                    val content = readFileContent(file)
                    if (content != null) {
                        realSend(content)
                    }
                } catch (e: Throwable) {
                    logger("Flush failed for ${file.name}: $e")
                } finally {
                    file.delete()
                }
            }
        } finally {
            isFlushing.set(false)
        }
    }

    private fun scheduleFlush() {
        executor.scheduleAtFixedRate(
            { flushIfNeeded() },
            flushIntervalSeconds.toLong(),
            flushIntervalSeconds.toLong(),
            TimeUnit.SECONDS,
        )
    }

    private fun readFileContent(file: File): String? {
        val bytes = file.readBytes()
        return try {
            String(replayStorageEncryption.decrypt(bytes), StandardCharsets.UTF_8)
        } catch (_: Throwable) {
            try {
                String(bytes, StandardCharsets.UTF_8)
            } catch (e: Throwable) {
                logger("Replay file read failed ${file.name}: $e")
                null
            }
        }
    }
}
