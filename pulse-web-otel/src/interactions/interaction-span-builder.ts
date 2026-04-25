import {
  SpanKind,
  SpanStatusCode,
  ROOT_CONTEXT,
  type Tracer,
} from "@opentelemetry/api";

import { PulseWebSemconv } from "../semconv";
import { INTERACTION_PROP_KEYS } from "../constants/interactions/interaction-prop-keys";
import { INTERACTION_TIME_CATEGORY } from "../constants/interactions/interaction-time-category";
import type {
  InteractionLocalEvent,
  PulseInteraction,
} from "../types/interactions/interaction-runtime";

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function toLocalEvents(value: unknown): InteractionLocalEvent[] {
  if (!Array.isArray(value)) return [];
  return value.filter((item): item is InteractionLocalEvent => {
    if (!isRecord(item)) return false;
    return (
      typeof item["name"] === "string" && typeof item["timeInNano"] === "number"
    );
  });
}

function asFiniteNumber(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function asString(value: unknown): string | null {
  return typeof value === "string" ? value : null;
}

export class InteractionSpanBuilder {
  constructor(private readonly tracer: Tracer) {}

  emitInteraction(interaction: PulseInteraction): void {
    const p = interaction.props;
    const nameFromProps = asString(p[INTERACTION_PROP_KEYS.NAME]);
    const configId = asString(p[INTERACTION_PROP_KEYS.CONFIG_ID]);
    const completeTimeNs = asFiniteNumber(
      p[INTERACTION_PROP_KEYS.TIME_TO_COMPLETE_IN_NANO],
    );
    const apdex = asFiniteNumber(p[INTERACTION_PROP_KEYS.APDEX_SCORE]);
    const category = asString(p[INTERACTION_PROP_KEYS.USER_CATEGORY]);
    const isError = p[INTERACTION_PROP_KEYS.IS_ERROR] === true;
    const errorType = asString(p[INTERACTION_PROP_KEYS.ERROR_TYPE]) ?? "";
    const errorMessage = asString(p[INTERACTION_PROP_KEYS.ERROR_MESSAGE]) ?? "";
    const localEvents = toLocalEvents(p[INTERACTION_PROP_KEYS.LOCAL_EVENTS]);

    const safeDurationNs = completeTimeNs ?? 0;
    const firstEventNs = localEvents[0]?.timeInNano;
    const lastEventNs = localEvents[localEvents.length - 1]?.timeInNano;
    const nowMs = Date.now();
    const startMs =
      firstEventNs !== undefined
        ? Math.round(firstEventNs / 1_000_000)
        : nowMs - Math.max(1, Math.round(safeDurationNs / 1_000_000));
    const endMs =
      lastEventNs !== undefined
        ? Math.round(lastEventNs / 1_000_000)
        : Math.max(startMs + 1, nowMs);

    const span = this.tracer.startSpan(
      interaction.name,
      {
        kind: SpanKind.INTERNAL,
        startTime: startMs,
      },
      ROOT_CONTEXT,
    );

    span.setAttributes({
      [PulseWebSemconv.AttributeKey.PULSE_TYPE]:
        PulseWebSemconv.PulseType.INTERACTION,
      [PulseWebSemconv.InteractionAttributeKey.ID]: interaction.id,
      [PulseWebSemconv.InteractionAttributeKey.NAME]:
        nameFromProps ?? interaction.name,
      [PulseWebSemconv.InteractionAttributeKey.CONFIG_ID]: configId ?? "",
      [PulseWebSemconv.InteractionAttributeKey.CONFIG_NAME]: interaction.name,
      [PulseWebSemconv.InteractionAttributeKey.COMPLETE_TIME]: safeDurationNs,
      [PulseWebSemconv.InteractionAttributeKey.APDEX_SCORE]: isError
        ? 0.0
        : (apdex ?? 0.0),
      [PulseWebSemconv.InteractionAttributeKey.USER_CATEGORY]: isError
        ? INTERACTION_TIME_CATEGORY.POOR
        : (category ?? INTERACTION_TIME_CATEGORY.POOR),
      [PulseWebSemconv.InteractionAttributeKey.IS_ERROR]: isError,
      ...(isError
        ? {
            [PulseWebSemconv.InteractionAttributeKey.ERROR_TYPE]: errorType,
            [PulseWebSemconv.InteractionAttributeKey.ERROR_MESSAGE]:
              errorMessage,
          }
        : {}),
    });

    for (const ev of localEvents) {
      span.addEvent(ev.name, ev.props, Math.round(ev.timeInNano / 1_000_000));
    }

    if (isError) {
      span.setStatus({
        code: SpanStatusCode.ERROR,
        message: errorMessage || errorType || "interaction error",
      });
    } else {
      span.setStatus({ code: SpanStatusCode.OK });
    }

    span.end(endMs);
  }
}
