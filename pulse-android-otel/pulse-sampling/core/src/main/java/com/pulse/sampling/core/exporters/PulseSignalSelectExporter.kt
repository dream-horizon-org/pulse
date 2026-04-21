package com.pulse.sampling.core.exporters

import com.pulse.sampling.core.PulseSignalMatcher
import com.pulse.sampling.core.PulseSignalsAttrMatcher
import com.pulse.sampling.models.PulseSdkName
import com.pulse.sampling.models.PulseSignalScope
import com.pulse.sampling.models.matchers.PulseSignalMatchCondition
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter

public class PulseSignalSelectExporter internal constructor(
    private val sdkName: PulseSdkName,
    private val signalMatcher: PulseSignalMatcher = PulseSignalsAttrMatcher(),
) {
    public inner class SelectedSpanExporter(
        private val spanMap: List<Pair<PulseSignalMatchCondition, SpanExporter>>,
    ) : SpanExporter {
        private val spanMapReversed by lazy { spanMap.asReversed() }

        override fun export(spans: Collection<SpanData>): CompletableResultCode =
            exportSignals(
                signals = spans,
                map = spanMapReversed,
                signalScope = PulseSignalScope.TRACES,
                signalValuesProvider = SpanData::toSignalValues,
                exportBatch = { exporter, batch -> exporter.export(batch) },
            )

        override fun flush(): CompletableResultCode =
            CompletableResultCode.ofAll(
                spanMapReversed.map { (_, exporter) ->
                    exporter.flush()
                },
            )

        override fun shutdown(): CompletableResultCode =
            CompletableResultCode.ofAll(
                spanMapReversed.map { (_, exporter) ->
                    exporter.shutdown()
                },
            )
    }

    public inner class SelectedLogExporter(
        private val logMap: List<Pair<PulseSignalMatchCondition, LogRecordExporter>>,
    ) : LogRecordExporter {
        private val logMapReversed by lazy { logMap.asReversed() }

        override fun export(logs: Collection<LogRecordData>): CompletableResultCode =
            exportSignals(
                signals = logs,
                map = logMapReversed,
                signalScope = PulseSignalScope.LOGS,
                signalValuesProvider = LogRecordData::toSignalValues,
                exportBatch = { exporter, batch -> exporter.export(batch) },
            )

        override fun flush(): CompletableResultCode =
            CompletableResultCode.ofAll(
                logMapReversed.map { (_, exporter) ->
                    exporter.flush()
                },
            )

        override fun shutdown(): CompletableResultCode =
            CompletableResultCode.ofAll(
                logMapReversed.map { (_, exporter) ->
                    exporter.shutdown()
                },
            )

        override fun close() {
            logMapReversed.forEach { (_, exporter) ->
                exporter.close()
            }
        }
    }

    private inline fun <T, E> exportSignals(
        signals: Collection<T>,
        map: List<Pair<PulseSignalMatchCondition, E>>,
        signalScope: PulseSignalScope,
        signalValuesProvider: T.() -> SignalMatchValues,
        exportBatch: (E, Collection<T>) -> CompletableResultCode,
    ): CompletableResultCode {
        val signalsByExporter =
            signals
                .mapNotNull { signal ->
                    val (signalName, signalPropsMap) = signal.signalValuesProvider()

                    val matchingExporter =
                        map
                            .firstOrNull { (matchCondition, _) ->
                                signalMatcher.matches(
                                    signalScope,
                                    signalName,
                                    signalPropsMap,
                                    matchCondition,
                                    sdkName,
                                )
                            }?.second

                    matchingExporter?.let { exporter -> exporter to signal }
                }.groupBy({ it.first }, { it.second })

        val results =
            signalsByExporter.map { (exporter, signalsBatch) ->
                exportBatch(exporter, signalsBatch)
            }

        return CompletableResultCode.ofAll(results)
    }
}

public fun PulseSignalSelectExporter(sdkName: PulseSdkName): PulseSignalSelectExporter =
    PulseSignalSelectExporter(sdkName, PulseSignalsAttrMatcher())
