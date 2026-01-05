interface IInteractionConfig {
  url: string;
  enabled: boolean;
}

export interface PulsePluginProps {
  endpointBaseUrl: string;
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
