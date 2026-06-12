package com.pulse.android.api.otel.models

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.metrics.data.Data
import io.opentelemetry.sdk.metrics.data.DoublePointData
import io.opentelemetry.sdk.metrics.data.ExponentialHistogramData
import io.opentelemetry.sdk.metrics.data.GaugeData
import io.opentelemetry.sdk.metrics.data.HistogramData
import io.opentelemetry.sdk.metrics.data.LongPointData
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.data.PointData
import io.opentelemetry.sdk.metrics.data.SumData
import io.opentelemetry.sdk.metrics.data.SummaryData
import io.opentelemetry.sdk.resources.Resource

/**
 * [MetricData] data with all parameters as properties which can be changed using [copy].
 */
public class PulseMetricData(
    private val delegate: MetricData,
    private val resource: Resource,
    private val name: String,
    private val description: String,
    private val data: Data<*>,
) : MetricData by delegate,
    PulseSignalData {
    override fun getAttributes(): Attributes {
        val builder = Attributes.builder()

        @Suppress("UNCHECKED_CAST")
        val points = (data as? Data<PointData>)?.points.orEmpty()
        for (point in points) {
            builder.putAll(point.attributes)
        }
        return builder.build()
    }

    override fun getResource(): Resource = resource

    override fun getName(): String = name

    override fun getDescription(): String = description

    override fun getData(): Data<*> = data

    override fun isEmpty(): Boolean = delegate.isEmpty

    override fun getDoubleGaugeData(): GaugeData<DoublePointData?>? = delegate.getDoubleGaugeData()

    override fun getLongGaugeData(): GaugeData<LongPointData?>? = delegate.getLongGaugeData()

    override fun getDoubleSumData(): SumData<DoublePointData?>? = delegate.getDoubleSumData()

    override fun getLongSumData(): SumData<LongPointData?>? = delegate.getLongSumData()

    override fun getSummaryData(): SummaryData? = delegate.getSummaryData()

    override fun getHistogramData(): HistogramData? = delegate.getHistogramData()

    override fun getExponentialHistogramData(): ExponentialHistogramData? = delegate.getExponentialHistogramData()

    override fun toString(): String = "PulseMetricData(name=$name, description=$description, resource=$resource, data=$data)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PulseMetricData) return false
        return delegate == other.delegate &&
            resource == other.resource &&
            name == other.name &&
            description == other.description &&
            data == other.data
    }

    override fun hashCode(): Int {
        var result = System.identityHashCode(delegate)
        result = 31 * result + resource.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + data.hashCode()
        return result
    }
}

/**
 * Creates a new instance of [PulseMetricData].
 * All params default to the corresponding value from receiver.
 */
public fun MetricData.copy(
    resource: Resource = this.resource,
    name: String = this.name,
    description: String = this.description,
    data: Data<*> = this.data,
): PulseMetricData =
    PulseMetricData(
        delegate = this,
        resource = resource,
        name = name,
        description = description,
        data = data,
    )
