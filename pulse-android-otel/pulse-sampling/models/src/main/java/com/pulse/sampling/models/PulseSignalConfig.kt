package com.pulse.sampling.models

import androidx.annotation.Keep
import com.pulse.sampling.models.matchers.PulseSignalMatchCondition
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

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
    /**
     * Metrics to derive based on signal matching and target. See [PulseMetricsToAddEntry]
     */
    @SerialName("metricsToAdd")
    public val metricsToAdd: Collection<PulseMetricsToAddEntry> = emptySet(),
)

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
    /**
     * Name of the metric to add
     */
    @SerialName("name")
    public val name: String,
    /**
     * Target value from the signal is used as the data point
     */
    @SerialName("target")
    public val target: PulseMetricsToAddTarget,
    /**
     * Condition to match the signal
     */
    @SerialName("condition")
    public val condition: PulseSignalMatchCondition = PulseSignalMatchCondition(),
    /**
     * The kind of OTel instrument to create. See [PulseMetricsType] for all the supported metric data
     */
    @SerialName("type")
    public val type: PulseMetricsType,
    /**
     * Optional list of conditions specifying which signal attributes to attach to the emitted metric data point
     */
    @SerialName("attributesToPick")
    public val attributesToPick: Collection<PulseSignalMatchCondition> = emptySet(),
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
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
@Serializable
public sealed class PulseMetricsToAddTarget protected constructor() {
    /**
     * Name of the signal will be used as data to record the metric
     */
    @Serializable
    @SerialName("name")
    public class Name(
        @SerialName("type")
        public val type: String,
    ) : PulseMetricsToAddTarget()

    /**
     * Attribute of the signal matched by [condition] will be used as data to record the metric
     */
    @Serializable
    @SerialName("attribute")
    public class Attribute internal constructor(
        @SerialName("type")
        public val type: String,
        @SerialName("condition")
        public val condition: PulseSignalMatchCondition,
        @SerialName("shouldAddPropNameAsSuffix")
        /**
         * When true final name of the metric will be <name>.<key_name_of_the_prop_matched>, where name is from [PulseMetricsToAddEntry.name]
         */
        public val shouldAddPropNameAsSuffix: Boolean = false,
    ) : PulseMetricsToAddTarget()
}

@Keep
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
@Serializable
public sealed class PulseMetricsType {
    @Serializable
    @SerialName("counter")
    public class Counter internal constructor(
        @SerialName("type")
        public val type: String,
    ) : PulseMetricsType()

    @Serializable
    @SerialName("gauge")
    public class Gauge internal constructor(
        @SerialName("type")
        public val type: String,
        @SerialName("isFraction")
        public val isFraction: Boolean,
    ) : PulseMetricsType()

    @Serializable
    @SerialName("histogram")
    public class Histogram internal constructor(
        @SerialName("type")
        public val type: String,
        @SerialName("bucket")
        public val bucket: List<Double>?,
        @SerialName("isFraction")
        public val isFraction: Boolean,
    ) : PulseMetricsType()

    @Serializable
    @SerialName("sum")
    public class Sum internal constructor(
        @SerialName("type")
        public val type: String,
        @SerialName("isFraction")
        public val isFraction: Boolean,
        @SerialName("isMonotonic")
        public val isMonotonic: Boolean,
    ) : PulseMetricsType()
}
