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
) {
    private val retrofit: Retrofit by lazy {
        Retrofit
            .Builder()
            .baseUrl(url)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .client(okhttpClient)
            .build()
    }

    public val apiService: InteractionApiService by lazy {
        retrofit.create(InteractionApiService::class.java)
    }

    public fun newInstance(url: String): InteractionRetrofitClient = InteractionRetrofitClient(url, json, okhttpClient)
}
