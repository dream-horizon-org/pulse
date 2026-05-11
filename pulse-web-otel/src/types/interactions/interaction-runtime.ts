import type { InteractionConfig } from "../../interactions/interaction-models";

/** Epoch nanoseconds (Android InteractionLocalEvent.timeInNano). */
export interface InteractionLocalEvent {
  name: string;
  timeInNano: number;
  props?: Record<string, string>;
}

export type InteractionErrorCode = "timeout" | "sequence_violation";

export interface InteractionBuildError {
  type: InteractionErrorCode;
  timeoutExpectedEventName?: string | null;
  sequenceViolationExpectedEventName?: string | null;
  sequenceViolationReceivedEventName?: string | null;
}

export interface PulseInteraction {
  id: string;
  name: string;
  props: Record<string, unknown>;
}

export type InteractionRunningStatus =
  | { kind: "no_ongoing"; old: InteractionRunningStatus | null }
  | {
      kind: "ongoing";
      index: number;
      interactionId: string;
      interactionConfig: InteractionConfig;
      interaction: PulseInteraction | null;
    };

export interface MatchResult {
  shouldTakeFirstEvent: boolean;
  shouldResetList: boolean;
  interactionStatus: InteractionRunningStatus;
}

export interface InteractionTrackerCallbacks {
  /** Emitted when an interaction span payload is ready (success or error). */
  onInteractionTerminal?: (interaction: PulseInteraction) => void;
  /** Latest status tuple (length 1 or 2) after each relevant event, for debugging/tests. */
  onStatusesEmitted?: (statuses: InteractionRunningStatus[]) => void;
}
