package com.pulse.sampling.models

import androidx.annotation.Keep
import com.pulse.sampling.models.matchers.PulseSignalMatchCondition
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
public class PulseSignalConfig internal constructor(
    @SerialName("scheduleDurationMs")
    public val scheduleDurationMs: Long,
    @SerialName("logsCollectorUrl")
    public val logsCollectorUrl: String,
    @SerialName("metricCollectorUrl")
    public val metricCollectorUrl: String,
    @SerialName("spanCollectorUrl")
    public val spanCollectorUrl: String,
    @SerialName("attributesToDrop")
    public val attributesToDrop: List<PulseSignalMatchCondition>,
    @SerialName("attributesToAdd")
    public val attributesToAdd: List<PulseAttributesToAddEntry> = emptyList(),
    @SerialName("filters")
    public val filters: PulseSignalFilter,
)

@Keep
@Serializable
public class PulseSignalFilter internal constructor(
    @SerialName("mode")
    public val mode: PulseSignalFilterMode,
    @SerialName("values")
    public val values: List<PulseSignalMatchCondition>,
)

@Keep
@Serializable
public enum class PulseSignalFilterMode {
    @SerialName("blacklist")
    BLACKLIST,

    @SerialName("whitelist")
    WHITELIST,
}

@Keep
@Serializable
public class PulseProp internal constructor(
    @SerialName("name")
    public val name: String,
    @SerialName("value")
    public val value: String?,
)

@Keep
@Serializable
public enum class PulseAttributeType {
    @SerialName("string")
    STRING,

    @SerialName("boolean")
    BOOLEAN,

    @SerialName("long")
    LONG,

    @SerialName("double")
    DOUBLE,

    @SerialName("string_array")
    STRING_ARRAY,

    @SerialName("boolean_array")
    BOOLEAN_ARRAY,

    @SerialName("long_array")
    LONG_ARRAY,

    @SerialName("double_array")
    DOUBLE_ARRAY,
}

@Keep
@Serializable
public class PulseAttributeValue internal constructor(
    @SerialName("name")
    public val name: String,
    @SerialName("value")
    public val value: String,
    @SerialName("type")
    public val type: PulseAttributeType,
)

@Keep
@Serializable
public class PulseAttributesToAddEntry internal constructor(
    @SerialName("values")
    public val values: List<PulseAttributeValue>,
    @SerialName("matcher")
    public val matcher: PulseSignalMatchCondition,
)
