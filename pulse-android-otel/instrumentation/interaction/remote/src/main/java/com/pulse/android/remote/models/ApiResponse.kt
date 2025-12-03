package com.pulse.android.remote.models

import kotlinx.serialization.Serializable

/**
 * Wrapper for API responses that follow the pattern:
 * {
 *   "data": [...],
 *   "error": null
 * }
 */
@Serializable
public class ApiResponse<T> internal constructor(
    public val data: T,
    public val error: String? = null,
)
