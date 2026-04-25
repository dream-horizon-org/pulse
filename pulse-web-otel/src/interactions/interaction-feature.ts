import type { Tracer } from "@opentelemetry/api";

import type { PulseWebConfig } from "../config";
import type { FeatureGate } from "../feature-gate";
import { PulseWebLogger } from "../pulse-web-logger";
import { InteractionCoordinator } from "./interaction-coordinator";
import {
  InteractionConfigFetcher,
  resolveInteractionConfigRequest,
} from "./config-fetcher";
import { InteractionSpanBuilder } from "./interaction-span-builder";

export class InteractionFeature {
  private readonly coordinator: InteractionCoordinator;
  private readonly fetcher: InteractionConfigFetcher;
  private initialized = false;

  constructor(
    endpointBaseUrl: string,
    config: Pick<PulseWebConfig, "apiKey">,
    private readonly gate: FeatureGate,
    private readonly interactionsEnabledByConfig: boolean,
    tracer: Tracer,
  ) {
    const spanBuilder = new InteractionSpanBuilder(tracer);
    this.coordinator = new InteractionCoordinator({
      onInteractionTerminal: (interaction) => {
        spanBuilder.emitInteraction(interaction);
      },
    });
    this.fetcher = new InteractionConfigFetcher(
      resolveInteractionConfigRequest(endpointBaseUrl, config),
    );
    this.fetcher.onChange((configs) => {
      this.coordinator.setConfigs(configs);
    });
  }

  async init(): Promise<void> {
    if (!this.isEnabled()) return;
    await this.fetcher.init();
    this.coordinator.setConfigs(this.fetcher.getConfigs());
    this.initialized = true;
  }

  trackEvent(
    name: string,
    attrs?: Record<string, unknown>,
    timestampMs: number = Date.now(),
  ): void {
    if (!this.isEnabled() || !this.initialized) return;
    this.coordinator.trackEvent(name, attrs, timestampMs);
  }

  shutdown(): void {
    this.initialized = false;
    this.fetcher.destroy();
    this.coordinator.shutdown();
  }

  private isEnabled(): boolean {
    if (!this.interactionsEnabledByConfig) return false;
    const enabledByGate = this.gate.isEnabled("interaction");
    if (!enabledByGate) {
      PulseWebLogger.debug(
        "[interactions] disabled by feature gate: interaction",
      );
    }
    return enabledByGate;
  }
}
import type { Tracer } from "@opentelemetry/api";

import type { PulseWebConfig } from "../config";
import type { FeatureGate } from "../feature-gate";
import { PulseWebLogger } from "../pulse-web-logger";
import { InteractionCoordinator } from "./interaction-coordinator";
import {
  InteractionConfigFetcher,
  resolveInteractionConfigRequest,
} from "./config-fetcher";
import { InteractionSpanBuilder } from "./interaction-span-builder";

export class InteractionFeature {
  private readonly coordinator: InteractionCoordinator;
  private readonly fetcher: InteractionConfigFetcher;
  private initialized = false;

  constructor(
    endpointBaseUrl: string,
    config: Pick<PulseWebConfig, "apiKey">,
    private readonly gate: FeatureGate,
    private readonly interactionsEnabledByConfig: boolean,
    tracer: Tracer,
  ) {
    const spanBuilder = new InteractionSpanBuilder(tracer);
    this.coordinator = new InteractionCoordinator({
      onInteractionTerminal: (interaction) => {
        spanBuilder.emitInteraction(interaction);
      },
    });
    this.fetcher = new InteractionConfigFetcher(
      resolveInteractionConfigRequest(endpointBaseUrl, config),
    );
    this.fetcher.onChange((configs) => {
      this.coordinator.setConfigs(configs);
    });
  }

  async init(): Promise<void> {
    if (!this.isEnabled()) return;
    await this.fetcher.init();
    this.coordinator.setConfigs(this.fetcher.getConfigs());
    this.initialized = true;
  }

  trackEvent(
    name: string,
    attrs?: Record<string, unknown>,
    timestampMs: number = Date.now(),
  ): void {
    if (!this.isEnabled() || !this.initialized) return;
    this.coordinator.trackEvent(name, attrs, timestampMs);
  }

  shutdown(): void {
    this.initialized = false;
    this.fetcher.destroy();
    this.coordinator.shutdown();
  }

  private isEnabled(): boolean {
    if (!this.interactionsEnabledByConfig) return false;
    const enabledByGate = this.gate.isEnabled("interaction");
    if (!enabledByGate) {
      PulseWebLogger.debug(
        "[interactions] disabled by feature gate: interaction",
      );
    }
    return enabledByGate;
  }
}
