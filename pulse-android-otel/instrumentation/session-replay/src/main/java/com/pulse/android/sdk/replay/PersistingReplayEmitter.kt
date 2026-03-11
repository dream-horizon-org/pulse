package com.pulse.android.sdk.replay

import android.util.Log
import com.pulse.android.sdk.replay.events.ReplayCustomEvent
import com.pulse.android.sdk.replay.events.ReplayEvent
import com.pulse.android.sdk.replay.events.ReplayIncrementalMouseInteractionEvent
import com.pulse.android.sdk.replay.events.ReplayIncrementalSnapshotEvent
import com.pulse.android.sdk.replay.internal.ReplayLog
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
 *
 * When [realSend] returns [Result.failure] (e.g. API error or no network), files are not deleted:
 * - Flush: failed batches are re-queued and retried on the next flush or app launch.
 * - Send cached: files stay on disk and are retried on the next app launch.
 */
public class PersistingReplayEmitter(
    private val storageDir: File,
    private val buildEnvelope: (sessionId: String, events: List<ReplayEvent>) -> String,
    private val realSend: (envelopeJson: String) -> Result<Unit>,
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
                logger("Replay batch persisted: ${file.name} (queue size: ${deque.size}) session_id: $sessionId")
                val eventTypesSummary = events
                    .groupBy { e ->
                        when (e) {
                            is ReplayIncrementalMouseInteractionEvent -> "Touch"
                            is ReplayIncrementalSnapshotEvent -> "ViewMutation"
                            is ReplayCustomEvent -> "Custom(${(e.data as? Map<*, *>)?.get("tag")?.toString() ?: "?"})"
                            else -> e.type.name
                        }
                    }
                    .entries.joinToString(", ") { "${it.key}(${it.value.size})" }
                val eventWord = if (events.size == 1) "event" else "events"
                Log.d(ReplayLog.TAG, "[Replay flow] Batch persisted to disk (${events.size} $eventWord) — event types: [$eventTypesSummary] — queue size: ${deque.size}, flush at: $flushAt")
                if (deque.size >= flushAt) {
                    Log.d(ReplayLog.TAG, "[Replay flow] Queue reached flush threshold ($flushAt) → triggering flush")
                    flushIfNeeded()
                }
            } catch (e: Throwable) {
                logger("Replay persist failed: $e")
            }
        }
    }

    /**
     * Sends any replay batches that were persisted in a previous run (e.g. before app was killed).
     * Batches all cached envelopes into a single request when possible.
     * Only deletes files after successful send; on failure files stay on disk and are retried on next launch.
     * Call once after SDK init, before starting session replay. Runs asynchronously on the queue executor.
     */
    public fun sendCachedEvents() {
        executor.execute {
            try {
                val files = listCachedReplayFiles()
                if (files.isEmpty()) return@execute
                logger("Sending ${files.size} cached replay batches from previous run")
                Log.d(ReplayLog.TAG, "[Replay flow] sendCachedEvents: found ${files.size} cached batch(es) from previous run")
                val fileToContent = readFilesToContent(files) { file, e ->
                    logger("Failed to read cached replay file ${file.name}: $e")
                    file.delete()
                }
                if (fileToContent.isEmpty()) return@execute
                val payload = buildBatchPayload(fileToContent.map { it.second })
                Log.d(ReplayLog.TAG, "[Replay flow] Cached → combining ${fileToContent.size} batch(es) into single request (${payload.length} bytes) → flushing to backend")
                realSend(payload).fold(
                    onSuccess = { fileToContent.forEach { (file) -> file.delete() } },
                    onFailure = { t ->
                        Log.w(ReplayLog.TAG, "[Replay flow] Cached send failed, ${fileToContent.size} batch(es) will be retried on next launch", t)
                        logger("Send cached replay failed: ${t.message}")
                    },
                )
            } catch (e: Throwable) {
                logger("Send cached replay events failed: $e")
            }
        }
    }

    /**
     * Flushes the in-memory queue: sends up to [maxBatchSize] batches and deletes their files on success.
     * On send failure batches are re-queued and retried on the next flush or app launch.
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
            if (toSend.isEmpty()) return
            Log.d(ReplayLog.TAG, "[Replay flow] Flush: taking ${toSend.size} batch(es) from queue (maxBatchSize: $maxBatchSize)")
            val fileToContent = readFilesToContent(toSend) { file, e ->
                logger("Flush failed for ${file.name}: $e")
                file.delete()
            }
            if (fileToContent.isEmpty()) return
            val payload = buildBatchPayload(fileToContent.map { it.second })
            Log.d(ReplayLog.TAG, "[Replay flow] Flush → combining ${fileToContent.size} batch(es) into single request (${payload.length} bytes) → sending to backend")
            realSend(payload).fold(
                onSuccess = { fileToContent.forEach { (file) -> file.delete() } },
                onFailure = { t ->
                    Log.w(ReplayLog.TAG, "[Replay flow] Flush send failed, re-queuing ${fileToContent.size} batch(es) for retry", t)
                    logger("Flush send failed: ${t.message}")
                    synchronized(dequeLock) {
                        fileToContent.map { it.first }.forEach { deque.addFirst(it) }
                    }
                },
            )
        } finally {
            isFlushing.set(false)
        }
    }

    private fun listCachedReplayFiles(): List<File> =
        storageDir.listFiles()?.filter { it.isFile && it.name.endsWith(".replay") }?.sortedBy { it.lastModified() }
            ?: emptyList()

    private fun readFilesToContent(
        files: List<File>,
        onReadError: (File, Throwable) -> Unit,
    ): List<Pair<File, String>> = files.mapNotNull { file ->
        try {
            readFileContent(file)?.let { file to it }
        } catch (e: Throwable) {
            onReadError(file, e)
            null
        }
    }

    private fun buildBatchPayload(contents: List<String>): String =
        if (contents.size == 1) contents.single() else contents.joinToString(prefix = "[", postfix = "]", separator = ",")

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
