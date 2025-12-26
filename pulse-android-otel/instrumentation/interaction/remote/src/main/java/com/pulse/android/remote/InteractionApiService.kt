package com.pulse.android.remote

import com.pulse.android.remote.models.InteractionConfig
import com.pulse.otel.utils.models.PulseApiResponse
import retrofit2.http.GET

public interface InteractionApiService {
    @GET(".")
    public suspend fun getInteractions(): PulseApiResponse<List<InteractionConfig>>
}
