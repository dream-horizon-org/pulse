export type FilterOptions =
  | "APP_VERSION"
  | "PLATFORM"
  | "OS_VERSION"
  | "NETWORK_PROVIDER"
  | "STATE";

export const interactionDetailsfilterConstants = {
  APP_VERSION: "App Version",
  PLATFORM: "Platform",
  OS_VERSION: "OS Version",
  NETWORK_PROVIDER: "Network Provider",
  STATE: "State",
};

export const interactionDetailsfilterOptions = [
  {
    label: "App Version",
    value: "APP_VERSION",
  },
  {
    label: "Platform",
    value: "PLATFORM",
  },
  {
    label: "OS Version",
    value: "OS_VERSION",
  },
  {
    label: "Network Provider",
    value: "NETWORK_PROVIDER",
  },
  {
    label: "State",
    value: "STATE",
  },
];

export const interactionDetailsfilterDefaultValues = [
  "APP_VERSION",
  "PLATFORM",
  "OS_VERSION",
];
