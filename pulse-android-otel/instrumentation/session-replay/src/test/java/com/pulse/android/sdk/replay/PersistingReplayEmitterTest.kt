package com.pulse.android.sdk.replay

import com.pulse.android.sdk.replay.events.ReplayMetaEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class PersistingReplayEmitterTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `emit with empty events does nothing`() {
        val sent = AtomicReference<String?>(null)
        val emitter = PersistingReplayEmitter(
            storageDir = tempDir,
            buildEnvelope = { _, _ -> """{"event":"snapshot"}""" },
            realSend = { Result.success(Unit) },
            flushIntervalSeconds = 60,
            flushAt = 10,
            maxBatchSize = 50,
            replayStorageEncryption = IdentityReplayStorageEncryption(),
        )
        emitter.emit("sid", emptyList())
        Thread.sleep(300)
        assertThat(tempDir.listFiles()).isEmpty()
        assertThat(sent.get()).isNull()
    }

    @Test
    fun `emit persists envelope to file and enqueues`() {
        val envelope = """{"event":"snapshot","properties":{"session_id":"sid-1"}}"""
        val emitter = PersistingReplayEmitter(
            storageDir = tempDir,
            buildEnvelope = { _, _ -> envelope },
            realSend = { Result.success(Unit) },
            flushIntervalSeconds = 60,
            flushAt = 10,
            maxBatchSize = 50,
            replayStorageEncryption = IdentityReplayStorageEncryption(),
        )
        val events = listOf(ReplayMetaEvent(1080, 1920, 1000L, "Test"))
        emitter.emit("sid-1", events)
        Thread.sleep(500)
        val files = tempDir.listFiles()?.filter { it.name.endsWith(".replay") } ?: emptyList()
        assertThat(files).hasSize(1)
        assertThat(String(files.single().readBytes(), Charsets.UTF_8)).isEqualTo(envelope)
    }

    @Test
    fun `sendCachedEvents sends persisted file and deletes on success`() {
        val envelope = """{"event":"snapshot","properties":{"session_id":"cached"}}"""
        val file = File(tempDir, "1000_uuid.replay")
        file.writeBytes(envelope.toByteArray(Charsets.UTF_8))
        val sent = AtomicReference<String?>(null)
        val latch = CountDownLatch(1)
        val emitter = PersistingReplayEmitter(
            storageDir = tempDir,
            buildEnvelope = { _, _ -> envelope },
            realSend = { payload ->
                sent.set(payload)
                latch.countDown()
                Result.success(Unit)
            },
            flushIntervalSeconds = 60,
            flushAt = 10,
            maxBatchSize = 50,
            replayStorageEncryption = IdentityReplayStorageEncryption(),
        )
        emitter.sendCachedEvents()
        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue()
        assertThat(sent.get()).isEqualTo(envelope)
        Thread.sleep(200)
        val remaining = (tempDir.listFiles() ?: arrayOf()).filter { it.isFile }
        assertThat(remaining).isEmpty()
    }

    @Test
    fun `sendCachedEvents keeps files on send failure`() {
        val envelope = """{"event":"snapshot"}"""
        val file = File(tempDir, "1000_abc.replay")
        file.writeBytes(envelope.toByteArray(Charsets.UTF_8))
        val latch = CountDownLatch(1)
        val emitter = PersistingReplayEmitter(
            storageDir = tempDir,
            buildEnvelope = { _, _ -> envelope },
            realSend = {
                latch.countDown()
                Result.failure(Exception("network error"))
            },
            flushIntervalSeconds = 60,
            flushAt = 10,
            maxBatchSize = 50,
            replayStorageEncryption = IdentityReplayStorageEncryption(),
        )
        emitter.sendCachedEvents()
        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue()
        val files = tempDir.listFiles()?.filter { it.name.endsWith(".replay") } ?: emptyList()
        assertThat(files).hasSize(1)
    }

    @Test
    fun `flush sends queued batches and deletes files on success`() {
        val envelope = """{"event":"snapshot"}"""
        val sent = AtomicReference<String?>(null)
        val latch = CountDownLatch(1)
        val emitter = PersistingReplayEmitter(
            storageDir = tempDir,
            buildEnvelope = { _, _ -> envelope },
            realSend = { payload ->
                sent.set(payload)
                latch.countDown()
                Result.success(Unit)
            },
            flushIntervalSeconds = 60,
            flushAt = 1,
            maxBatchSize = 50,
            replayStorageEncryption = IdentityReplayStorageEncryption(),
        )
        emitter.emit("sid", listOf(ReplayMetaEvent(800, 600, 0L, "")))
        Thread.sleep(400)
        emitter.flush()
        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue()
        assertThat(sent.get()).isEqualTo(envelope)
        Thread.sleep(200)
        assertThat(tempDir.listFiles()?.filter { it.name.endsWith(".replay") }).isEmpty()
    }
}
