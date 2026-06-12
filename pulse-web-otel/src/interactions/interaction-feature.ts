import type { Tracer } from "@opentelemetry/api";

import type { PulseWebConfig } from "../config";
import type { FeatureGate } from "../feature-gate";
import { PulseFeature } from "../remote-config";
import { PulseWebLogger } from "../pulse-web-logger";
import { InteractionCoordinator } from "./interaction-coordinator";
import {
  InteractionConfigFetcher,
  resolveInteractionConfigRequest,
  type InteractionConfigRequest,
} from "./config-fetcher";
import { InteractionSpanBuilder } from "./interaction-span-builder";
import type { PulseAttributes } from "../types/attributes";

const LIFECYCLE = "[interactions:feature]";

export class InteractionFeature {
  private readonly coordinator: InteractionCoordinator;
  private readonly fetcher: InteractionConfigFetcher;
  private readonly configRequest: InteractionConfigRequest;
  private initialized = false;

  constructor(
    endpointBaseUrl: string,
    config: Pick<PulseWebConfig, "apiKey">,
    private readonly gate: FeatureGate,
    private readonly interactionsEnabledByConfig: boolean,
    tracer: Tracer,
  ) {
    this.configRequest = resolveInteractionConfigRequest(
      endpointBaseUrl,
      config,
    );
    const spanBuilder = new InteractionSpanBuilder(tracer);
    this.coordinator = new InteractionCoordinator({
      onInteractionTerminal: (interaction) => {
        spanBuilder.emitInteraction(interaction);
      },
    });
    this.fetcher = new InteractionConfigFetcher(this.configRequest);
    this.fetcher.onChange((configs) => {
      PulseWebLogger.debug(
        `${LIFECYCLE} configs applied: count=${configs.length} (source=fetch or cache)`,
      );
      this.coordinator.setConfigs(configs);
    });
    PulseWebLogger.debug(
      `${LIFECYCLE} created: requestEnabled=${this.configRequest.enabled} url=${this.configRequest.url}`,
    );
  }

  async init(): Promise<void> {
    if (!this.interactionsEnabledByConfig) {
      PulseWebLogger.debug(
        `${LIFECYCLE} init skipped: instrumentations.interactions disabled`,
      );
      return;
    }
    if (!this.gate.isEnabled(PulseFeature.INTERACTION)) {
      PulseWebLogger.debug(
        `${LIFECYCLE} init skipped: feature gate ${PulseFeature.INTERACTION} off`,
      );
      return;
    }
    PulseWebLogger.debug(
      `${LIFECYCLE} init: fetching interaction configs (url=${this.configRequest.url})`,
    );
    await this.fetcher.init();
    const configs = this.fetcher.getConfigs();
    this.coordinator.setConfigs(configs);
    this.initialized = true;
    PulseWebLogger.debug(
      `${LIFECYCLE} init complete: flows=${configs.length} initialized=true`,
    );
  }

  trackEvent(
    name: string,
    attrs?: PulseAttributes,
    timestampMs: number = Date.now(),
  ): void {
    if (!this.interactionsEnabledByConfig) return;
    if (!this.gate.isEnabled(PulseFeature.INTERACTION)) return;
    if (!this.initialized) {
      PulseWebLogger.verbose(
        `${LIFECYCLE} trackEvent dropped: not initialized (name=${name})`,
      );
      return;
    }
    PulseWebLogger.verbose(
      `${LIFECYCLE} trackEvent: name=${name} timeMs=${timestampMs} attrKeys=${
        attrs != null ? Object.keys(attrs).join(",") : ""
      }`,
    );
    this.coordinator.trackEvent(name, attrs, timestampMs);
  }

  getRunningInteractions(): Array<{ id: string; name: string }> {
    if (!this.interactionsEnabledByConfig) return [];
    if (!this.gate.isEnabled(PulseFeature.INTERACTION)) return [];
    if (!this.initialized) return [];
    return this.coordinator.getRunningInteractions();
  }

  addMarkerToAll(
    name: string,
    attrs?: PulseAttributes,
    timestampMs: number = Date.now(),
  ): void {
    if (!this.interactionsEnabledByConfig) return;
    if (!this.gate.isEnabled(PulseFeature.INTERACTION)) return;
    if (!this.initialized) {
      PulseWebLogger.verbose(
        `${LIFECYCLE} addMarkerToAll dropped: not initialized (name=${name})`,
      );
      return;
    }
    PulseWebLogger.verbose(
      `${LIFECYCLE} addMarkerToAll: name=${name} timeMs=${timestampMs}`,
    );
    this.coordinator.addMarkerToAll(name, attrs, timestampMs);
  }

  shutdown(): void {
    PulseWebLogger.debug(
      `${LIFECYCLE} shutdown: clearing fetcher and trackers`,
    );
    this.initialized = false;
    this.fetcher.destroy();
    this.coordinator.shutdown();
  }
}
