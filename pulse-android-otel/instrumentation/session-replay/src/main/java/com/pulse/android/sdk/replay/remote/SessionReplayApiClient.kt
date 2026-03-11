package com.pulse.android.sdk.replay.remote

import android.util.Log
import com.pulse.android.sdk.replay.internal.ReplayLog
import com.pulse.utils.PulseNetworkingUtils
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Sends session replay snapshot batches to the backend API.
 * Uses the same common [OkHttpClient] as interaction and sampling
 * ([PulseNetworkingUtils.okHttpClient]); no client is passed from outside.
 *
 * API contract:
 * - POST to [baseUrl]/s/
 * - Content-Type: application/json
 * - Body: JSON array of snapshot events (envelope already contains event, project_id, user_id, properties).
 */
public class SessionReplayApiClient(
    private val baseUrl: String,
    private val maxRetries: Int = 2,
    private val retryDelayMs: Long = 1_000L,
) {

    private val okHttpClient: OkHttpClient
        get() = PulseNetworkingUtils.okHttpClient

    private val uploadUrl: String by lazy {
        PulseNetworkingUtils.endWithSlash(baseUrl) + SNAPSHOT_PATH
    }

    /**
     * Sends one or more snapshot envelopes to the backend in a single POST.
     * [payload] may be a single JSON object or a JSON array of objects (batch).
     *
     * @param payload Single envelope (object) or pre-batched array string.
     * @return [Result.success] on 2xx, [Result.failure] on network error, 4xx, 5xx, or timeout.
     */
    public fun sendBatch(payload: String): Result<Unit> {
        val body = if (payload.trimStart().startsWith("[")) payload else "[$payload]"
        var lastFailure: Throwable? = null
        repeat(maxRetries + 1) { attempt ->
            val result = executePost(body)
            result.fold(
                onSuccess = { return result },
                onFailure = { t ->
                    lastFailure = t
                    if (attempt < maxRetries && shouldRetry(t)) {
                        Thread.sleep(retryDelayMs)
                    } else {
                        return Result.failure(t)
                    }
                },
            )
        }
        return Result.failure(lastFailure ?: IOException("Session replay upload failed after $maxRetries retries"))
    }

    private fun shouldRetry(t: Throwable): Boolean {
        if (t is HttpException) return t.code in 500..599
        if (t is IOException) return true
        return false
    }

    private fun executePost(body: String): Result<Unit> = runCatching {
        val request = Request.Builder()
            .url(uploadUrl)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val responseBody = response.body?.string()?.take(MAX_ERROR_BODY_LOG) ?: ""
                Log.e(ReplayLog.TAG, "Session replay API error: ${response.code} ${response.message}. Body: $responseBody")
                if (body.length <= MAX_REQUEST_LOG) {
                    Log.e(ReplayLog.TAG, "Request payload: $body")
                } else {
                    Log.e(ReplayLog.TAG, "Request payload (first ${MAX_REQUEST_LOG} chars): ${body.take(MAX_REQUEST_LOG)}...")
                }
                throw HttpException(response.code, response.message, responseBody)
            }
        }
    }

    private class HttpException(
        val code: Int,
        message: String?,
        val responseBody: String = "",
    ) : IOException("HTTP $code: $message${if (responseBody.isNotEmpty()) " — $responseBody" else ""}")

    private companion object {
        private const val MAX_ERROR_BODY_LOG = 1024
        private const val MAX_REQUEST_LOG = 800
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val SNAPSHOT_PATH = "s/"
    }
}
