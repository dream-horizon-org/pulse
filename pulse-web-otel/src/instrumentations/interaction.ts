import type { PulseInstrumentation, SdkContext } from "../instrumentation-registry";
import { InteractionFeature } from "../interactions/interaction-feature";

export class InteractionInstrumentation implements PulseInstrumentation {
  readonly name = "interactions";
  private feature?: InteractionFeature;

  install(sdk: SdkContext): void {
    this.feature = new InteractionFeature(
      sdk.endpointBaseUrl,
      sdk.config,
      sdk.gate,
      sdk.config.instrumentations?.interactions?.enabled ?? true,
      sdk.tracer,
    );
    void this.feature.init();
  }

  trackEvent(
    name: string,
    attrs?: Record<string, unknown>,
    timestampMs?: number,
  ): void {
    this.feature?.trackEvent(name, attrs, timestampMs);
  }

  uninstall(): void {
    this.feature?.shutdown();
    this.feature = undefined;
  }
}
