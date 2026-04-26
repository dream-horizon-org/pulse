/**
 * Fan-out to per-config InteractionTracker instances.
 * Config refresh replaces all trackers and clears timers (Android InteractionManager init pattern).
 */
import { PulseWebLogger } from "../pulse-web-logger";
import type { InteractionConfig } from "./interaction-models";
import type { InteractionTrackerCallbacks } from "../types/interactions/interaction-runtime";
import { toInteractionLocalEvent } from "../utils/interactions/local-event-converter";
import { InteractionTracker } from "./interaction-tracker";

const LIFECYCLE = "[interactions:coordinator]";

export class InteractionCoordinator {
  private trackers: InteractionTracker[] = [];

  constructor(
    private readonly sharedCallbacks: InteractionTrackerCallbacks = {},
  ) {}

  /**
   * Replaces all trackers. Destroys previous timers and buffers (mid-flight refresh).
   */
  setConfigs(configs: readonly InteractionConfig[]): void {
    PulseWebLogger.debug(
      `${LIFECYCLE} setConfigs: replacing trackers, flowCount=${configs.length}`,
    );
    this.destroyTrackers();
    this.trackers = configs.map(
      (cfg) => new InteractionTracker(cfg, this.sharedCallbacks),
    );
  }

  getTrackers(): readonly InteractionTracker[] {
    return this.trackers;
  }

  /**
   * Synchronous fan-out to every tracker (Android InteractionManager eventQueue fan-out).
   */
  trackEvent(
    name: string,
    attrs?: Record<string, unknown>,
    timeMs: number = Date.now(),
  ): void {
    const ev = toInteractionLocalEvent(name, attrs, timeMs);
    for (const tracker of this.trackers) {
      tracker.checkAndAdd(ev);
    }
  }

  shutdown(): void {
    PulseWebLogger.debug(
      `${LIFECYCLE} shutdown: destroying ${this.trackers.length} tracker(s)`,
    );
    this.destroyTrackers();
  }

  private destroyTrackers(): void {
    for (const t of this.trackers) {
      t.destroy();
    }
    this.trackers = [];
  }
}
