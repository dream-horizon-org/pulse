// Android-style session sampling: one random draw per SDK init, export-time filter
// (see pulse-android-otel PulseSamplingSignalProcessors.sampleSession).

import type { Attributes } from "@opentelemetry/api";
import type { ReadableSpan } from "@opentelemetry/sdk-trace-web";
import type { ReadableLogRecord } from "@opentelemetry/sdk-logs";
import type { ResourceMetrics } from "@opentelemetry/sdk-metrics";

import type { PulseSdkConfig, PulseSdkName } from "../types/remote-config";
import type { PulseSignalsToSampleEntry } from "../types/remote-config";
import type { PulseSignalScope } from "../types/sampling";
import { pulseSignalConditionMatches } from "../utils/sampling-signal-match";
import {
  clamp01,
  getCriticalAlwaysSendConditions,
  logRecordBodyAsString,
  resolveSessionSamplingRate,
} from "../utils/session-sampling-rate";

export class ExportSamplingGate {
  private readonly sessionRandomValue: number;
  private readonly shouldSampleThisSession: boolean;
  private readonly signalsToSample: PulseSignalsToSampleEntry[];

  constructor(
    private readonly config: PulseSdkConfig,
    private readonly sdkName: PulseSdkName,
  ) {
    this.sessionRandomValue = Math.random();
    const sessionRate = resolveSessionSamplingRate(config, sdkName);
    this.shouldSampleThisSession = this.sessionRandomValue < sessionRate;
    this.signalsToSample = config.sampling.signalsToSample ?? [];
  }

  private shouldSampleByRate(rate: number): boolean {
    return this.sessionRandomValue < clamp01(rate);
  }

  private isAlwaysSend(
    scope: PulseSignalScope,
    signalName: string,
    attrs: Attributes | Readonly<Attributes> | undefined,
  ): boolean {
    for (const c of getCriticalAlwaysSendConditions(this.config)) {
      if (
        pulseSignalConditionMatches(scope, signalName, attrs, c, this.sdkName)
      )
        return true;
    }
    return false;
  }

  /** Android: matched signalsToSample entry uses its rate; else session decision. */
  shouldExportSignal(
    scope: PulseSignalScope,
    signalName: string,
    attrs: Attributes | Readonly<Attributes> | undefined,
  ): boolean {
    if (this.isAlwaysSend(scope, signalName, attrs)) return true;

    const matched = this.signalsToSample.find((entry) =>
      pulseSignalConditionMatches(
        scope,
        signalName,
        attrs,
        entry.condition,
        this.sdkName,
      ),
    );
    if (matched !== undefined) {
      return this.shouldSampleByRate(matched.sampleRate);
    }
    return this.shouldSampleThisSession;
  }

  filterReadableSpans(spans: ReadableSpan[]): ReadableSpan[] {
    return spans.filter((s) =>
      this.shouldExportSignal("TRACES", s.name, s.attributes),
    );
  }

  filterReadableLogs(logs: ReadableLogRecord[]): ReadableLogRecord[] {
    return logs.filter((l) => {
      const name = logRecordBodyAsString(l.body);
      return this.shouldExportSignal(
        "LOGS",
        name,
        l.attributes as unknown as Attributes,
      );
    });
  }

  filterResourceMetrics(rm: ResourceMetrics): ResourceMetrics {
    const scopeMetrics = rm.scopeMetrics
      .map((sm) => ({
        ...sm,
        metrics: sm.metrics.filter((m) =>
          // Android MetricData.toSignalValues uses empty attributes for match.
          this.shouldExportSignal("METRICS", m.descriptor.name, undefined),
        ),
      }))
      .filter((sm) => sm.metrics.length > 0);
    return { ...rm, scopeMetrics };
  }
}
