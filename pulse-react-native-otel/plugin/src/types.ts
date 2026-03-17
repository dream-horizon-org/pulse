/**
 * OpenTelemetry attribute value types.
 * Based on OpenTelemetry JavaScript SDK attribute types.
 * @see https://github.com/open-telemetry/opentelemetry-js/blob/main/api/src/common/Attributes.ts
 */
export type PulseAttributeValue =
  | string
  | number
  | boolean
  | string[]
  | number[]
  | boolean[];

export type PulseAttributes = Record<
  string,
  PulseAttributeValue | undefined | null
>;

interface IInteractionConfig {
  enabled: boolean;
  url?: string;
}

export interface PulsePluginProps {
  endpointBaseUrl: string;
  apiKey: string;
  /**
   * Initial data collection consent state.
   * Defaults to PENDING (buffers all telemetry until setDataCollectionState is called).
   * Use ALLOWED to skip buffering and export immediately.
   */
  dataCollectionState?: 'PENDING' | 'ALLOWED' | 'DENIED';
  endpointHeaders?: Record<string, string>;
  /**
   * Optional custom URL for fetching SDK configuration.
   * If not provided, defaults to: {endpointBaseUrl with port 8080}/v1/configs/active/
   */
  configEndpointUrl?: string;
  globalAttributes?: PulseAttributes;
  instrumentation?: {
    interaction?: IInteractionConfig;
    activity?: boolean;
    network?: boolean;
    anr?: boolean;
    crash?: boolean;
    slowRendering?: boolean;
    fragment?: boolean;
  };
}
