package com.pulse.otel.utils.models

import kotlinx.serialization.Serializable

/**
 * Wrapper for API responses that follow the pattern
 * In case of error [error] field will be non null and data will be null and vice versa
 */
@Serializable
public class PulseApiResponse<T> internal constructor(
    public val data: T?,
    public val error: PulseApiError? = null,
)

@Serializable
public class PulseApiError internal constructor(
    public val code: String,
    public val message: String,
)
