package com.pulse.android.sdk.replay.remote

import com.pulse.utils.PulseNetworkingUtils
import com.pulse.utils.PulseOtelUtils
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import java.io.IOException

/**
 * Sends session replay snapshot batches to the backend API.
 * Uses the same common OkHttpClient as interaction and sampling
 * ([PulseNetworkingUtils.okHttpClient]); no client is passed from outside.
 *
 * No client-side retries: server may signal retry via Retry-After headers or config.
 *
 * API contract:
 * - POST to [baseUrl]/s/
 * - Content-Type: application/json
 * - Body: JSON array of snapshot events (envelope already contains event, project_id, user_id, properties).
 */
public class SessionReplayApiClient(
    private val baseUrl: String,
) {
    private val okHttpClient
        get() = PulseNetworkingUtils.okHttpClient

    private val apiService: SessionReplayApiService by lazy {
        Retrofit
            .Builder()
            .baseUrl(PulseNetworkingUtils.endWithSlash(baseUrl))
            .client(okHttpClient)
            .build()
            .create(SessionReplayApiService::class.java)
    }

    /**
     * Sends one or more snapshot envelopes to the backend in a single POST.
     * [payload] may be a single JSON object or a JSON array of objects (batch).
     *
     * @param payload Single envelope (object) or pre-batched array string.
     * @return [Result.success] on 2xx, [Result.failure] on network error, 4xx, 5xx, or timeout.
     */
    public fun sendBatch(payload: String): Result<Unit> =
        runCatching {
            val body = if (payload.trimStart().startsWith("[")) payload else "[$payload]"
            val requestBody = body.toRequestBody(JSON_MEDIA_TYPE)
            val response = apiService.sendBatch(requestBody).execute()
            if (!response.isSuccessful) {
                val responseBody =
                    response
                        .errorBody()
                        ?.string()
                        .orEmpty()
                        .take(MAX_ERROR_BODY_LOG)
                val msg = response.message()
                PulseOtelUtils.logError(REPLAY_LOG_TAG) {
                    "Session replay API error: ${response.code()} $msg. Body: $responseBody"
                }
                if (body.length <= MAX_REQUEST_LOG) {
                    PulseOtelUtils.logError(REPLAY_LOG_TAG) { "Request payload: $body" }
                } else {
                    PulseOtelUtils.logError(REPLAY_LOG_TAG) {
                        "Request payload (first $MAX_REQUEST_LOG chars): ${body.take(MAX_REQUEST_LOG)}..."
                    }
                }
                throw HttpException(response.code(), msg, responseBody)
            }
        }

    private class HttpException(
        val code: Int,
        message: String,
        val responseBody: String = "",
    ) : IOException("HTTP $code: $message${if (responseBody.isNotEmpty()) " — $responseBody" else ""}")

    private companion object {
        private const val REPLAY_LOG_TAG = "SessionReplay"
        private const val MAX_ERROR_BODY_LOG = 1024
        private const val MAX_REQUEST_LOG = 800
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
