package com.pulse.sampling.remote

import com.pulse.sampling.models.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File

public class PulseSdkConfigRetrofitClient(
    private val url: String,
    private val cacheDir: File,
    private val json: Json =
        Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = !BuildConfig.DEBUG
            prettyPrint = BuildConfig.DEBUG
            isLenient = !BuildConfig.DEBUG
            allowSpecialFloatingPointValues = true
            useAlternativeNames = true
        },
    private val okhttpClient: OkHttpClient? = null,
    private val headers: Map<String, String> = emptyMap(),
) {
    private val retrofit: Retrofit by lazy {
        Retrofit
            .Builder()
            .baseUrl(url)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .client(buildOkHttpClient())
            .build()
    }

    public val apiService: PulseSdkConfigApiService by lazy {
        retrofit.create(PulseSdkConfigApiService::class.java)
    }

    private fun buildOkHttpClient(): OkHttpClient {
        val builder = okhttpClient?.newBuilder() ?: OkHttpClient.Builder()
        if (okhttpClient?.cache == null) {
            val cache = Cache(cacheDir, MAX_CACHE_SIZE_BYTE)
            builder.cache(cache)
        }

        if (headers.isNotEmpty()) {
            builder.addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                headers.forEach { (key, value) ->
                    requestBuilder.header(key, value)
                }
                chain.proceed(requestBuilder.build())
            }
        }

        return builder.build()
    }

    private companion object {
        private const val MAX_CACHE_SIZE_BYTE: Long = 10 * 1024 * 1024
    }

    public fun newInstance(
        url: String,
        headers: Map<String, String> = this.headers,
    ): PulseSdkConfigRetrofitClient = PulseSdkConfigRetrofitClient(url, cacheDir, json, okhttpClient, headers)

    init {
        assert(!cacheDir.isFile) {
            "cacheDir = ${cacheDir.absolutePath} is not directory"
        }
    }
}
