package com.pulse.sampling.models

import androidx.annotation.Keep
import com.pulse.sampling.models.matchers.PulseSignalMatchCondition
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
public class PulseSignalConfig internal constructor(
    @SerialName("logsCollectorUrl")
    public val logsCollectorUrl: String? = null,
    @SerialName("metricCollectorUrl")
    public val metricCollectorUrl: String? = null,
    @SerialName("spanCollectorUrl")
    public val spanCollectorUrl: String? = null,
    @SerialName("customEventCollectorUrl")
    public val customEventCollectorUrl: String? = logsCollectorUrl,
    @SerialName("scheduleDurationMs")
    public val scheduleDurationMs: Long = 5000L,
    @SerialName("attributesToDrop")
    public val attributesToDrop: Collection<PulseAttributesToDropEntry> = emptySet(),
    @SerialName("attributesToAdd")
    public val attributesToAdd: Collection<PulseAttributesToAddEntry> = emptySet(),
    @SerialName("metricsToAdd")
    public val metricsToAdd: Collection<PulseMetricsToAddEntry> = emptySet(),
    @SerialName("filters")
    public val filters: PulseSignalFilter = PulseSignalFilter(),
)

@Keep
@Serializable
public class PulseSignalFilter internal constructor(
    @SerialName("mode")
    public val mode: PulseSignalFilterMode = PulseSignalFilterMode.BLACKLIST,
    @SerialName("values")
    public val values: Collection<PulseSignalMatchCondition> = emptySet(),
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
public class PulseProp(
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
    public val name: String = "",
    @SerialName("value")
    public val value: String = "",
    @SerialName("type")
    public val type: PulseAttributeType = PulseAttributeType.STRING,
)

@Keep
@Serializable
public class PulseAttributesToAddEntry internal constructor(
    @SerialName("values")
    public val values: Collection<PulseAttributeValue>,
    @SerialName("condition")
    public val condition: PulseSignalMatchCondition,
)

@Keep
@Serializable
public class PulseMetricsToAddEntry internal constructor(
    @SerialName("name")
    public val name: String,
    @SerialName("target")
    public val target: PulseMetricsToAddTarget,
    public val values: Collection<PulseAttributeValue> = emptySet(),
    @SerialName("condition")
    public val condition: PulseSignalMatchCondition = PulseSignalMatchCondition(),
    @SerialName("type")
    public val type: PulseMetricsType,
)

@Keep
@Serializable
public class PulseAttributesToDropEntry internal constructor(
    /**
     * List of regex entries which will dropped from the signal
     */
    @SerialName("values")
    public val values: Collection<String> = emptySet(),
    /**
     * Condition which should be matched for [values] to be dropped
     */
    @SerialName("condition")
    public val condition: PulseSignalMatchCondition = PulseSignalMatchCondition(),
)

@Keep
@Serializable
public sealed class PulseMetricsToAddTarget protected constructor() {
    public object Name : PulseMetricsToAddTarget()

    public class Attribute internal constructor(
        public val matcher: PulseSignalMatchCondition,
    ) : PulseMetricsToAddTarget()
}

@Keep
@Serializable
public enum class PulseMetricsType {
    @SerialName("counter")
    COUNTER,

    @SerialName("gauge")
    GAUGE,

    @SerialName("histogram")
    HISTOGRAM,

    @SerialName("sum")
    SUM,
    ;
}
