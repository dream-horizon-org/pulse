/**
 * Android parity: InteractionEventsTracker
 * (pulse-android-otel/.../InteractionEventsTracker.kt).
 */
import type { InteractionConfig } from "./interaction-models";
import {
  buildPulseInteraction,
  matchInteractionSequence,
} from "./interaction-sequence-matcher";
import type {
  InteractionLocalEvent,
  InteractionRunningStatus,
  InteractionTrackerCallbacks,
  PulseInteraction,
} from "../types/interactions/interaction-runtime";
import { localMatchesAnyEvent } from "../utils/interactions/event-matching";
import {
  globalBlacklistAsEvents,
  localEventMatchesFirstConfigEvent,
  sortedInsertLocalEvent,
} from "../utils/interactions/interaction-events";
import { randomInteractionId } from "../utils/interactions/interaction-id";

export class InteractionTracker {
  private readonly localEvents: InteractionLocalEvent[] = [];
  private readonly localMarkers: InteractionLocalEvent[] = [];
  private interactionClosed = true;
  private timer: ReturnType<typeof setTimeout> | undefined;
  private current: InteractionRunningStatus[] = [
    { kind: "no_ongoing", old: null },
  ];

  constructor(
    private readonly interactionConfig: InteractionConfig,
    private readonly callbacks: InteractionTrackerCallbacks = {},
  ) {}

  getConfig(): InteractionConfig {
    return this.interactionConfig;
  }

  getStatuses(): InteractionRunningStatus[] {
    return this.current;
  }

  getLocalEvents(): readonly InteractionLocalEvent[] {
    return this.localEvents;
  }

  addMarker(event: InteractionLocalEvent): void {
    this.localMarkers.push(event);
  }

  checkAndAdd(event: InteractionLocalEvent): void {
    const globalSynthetic = globalBlacklistAsEvents(
      this.interactionConfig.globalBlacklistedEvents,
    );
    const relevant =
      localMatchesAnyEvent(event, this.interactionConfig.events) ||
      localMatchesAnyEvent(event, globalSynthetic);
    if (!relevant) {
      return;
    }

    sortedInsertLocalEvent(this.localEvents, event);

    const interactionId = this.nextInteractionIdForMatch();

    const seqResult = matchInteractionSequence(
      interactionId,
      this.localEvents,
      this.localMarkers,
      this.interactionConfig,
    );

    if (seqResult == null) {
      this.interactionClosed = true;
      return;
    }

    const { shouldTakeFirstEvent, shouldResetList, interactionStatus } =
      seqResult;

    let oldStatus: InteractionRunningStatus | null = null;
    let newStatus: InteractionRunningStatus = interactionStatus;

    if (shouldResetList) {
      if (
        shouldTakeFirstEvent &&
        this.localEvents.length > 0 &&
        localEventMatchesFirstConfigEvent(
          this.localEvents[this.localEvents.length - 1]!,
          this.interactionConfig,
        )
      ) {
        const lastEvent = this.localEvents[this.localEvents.length - 1]!;
        if (interactionStatus.kind !== "ongoing") {
          throw new Error(
            "Expected OngoingMatch for sequence violation restart",
          );
        }
        if (interactionStatus.interaction == null) {
          throw new Error("Expected interaction payload on sequence violation");
        }
        oldStatus = interactionStatus;
        this.clearStates();
        this.localEvents.push(lastEvent);
        newStatus = {
          kind: "ongoing",
          index: interactionStatus.index,
          interactionId: randomInteractionId(),
          interactionConfig: this.interactionConfig,
          interaction: null,
        };
        this.interactionClosed = false;
      } else {
        this.interactionClosed = true;
        this.clearStates();
        oldStatus = null;
        newStatus = interactionStatus;
      }
    } else {
      oldStatus = null;
      newStatus = interactionStatus;
    }

    this.current = oldStatus != null ? [oldStatus, newStatus] : [newStatus];
    this.emitTerminals(oldStatus, newStatus);
    this.scheduleTimer(newStatus);
    this.callbacks.onStatusesEmitted?.(this.current);
  }

  destroy(): void {
    this.clearTimer();
    this.clearStates();
    this.interactionClosed = true;
    this.current = [{ kind: "no_ongoing", old: null }];
  }

  private nextInteractionIdForMatch(): string {
    if (this.interactionClosed) {
      this.interactionClosed = false;
      return randomInteractionId();
    }
    const last = this.current[this.current.length - 1];
    if (last?.kind === "ongoing") {
      return last.interactionId;
    }
    return randomInteractionId();
  }

  private createErrorInteraction(
    status: Extract<InteractionRunningStatus, { kind: "ongoing" }>,
    localEvents: readonly InteractionLocalEvent[],
    localMarkers: readonly InteractionLocalEvent[],
    errorType: "timeout" | "sequence_violation",
  ): PulseInteraction {
    const err =
      errorType === "timeout"
        ? {
            type: "timeout" as const,
            timeoutExpectedEventName:
              this.interactionConfig.events[status.index + 1]?.name ?? null,
          }
        : {
            type: "sequence_violation" as const,
            sequenceViolationExpectedEventName:
              this.interactionConfig.events[status.index + 1]?.name ?? null,
            sequenceViolationReceivedEventName:
              localEvents[localEvents.length - 1]?.name ?? null,
          };
    return buildPulseInteraction(
      status.interactionId,
      this.interactionConfig,
      localEvents,
      localMarkers,
      err,
    );
  }

  private clearStates(): void {
    this.localEvents.length = 0;
    this.localMarkers.length = 0;
  }

  private clearTimer(): void {
    if (this.timer !== undefined) {
      clearTimeout(this.timer);
      this.timer = undefined;
    }
  }

  private scheduleTimer(newValue: InteractionRunningStatus): void {
    this.clearTimer();
    if (newValue.kind !== "ongoing" || newValue.interaction != null) {
      return;
    }
    const delayMs = this.interactionConfig.thresholdInMs + 10;
    this.timer = setTimeout(() => this.onInterStepTimeout(), delayMs);
  }

  private onInterStepTimeout(): void {
    this.timer = undefined;
    this.interactionClosed = true;
    const last = this.current[this.current.length - 1];
    if (last?.kind !== "ongoing" || last.interaction != null) {
      return;
    }
    const errInteraction = this.createErrorInteraction(
      last,
      this.localEvents,
      this.localMarkers,
      "timeout",
    );
    const next: InteractionRunningStatus = {
      kind: "ongoing",
      index: last.index,
      interactionId: last.interactionId,
      interactionConfig: this.interactionConfig,
      interaction: errInteraction,
    };
    this.current = [next];
    this.emitTerminals(null, next);
    this.callbacks.onStatusesEmitted?.(this.current);
    this.clearStates();
  }

  private emitTerminals(
    oldValue: InteractionRunningStatus | null,
    newValue: InteractionRunningStatus,
  ): void {
    if (oldValue?.kind === "ongoing" && oldValue.interaction != null) {
      this.callbacks.onInteractionTerminal?.(oldValue.interaction);
    }
    if (newValue.kind === "ongoing" && newValue.interaction != null) {
      this.callbacks.onInteractionTerminal?.(newValue.interaction);
    }
  }
}
