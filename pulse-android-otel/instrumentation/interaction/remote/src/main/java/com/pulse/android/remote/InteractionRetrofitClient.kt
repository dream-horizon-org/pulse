package com.pulse.android.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

public class InteractionRetrofitClient(
    private val url: String,
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
    private val okhttpClient: OkHttpClient = OkHttpClient.Builder().build(),
    private val headers: Map<String, String> = emptyMap(),
) {
    private val retrofit: Retrofit by lazy {
        val clientBuilder = okhttpClient.newBuilder()
        if (headers.isNotEmpty()) {
            clientBuilder.addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                headers.forEach { (key, value) ->
                    requestBuilder.header(key, value)
                }
                chain.proceed(requestBuilder.build())
            }
        }
        Retrofit
            .Builder()
            .baseUrl(url)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .client(clientBuilder.build())
            .build()
    }

    public val apiService: InteractionApiService by lazy {
        retrofit.create(InteractionApiService::class.java)
    }

    public fun newInstance(
        url: String,
        headers: Map<String, String> = this.headers,
    ): InteractionRetrofitClient = InteractionRetrofitClient(url, json, okhttpClient, headers)
}
