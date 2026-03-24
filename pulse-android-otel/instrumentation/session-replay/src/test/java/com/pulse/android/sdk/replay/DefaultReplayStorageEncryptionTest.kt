package com.pulse.android.sdk.replay

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@Config(sdk = [33])
@RunWith(RobolectricTestRunner::class)
class DefaultReplayStorageEncryptionTest {
    private lateinit var encryption: DefaultReplayStorageEncryption

    @Before
    fun setUp() {
        encryption = DefaultReplayStorageEncryption(RuntimeEnvironment.getApplication())
    }

    @Test
    fun `round-trip encrypt then decrypt returns original bytes`() {
        val original = "hello session replay".toByteArray(Charsets.UTF_8)
        val encrypted = encryption.encrypt(original)
        val decrypted = encryption.decrypt(encrypted)
        assertThat(decrypted).isEqualTo(original)
    }

    @Test
    fun `encrypted bytes differ from plaintext`() {
        val original = "secret payload".toByteArray(Charsets.UTF_8)
        val encrypted = encryption.encrypt(original)
        assertThat(encrypted).isNotEqualTo(original)
    }

    @Test
    fun `ciphertext too short throws IllegalArgumentException`() {
        val tooShort = ByteArray(5) { 0x42 }
        assertThatThrownBy { encryption.decrypt(tooShort) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("too short")
    }

    @Test
    fun `empty plaintext round-trips correctly`() {
        val empty = ByteArray(0)
        val encrypted = encryption.encrypt(empty)
        val decrypted = encryption.decrypt(encrypted)
        assertThat(decrypted).isEqualTo(empty)
    }

    @Test
    fun `large payload round-trips correctly`() {
        val large = ByteArray(100_000) { (it % 256).toByte() }
        val encrypted = encryption.encrypt(large)
        val decrypted = encryption.decrypt(encrypted)
        assertThat(decrypted).isEqualTo(large)
    }

    @Test
    fun `key is persisted across instances with same context`() {
        val context = RuntimeEnvironment.getApplication()
        val first = DefaultReplayStorageEncryption(context)
        val second = DefaultReplayStorageEncryption(context)
        val original = "consistency check".toByteArray(Charsets.UTF_8)
        val encrypted = first.encrypt(original)
        val decrypted = second.decrypt(encrypted)
        assertThat(decrypted).isEqualTo(original)
    }

    @Test
    fun `corrupted ciphertext throws on decrypt`() {
        val original = "valid data".toByteArray(Charsets.UTF_8)
        val encrypted = encryption.encrypt(original)
        encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1].toInt() xor 0xFF).toByte()
        assertThatThrownBy { encryption.decrypt(encrypted) }
            .isInstanceOf(Exception::class.java)
    }
}
