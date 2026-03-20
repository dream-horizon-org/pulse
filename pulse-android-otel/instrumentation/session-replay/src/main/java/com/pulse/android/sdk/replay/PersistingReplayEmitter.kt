package com.pulse.android.sdk.replay

import com.pulse.android.sdk.replay.events.ReplayCustomEventData
import com.pulse.android.sdk.replay.events.ReplayEvent
import com.pulse.android.sdk.replay.events.ReplayIncrementalMouseInteractionData
import com.pulse.android.sdk.replay.events.ReplayIncrementalMutationData
import com.pulse.utils.PulseOtelUtils
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Wraps a [ReplayEventEmitter] with file-based persistence and a queue so that replay batches
 * survive app process death. When the app is killed, batches already written to disk are sent
 * on the next launch via [sendCachedEvents].
 *
 * Payloads are always encrypted before write and decrypted on read using [replayStorageEncryption].
 *
 * - [maxBatchSize]: **Storage cap** — maximum number of `.replay` batch files retained. When exceeded,
 *   the **oldest** files are deleted (latest-first caching).
 * - [flushAt]: **Upload chunk size** — at most this many batch files are sent per backend request. Also
 *   triggers a flush when the in-memory queue reaches this size (in addition to the periodic timer).
 *
 * Behavior:
 * - Each [emit] writes the batch (as envelope JSON, encrypted) to a file and adds it to an in-memory queue.
 * - A background timer flushes the queue periodically; flush also runs when queue size reaches [flushAt].
 * - On startup, call [sendCachedEvents] once to send any leftover files from a previous run (in chunks of [flushAt]).
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
    private val executor =
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "PulseReplayQueue").apply { isDaemon = true }
        } as ScheduledExecutorService

    /** Used for realSend so that blocking retries (e.g. Thread.sleep) do not block the replay queue. */
    private val networkExecutor =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "PulseReplayNetwork").apply { isDaemon = true }
        }

    private val deque = ArrayDeque<File>()
    private val dequeLock = Any()

    private val isFlushing = AtomicBoolean(false)
    private val shutDown = AtomicBoolean(false)
    private var scheduledFlushFuture: ScheduledFuture<*>? = null

    init {
        storageDir.mkdirs()
        trimPersistedReplayFilesToStorageCap()
        scheduledFlushFuture = scheduleFlush()
    }

    /**
     * Shuts down the emitter: cancels periodic flush and stops all executors.
     * Idempotent. Call from integration uninstall to avoid thread leaks.
     */
    public fun shutdown() {
        if (!shutDown.compareAndSet(false, true)) return
        scheduledFlushFuture?.cancel(false)
        scheduledFlushFuture = null
        networkExecutor.shutdownNow()
        try {
            if (!networkExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                logger("PersistingReplayEmitter network executor did not terminate in time")
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        executor.shutdownNow()
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                logger("PersistingReplayEmitter queue executor did not terminate in time")
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    override fun emit(
        sessionId: String,
        events: List<ReplayEvent>,
    ) {
        if (events.isEmpty()) return
        if (shutDown.get()) return
        executor.execute {
            try {
                val envelope = buildEnvelope(sessionId, events)
                val file = File(storageDir, "${System.currentTimeMillis()}_${UUID.randomUUID()}.replay")
                val bytes = envelope.toByteArray(StandardCharsets.UTF_8)
                val toWrite = replayStorageEncryption.encrypt(bytes)
                file.writeBytes(toWrite)
                synchronized(dequeLock) {
                    deque.addLast(file)
                    evictOldestBatchesWhileOverStorageCap()
                }
                logger("Replay batch persisted: ${file.name} (queue size: ${deque.size}) session_id: $sessionId")
                val eventTypesSummary =
                    events
                        .groupBy { e ->
                            when (val d = e.data) {
                                is ReplayIncrementalMouseInteractionData -> "Touch"
                                is ReplayIncrementalMutationData -> "ViewMutation"
                                is ReplayCustomEventData -> "Custom(${d.tag})"
                                else -> e.type.name
                            }
                        }.entries
                        .joinToString(", ") { "${it.key}(${it.value.size})" }
                val eventWord = if (events.size == 1) "event" else "events"
                PulseOtelUtils.logDebug(ReplayConstants.REPLAY_LOG_TAG) {
                    "[Replay flow] Batch persisted to disk (${events.size} $eventWord) — " +
                        "event types: [$eventTypesSummary] — queue size: ${deque.size}, flush at: $flushAt"
                }
                if (deque.size >= flushAt) {
                    PulseOtelUtils.logDebug(ReplayConstants.REPLAY_LOG_TAG) {
                        "[Replay flow] Queue reached flush threshold ($flushAt) → triggering flush"
                    }
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
            if (shutDown.get()) return@execute
            try {
                trimPersistedReplayFilesToStorageCap()
                val files = listCachedReplayFiles()
                if (files.isEmpty()) return@execute
                logger("Sending ${files.size} cached replay batches from previous run (${flushAt} per request)")
                PulseOtelUtils.logDebug(ReplayConstants.REPLAY_LOG_TAG) {
                    "[Replay flow] sendCachedEvents: found ${files.size} cached batch(es) from previous run"
                }
                sendCachedFileChunksSequentially(files)
            } catch (e: Throwable) {
                logger("Send cached replay events failed: $e")
            }
        }
    }

    /**
     * Flushes the in-memory queue: sends up to [flushAt] batches per request and deletes their files on success.
     * On send failure batches are re-queued and retried on the next flush or app launch.
     * Called periodically and when queue size >= [flushAt].
     */
    override fun flush() {
        if (shutDown.get()) return
        executor.execute { flushIfNeeded() }
    }

    private fun flushIfNeeded() {
        if (shutDown.get()) return
        if (!isFlushing.compareAndSet(false, true)) return
        try {
            val toSend = mutableListOf<File>()
            synchronized(dequeLock) {
                val n = minOf(flushAt, deque.size)
                repeat(n) {
                    deque.removeFirstOrNull()?.let { file -> toSend.add(file) }
                }
            }
            if (toSend.isEmpty()) return
            PulseOtelUtils.logDebug(ReplayConstants.REPLAY_LOG_TAG) {
                "[Replay flow] Flush: taking ${toSend.size} batch(es) from queue (max per upload: $flushAt)"
            }
            val fileToContent =
                readFilesToContent(toSend) { file, e ->
                    logger("Flush failed for ${file.name}: $e")
                    file.delete()
                }
            if (fileToContent.isEmpty()) return
            val payload = buildBatchPayload(fileToContent.map { it.second })
            val filesToRequeue = fileToContent.map { it.first }
            PulseOtelUtils.logDebug(ReplayConstants.REPLAY_LOG_TAG) {
                "[Replay flow] Flush → combining ${fileToContent.size} batch(es) " +
                    "into single request (${payload.length} bytes) → sending to backend"
            }
            networkExecutor.execute {
                realSend(payload).fold(
                    onSuccess = {
                        executor.execute {
                            if (!shutDown.get()) fileToContent.forEach { (file) -> file.delete() }
                        }
                    },
                    onFailure = { t ->
                        PulseOtelUtils.logWarn(ReplayConstants.REPLAY_LOG_TAG, t) {
                            "[Replay flow] Flush send failed, re-queuing ${fileToContent.size} batch(es) for retry"
                        }
                        logger("Flush send failed: ${t.message.orEmpty()}")
                        executor.execute {
                            if (!shutDown.get()) {
                                synchronized(dequeLock) {
                                    filesToRequeue.forEach { deque.addFirst(it) }
                                }
                            }
                        }
                    },
                )
            }
        } finally {
            isFlushing.set(false)
        }
    }

    private fun listCachedReplayFiles(): List<File> {
        val files =
            storageDir.listFiles()?.run {
                filter { it.isFile && it.name.endsWith(".replay") }.sortedBy { it.lastModified() }
            }
        return files.orEmpty()
    }

    /**
     * Deletes oldest `.replay` files on disk until at most [maxBatchSize] remain (newest retained).
     */
    private fun trimPersistedReplayFilesToStorageCap() {
        val files = listCachedReplayFiles()
        if (files.size <= maxBatchSize) return
        val toRemove = files.size - maxBatchSize
        repeat(toRemove) { i ->
            val f = files[i]
            if (f.delete()) {
                logger("Replay storage cap: removed oldest batch ${f.name}")
            } else {
                logger("Replay storage cap: failed to delete ${f.name}")
            }
        }
    }

    /** Caller must hold [dequeLock]. Drops oldest queued files until [deque] size is <= [maxBatchSize]. */
    private fun evictOldestBatchesWhileOverStorageCap() {
        while (deque.size > maxBatchSize) {
            val evicted = deque.removeFirst()
            if (evicted.delete()) {
                logger("Replay storage cap: dropped oldest queued batch ${evicted.name}")
            } else {
                logger("Replay storage cap: failed to delete ${evicted.name}")
            }
        }
    }

    /**
     * Sends cached files in chunks of at most [flushAt] batches per HTTP request (oldest first).
     */
    private fun sendCachedFileChunksSequentially(files: List<File>) {
        val chunks = files.chunked(flushAt)
        val nextChunkIndex = AtomicInteger(0)
        fun sendNextChunk() {
            if (shutDown.get()) return
            val idx = nextChunkIndex.get()
            if (idx >= chunks.size) return
            val chunk = chunks[idx]
            val fileToContent =
                readFilesToContent(chunk) { file, e ->
                    logger("Failed to read cached replay file ${file.name}: $e")
                    file.delete()
                }
            if (fileToContent.isEmpty()) {
                nextChunkIndex.incrementAndGet()
                sendNextChunk()
                return
            }
            val payload = buildBatchPayload(fileToContent.map { it.second })
            PulseOtelUtils.logDebug(ReplayConstants.REPLAY_LOG_TAG) {
                "[Replay flow] Cached chunk ${idx + 1}/${chunks.size} → ${fileToContent.size} batch(es) " +
                    "(${payload.length} bytes) → backend"
            }
            networkExecutor.execute {
                realSend(payload).fold(
                    onSuccess = {
                        executor.execute {
                            if (!shutDown.get()) {
                                fileToContent.forEach { (file) -> file.delete() }
                                nextChunkIndex.incrementAndGet()
                                sendNextChunk()
                            }
                        }
                    },
                    onFailure = { t ->
                        PulseOtelUtils.logWarn(ReplayConstants.REPLAY_LOG_TAG, t) {
                            "[Replay flow] Cached send failed, remaining batch(es) will be retried on next launch"
                        }
                        logger("Send cached replay failed: ${t.message.orEmpty()}")
                    },
                )
            }
        }
        sendNextChunk()
    }

    private fun readFilesToContent(
        files: List<File>,
        onReadError: (File, Throwable) -> Unit,
    ): List<Pair<File, String>> =
        files.mapNotNull { file ->
            try {
                readFileContent(file)?.let { content -> file to content }
            } catch (e: Throwable) {
                onReadError(file, e)
                null
            }
        }

    private fun buildBatchPayload(contents: List<String>): String =
        if (contents.size == 1) contents.single() else contents.joinToString(prefix = "[", postfix = "]", separator = ",")

    private fun scheduleFlush(): ScheduledFuture<*> =
        executor.scheduleWithFixedDelay(
            { flushIfNeeded() },
            flushIntervalSeconds.toLong(),
            flushIntervalSeconds.toLong(),
            TimeUnit.SECONDS,
        )

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
