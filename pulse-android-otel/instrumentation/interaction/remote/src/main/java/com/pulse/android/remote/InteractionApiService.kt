package com.pulse.android.remote

import com.pulse.android.remote.models.InteractionConfig
import retrofit2.http.GET

public interface InteractionApiService {
    @GET
    public suspend fun getInteractions(): List<InteractionConfig>
}
