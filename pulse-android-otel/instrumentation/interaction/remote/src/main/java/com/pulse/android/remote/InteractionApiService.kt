package com.pulse.android.remote

import com.pulse.android.remote.models.InteractionConfig
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.Url

public interface InteractionApiService {
    @GET
    public suspend fun getInteractions(
        @Url fullFileUrl: String,
        @HeaderMap headers: Map<String, String>,
    ): List<InteractionConfig>
}
