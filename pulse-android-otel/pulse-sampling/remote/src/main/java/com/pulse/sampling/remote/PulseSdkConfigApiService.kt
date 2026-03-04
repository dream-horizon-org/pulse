package com.pulse.sampling.remote

import com.pulse.sampling.models.PulseSdkConfig
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.Url

public interface PulseSdkConfigApiService {
    @GET
    public suspend fun getConfig(
        @Url fullFileUrl: String,
        @HeaderMap headers: Map<String, String>,
    ): PulseSdkConfig
}
