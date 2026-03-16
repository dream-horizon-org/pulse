package com.pulse.android.sdk.replay

import android.content.Context
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Default encryption for replay files: AES-256-GCM. Key is generated once and stored in
 * app-private SharedPreferences. Applied by the SDK for all persisted replay files.
 * For hardware-backed key storage, provide a custom [ReplayStorageEncryption] using Android Keystore.
 */
public class DefaultReplayStorageEncryption(context: Context) : ReplayStorageEncryption {

    private val key: SecretKey = getOrCreateKey(context)

    private companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val KEY_SIZE = 256
        private const val PREFS_NAME = "pulse_replay_encryption"
        private const val PREF_KEY_SECRET = "replay_key"
    }

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = ByteArray(GCM_IV_LENGTH).also { java.security.SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        if (ciphertext.size <= GCM_IV_LENGTH) {
            throw IllegalArgumentException("Ciphertext too short")
        }
        val iv = ciphertext.copyOf(GCM_IV_LENGTH)
        val data = ciphertext.copyOfRange(GCM_IV_LENGTH, ciphertext.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(data)
    }

    private fun getOrCreateKey(context: Context): SecretKey {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encoded = prefs.getString(PREF_KEY_SECRET, null)
        return if (encoded != null) {
            SecretKeySpec(Base64.decode(encoded, Base64.NO_WRAP), "AES")
        } else {
            val keyGenerator = KeyGenerator.getInstance("AES").apply { init(KEY_SIZE) }
            val secretKey = keyGenerator.generateKey()
            prefs.edit().putString(PREF_KEY_SECRET, Base64.encodeToString(secretKey.encoded, Base64.NO_WRAP)).apply()
            secretKey
        }
    }
}
