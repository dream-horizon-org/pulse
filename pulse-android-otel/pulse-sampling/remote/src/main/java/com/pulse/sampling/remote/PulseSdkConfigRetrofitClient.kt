package com.pulse.sampling.remote

import com.pulse.utils.PulseNetworkingUtils
import com.pulse.utils.PulseOtelUtils
import com.pulse.utils.PulseSerialisationUtils
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

public class PulseSdkConfigRetrofitClient(
    private val url: String,
    private val okhttpClient: OkHttpClient,
    private val json: Json = PulseSerialisationUtils.jsonConfigForSerialisation,
) {
    private val retrofit: Retrofit by lazy {
        Retrofit
            .Builder()
            .baseUrl(PulseNetworkingUtils.extractBaseUrlWithSlash(url))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .client(buildOkHttpClient())
            .build()
    }

    public val apiService: PulseSdkConfigApiService by lazy {
        retrofit.create(PulseSdkConfigApiService::class.java)
    }

    private fun buildOkHttpClient(): OkHttpClient =
        if (okhttpClient.cache != null && PulseOtelUtils.isDebug()) {
            val builder = okhttpClient.newBuilder()
            builder.eventListener(
                object : EventListener() {
                    override fun cacheConditionalHit(
                        call: Call,
                        cachedResponse: Response,
                    ) {
                        super.cacheConditionalHit(call, cachedResponse)
                        PulseOtelUtils.logDebug(TAG) {
                            "checking cache for url = ${call.request().url}"
                        }
                    }

                    override fun cacheHit(
                        call: Call,
                        response: Response,
                    ) {
                        super.cacheHit(call, response)
                        PulseOtelUtils.logDebug(TAG) {
                            "cacheHit for url = ${call.request().url}"
                        }
                    }

                    override fun cacheMiss(call: Call) {
                        super.cacheMiss(call)
                        PulseOtelUtils.logDebug(TAG) {
                            "cacheMiss for url = ${call.request().url}"
                        }
                    }
                },
            )
            builder.build()
        } else {
            okhttpClient
        }

    private companion object {
        private const val TAG = "PulseSdkConfigRetrofitClient"
    }

    public fun newInstance(url: String): PulseSdkConfigRetrofitClient =
        PulseSdkConfigRetrofitClient(
            url = url,
            okhttpClient = okhttpClient,
            json = json,
        )
}
