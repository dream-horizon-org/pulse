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

        return builder.build()
    }

    private companion object {
        private const val MAX_CACHE_SIZE_BYTE: Long = 10 * 1024 * 1024
    }

    public fun newInstance(url: String): PulseSdkConfigRetrofitClient = PulseSdkConfigRetrofitClient(url, cacheDir, json, okhttpClient)

    init {
        assert(!cacheDir.isFile) {
            "cacheDir = ${cacheDir.absolutePath} is not directory"
        }
    }
}
