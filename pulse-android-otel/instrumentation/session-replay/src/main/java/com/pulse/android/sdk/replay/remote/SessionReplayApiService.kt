package com.pulse.android.sdk.replay.remote

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit API for session replay snapshot uploads.
 */
internal interface SessionReplayApiService {
    @POST("s/")
    fun sendBatch(@Body body: RequestBody): Call<ResponseBody>
}
