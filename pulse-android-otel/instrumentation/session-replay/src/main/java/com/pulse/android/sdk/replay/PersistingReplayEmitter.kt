package com.pulse.android.sdk.replay

import com.pulse.android.sdk.replay.events.ReplayCustomEventData
import com.pulse.android.sdk.replay.events.ReplayEvent
import com.pulse.android.sdk.replay.events.ReplayIncrementalMouseInteractionData
import com.pulse.android.sdk.replay.events.ReplayIncrementalMutationData
import com.pulse.utils.PulseOtelUtils
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
 * - A background coroutine flushes the queue periodically; flush also runs when queue size reaches [flushAt].
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
    /** Single-dispatcher scope for file I/O and network. Replaces two dedicated executor threads. */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val deque = ArrayDeque<File>()

    /** Guards all deque read/write operations. */
    private val dequeMutex = Mutex()

    /** Prevents concurrent flush runs — tryLock() skips a flush if one is already in progress. */
    private val flushMutex = Mutex()

    private val shutDown = AtomicBoolean(false)
    private var periodicFlushJob: Job? = null

    init {
        storageDir.mkdirs()
        trimPersistedReplayFilesToStorageCap()
        periodicFlushJob = scope.launch {
            while (isActive) {
                delay(flushIntervalSeconds * 1000L)
                flushIfNeeded()
            }
        }
    }

    /**
     * Shuts down the emitter: cancels all coroutines and the scope.
     * Idempotent. Call from integration uninstall to avoid resource leaks.
     */
    public fun shutdown() {
        if (!shutDown.compareAndSet(false, true)) return
        scope.cancel()
    }

    override fun emit(
        sessionId: String,
        events: List<ReplayEvent>,
    ) {
        if (events.isEmpty()) return
        if (shutDown.get()) return
        scope.launch {
            try {
                val envelope = buildEnvelope(sessionId, events)
                val file = File(storageDir, "${System.currentTimeMillis()}_${UUID.randomUUID()}.replay")
                val bytes = envelope.toByteArray(StandardCharsets.UTF_8)
                val toWrite = replayStorageEncryption.encrypt(bytes)
                file.writeBytes(toWrite)
                val queueSize = dequeMutex.withLock {
                    deque.addLast(file)
                    evictOldestBatchesWhileOverStorageCap()
                    deque.size
                }
                logger("Replay batch persisted: ${file.name} (queue size: $queueSize) session_id: $sessionId")
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
                        "event types: [$eventTypesSummary] — queue size: $queueSize, flush at: $flushAt"
                }
                if (queueSize >= flushAt) {
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
     * Only deletes files after successful send; on failure files stay on disk and are retried on next launch.
     * Call once after SDK init, before starting session replay. Runs asynchronously on the IO dispatcher.
     */
    public fun sendCachedEvents() {
        scope.launch {
            if (shutDown.get()) return@launch
            try {
                trimPersistedReplayFilesToStorageCap()
                val files = listCachedReplayFiles()
                if (files.isEmpty()) return@launch
                logger("Sending ${files.size} cached replay batches from previous run ($flushAt per request)")
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
        scope.launch { flushIfNeeded() }
    }

    private suspend fun flushIfNeeded() {
        if (shutDown.get()) return
        if (!flushMutex.tryLock()) return
        try {
            val toSend = dequeMutex.withLock {
                val n = minOf(flushAt, deque.size)
                List(n) { deque.removeFirst() }
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
            PulseOtelUtils.logDebug(ReplayConstants.REPLAY_LOG_TAG) {
                "[Replay flow] Flush → combining ${fileToContent.size} batch(es) " +
                    "into single request (${payload.length} bytes) → sending to backend"
            }
            realSend(payload).fold(
                onSuccess = {
                    if (!shutDown.get()) fileToContent.forEach { (file) -> file.delete() }
                },
                onFailure = { t ->
                    PulseOtelUtils.logWarn(ReplayConstants.REPLAY_LOG_TAG, t) {
                        "[Replay flow] Flush send failed, re-queuing ${fileToContent.size} batch(es) for retry"
                    }
                    logger("Flush send failed: ${t.message.orEmpty()}")
                    if (!shutDown.get()) {
                        dequeMutex.withLock {
                            fileToContent.forEach { (file) -> deque.addFirst(file) }
                        }
                    }
                },
            )
        } finally {
            flushMutex.unlock()
        }
    }

    private fun listCachedReplayFiles(): List<File> =
        storageDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".replay") }
            ?.sortedBy { it.lastModified() }
            .orEmpty()

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

    /** Caller must hold [dequeMutex]. Drops oldest queued files until [deque] size is <= [maxBatchSize]. */
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
     * Sends cached files in chunks of at most [flushAt] per request (oldest first).
     * Stops on first send failure; remaining files stay on disk for retry on next launch.
     */
    private suspend fun sendCachedFileChunksSequentially(files: List<File>) {
        val chunks = files.chunked(flushAt)
        for ((idx, chunk) in chunks.withIndex()) {
            if (shutDown.get()) return
            val fileToContent =
                readFilesToContent(chunk) { file, e ->
                    logger("Failed to read cached replay file ${file.name}: $e")
                    file.delete()
                }
            if (fileToContent.isEmpty()) continue
            val payload = buildBatchPayload(fileToContent.map { it.second })
            PulseOtelUtils.logDebug(ReplayConstants.REPLAY_LOG_TAG) {
                "[Replay flow] Cached chunk ${idx + 1}/${chunks.size} → ${fileToContent.size} batch(es) " +
                    "(${payload.length} bytes) → backend"
            }
            realSend(payload).fold(
                onSuccess = {
                    if (!shutDown.get()) fileToContent.forEach { (file) -> file.delete() }
                },
                onFailure = { t ->
                    PulseOtelUtils.logWarn(ReplayConstants.REPLAY_LOG_TAG, t) {
                        "[Replay flow] Cached send failed, remaining batch(es) will be retried on next launch"
                    }
                    logger("Send cached replay failed: ${t.message.orEmpty()}")
                    return
                },
            )
        }
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
